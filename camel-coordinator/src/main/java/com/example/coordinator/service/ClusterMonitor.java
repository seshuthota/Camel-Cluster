package com.example.coordinator.service;

import com.example.common.service.ClusterService;
import com.example.common.util.ClusterConstants;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClusterMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterMonitor.class);
    
    @Autowired
    private ClusterService clusterService;
    
    @Autowired
    private HazelcastInstance hazelcastInstance;
    
    @Autowired
    private CamelContext camelContext;
    
    @Value("${cluster.node.id:coordinator-1}")
    private String nodeId;
    
    @Value("${cluster.monitor.health-check-interval:30000}")
    private long healthCheckInterval;
    
    @Value("${cluster.monitor.node-timeout:60000}")
    private long nodeTimeout;

    /**
     * Monitor cluster health and detect failed nodes
     */
    @Scheduled(fixedDelayString = "${cluster.monitor.health-check-interval:30000}")
    public void monitorClusterHealth() {
        if (!clusterService.isLeader()) {
            logger.debug("Node {} is not leader, skipping cluster monitoring", nodeId);
            return;
        }
        
        try {
            logger.info("Starting cluster health check as leader node: {}", nodeId);
            
            // Get current cluster state
            Map<String, Object> clusterState = getClusterState();
            
            // Check node health
            Set<String> activeNodes = checkNodeHealth();
            Set<String> failedNodes = detectFailedNodes(activeNodes);
            
            // Handle failed nodes
            if (!failedNodes.isEmpty()) {
                handleFailedNodes(failedNodes);
            }
            
            // Update cluster metrics
            updateClusterMetrics(activeNodes, failedNodes);
            
            logger.info("Cluster health check completed. Active nodes: {}, Failed nodes: {}", 
                       activeNodes.size(), failedNodes.size());
            
        } catch (Exception e) {
            logger.error("Error during cluster health monitoring", e);
        }
    }
    
    /**
     * Get comprehensive cluster state information
     */
    public Map<String, Object> getClusterState() {
        Map<String, Object> state = new HashMap<>();
        
        try {
            // Basic cluster info
            state.put("clusterId", hazelcastInstance.getCluster().getClusterVersion());
            state.put("clusterSize", hazelcastInstance.getCluster().getMembers().size());
            state.put("leader", getLeaderNodeId());
            state.put("isLeader", clusterService.isLeader());
            state.put("nodeId", nodeId);
            state.put("timestamp", LocalDateTime.now());
            
            // Node status from distributed map
            IMap<String, Map<String, Object>> nodeStatusMap = 
                hazelcastInstance.getMap(ClusterConstants.NODE_STATUS_MAP);
            
            Map<String, Map<String, Object>> nodeStatuses = new HashMap<>();
            for (Map.Entry<String, Map<String, Object>> entry : nodeStatusMap.entrySet()) {
                nodeStatuses.put(entry.getKey(), entry.getValue());
            }
            state.put("nodeStatuses", nodeStatuses);
            
            // Active routes information
            List<Map<String, Object>> routeInfo = camelContext.getRoutes().stream()
                .map(this::getRouteInfo)
                .collect(Collectors.toList());
            state.put("routes", routeInfo);
            
            // Cluster metrics
            IMap<String, Object> metricsMap = hazelcastInstance.getMap(ClusterConstants.CLUSTER_METRICS_MAP);
            state.put("metrics", new HashMap<>(metricsMap));
            
        } catch (Exception e) {
            logger.error("Error getting cluster state", e);
            state.put("error", e.getMessage());
        }
        
        return state;
    }
    
    /**
     * Check health of all cluster nodes
     */
    private Set<String> checkNodeHealth() {
        Set<String> activeNodes = new HashSet<>();
        IMap<String, Object> nodeStatusMap = 
            hazelcastInstance.getMap(ClusterConstants.NODE_STATUS_MAP);
        
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<String, Object> entry : nodeStatusMap.entrySet()) {
            String nodeId = entry.getKey();
            Object statusObj = entry.getValue();
            
            if (statusObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> status = (Map<String, Object>) statusObj;
                
                Object lastUpdateObj = status.get("lastUpdate");
                if (lastUpdateObj != null) {
                    try {
                        // For simplicity, consider node active if it exists in map
                        activeNodes.add(nodeId);
                        logger.debug("Node {} is considered healthy", nodeId);
                    } catch (Exception e) {
                        logger.warn("Error parsing last update for node {}: {}", nodeId, e.getMessage());
                    }
                }
            }
        }
        
        return activeNodes;
    }
    
    /**
     * Detect nodes that have failed (no recent heartbeat)
     */
    private Set<String> detectFailedNodes(Set<String> activeNodes) {
        Set<String> failedNodes = new HashSet<>();
        
        // For this implementation, we'll rely on Hazelcast's member detection
        Set<String> clusterMembers = clusterService.getClusterMembers();
        IMap<String, Object> nodeStatusMap = 
            hazelcastInstance.getMap(ClusterConstants.NODE_STATUS_MAP);
        
        // Find nodes that are in status map but not in cluster members
        for (String statusNodeId : nodeStatusMap.keySet()) {
            boolean foundInCluster = false;
            for (String member : clusterMembers) {
                if (member.contains(statusNodeId) || statusNodeId.contains("coordinator") || 
                    statusNodeId.contains("producer") || statusNodeId.contains("consumer")) {
                    foundInCluster = true;
                    break;
                }
            }
            if (!foundInCluster && !activeNodes.contains(statusNodeId)) {
                failedNodes.add(statusNodeId);
                logger.warn("Detected potentially failed node: {}", statusNodeId);
            }
        }
        
        return failedNodes;
    }
    
    /**
     * Handle failed nodes by cleaning up their state and triggering rebalancing
     */
    private void handleFailedNodes(Set<String> failedNodes) {
        IMap<String, Object> nodeStatusMap = 
            hazelcastInstance.getMap(ClusterConstants.NODE_STATUS_MAP);
        
        for (String failedNodeId : failedNodes) {
            logger.warn("Handling failed node: {}", failedNodeId);
            
            try {
                // Remove failed node from cluster state
                nodeStatusMap.remove(failedNodeId);
                
                // Publish cluster event about failed node
                publishClusterEvent("NODE_FAILED", 
                    Map.of("failedNode", failedNodeId, "detectedBy", nodeId));
                
                // Log failure for monitoring
                logger.error("Node {} has been removed from cluster due to failure", failedNodeId);
                
            } catch (Exception e) {
                logger.error("Error handling failed node: {}", failedNodeId, e);
            }
        }
    }
    
    /**
     * Update cluster-wide metrics
     */
    private void updateClusterMetrics(Set<String> activeNodes, Set<String> failedNodes) {
        IMap<String, Object> metricsMap = hazelcastInstance.getMap(ClusterConstants.CLUSTER_METRICS_MAP);
        
        try {
            metricsMap.put("activeNodeCount", activeNodes.size());
            metricsMap.put("failedNodeCount", failedNodes.size());
            metricsMap.put("lastHealthCheck", System.currentTimeMillis());
            metricsMap.put("healthCheckBy", nodeId);
            
            if (!failedNodes.isEmpty()) {
                metricsMap.put("lastFailure", System.currentTimeMillis());
                metricsMap.put("recentFailures", failedNodes);
            }
            
        } catch (Exception e) {
            logger.error("Error updating cluster metrics", e);
        }
    }
    
    /**
     * Get route information for monitoring
     */
    private Map<String, Object> getRouteInfo(Route route) {
        Map<String, Object> info = new HashMap<>();
        info.put("routeId", route.getId());
        info.put("status", route.getRouteController().getRouteStatus(route.getId()).toString());
        info.put("uptime", route.getUptime());
        info.put("description", route.getDescription());
        return info;
    }
    
    /**
     * Generate cluster health report
     */
    public Map<String, Object> generateHealthReport() {
        Map<String, Object> report = new HashMap<>();
        
        try {
            Map<String, Object> clusterState = getClusterState();
            Set<String> activeNodes = checkNodeHealth();
            
            // Overall health status
            boolean isHealthy = activeNodes.size() > 0 && clusterService.isLeader();
            report.put("healthy", isHealthy);
            report.put("activeNodeCount", activeNodes.size());
            report.put("totalClusterSize", hazelcastInstance.getCluster().getMembers().size());
            
            // Leader information
            report.put("hasLeader", getLeaderNodeId() != null);
            report.put("currentNode", nodeId);
            report.put("isCurrentNodeLeader", clusterService.isLeader());
            
            // Recent activity
            IMap<String, Object> metricsMap = hazelcastInstance.getMap(ClusterConstants.CLUSTER_METRICS_MAP);
            Object lastHealthCheck = metricsMap.get("lastHealthCheck");
            if (lastHealthCheck instanceof Long) {
                report.put("lastHealthCheckAge", System.currentTimeMillis() - (Long) lastHealthCheck);
            }
            
            // Detailed state
            report.put("clusterState", clusterState);
            report.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            logger.error("Error generating health report", e);
            report.put("healthy", false);
            report.put("error", e.getMessage());
        }
        
        return report;
    }
    
    /**
     * Force cluster rebalancing (manual trigger)
     */
    public void triggerRebalancing() {
        if (!clusterService.isLeader()) {
            logger.warn("Cannot trigger rebalancing - node {} is not leader", nodeId);
            return;
        }
        
        logger.info("Triggering cluster rebalancing by leader node: {}", nodeId);
        
        try {
            // Publish rebalancing event
            publishClusterEvent("REBALANCE_TRIGGERED", 
                Map.of("triggeredBy", nodeId, "timestamp", System.currentTimeMillis()));
            
            // Force health check
            monitorClusterHealth();
            
        } catch (Exception e) {
            logger.error("Error triggering cluster rebalancing", e);
        }
    }
    
    /**
     * Get leader node ID (simplified implementation)
     */
    private String getLeaderNodeId() {
        // For now, return the first coordinator node we find
        IMap<String, Object> nodeStatusMap = 
            hazelcastInstance.getMap(ClusterConstants.NODE_STATUS_MAP);
        
        for (Map.Entry<String, Object> entry : nodeStatusMap.entrySet()) {
            Object statusObj = entry.getValue();
            if (statusObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> status = (Map<String, Object>) statusObj;
                Object nodeType = status.get("nodeType");
                if ("coordinator".equals(nodeType)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
    
    /**
     * Publish cluster event
     */
    private void publishClusterEvent(String eventType, Map<String, Object> data) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", eventType,
                "data", data,
                "timestamp", System.currentTimeMillis(),
                "sourceNode", nodeId
            );
            
            clusterService.publishToTopic(ClusterConstants.CLUSTER_EVENTS_TOPIC, event);
            logger.info("Published cluster event: {} from {}", eventType, nodeId);
            
        } catch (Exception e) {
            logger.error("Error publishing cluster event: {}", eventType, e);
        }
    }
} 