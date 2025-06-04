package com.example.common.service;

import com.example.common.util.ClusterConstants;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Service providing cluster operations and utilities.
 * Used by all applications for cluster state management and monitoring.
 */
@Service
public class ClusterService {

    private static final Logger logger = LoggerFactory.getLogger(ClusterService.class);

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Value("${cluster.node.id:unknown}")
    private String nodeId;

    @Value("${cluster.node.type:unknown}")
    private String nodeType;

    /**
     * Update node status in the cluster
     */
    public void updateNodeStatus(String status, Map<String, Object> additionalInfo) {
        try {
            IMap<String, Object> nodeStatusMap = hazelcastInstance.getMap(ClusterConstants.NODE_STATUS_MAP);
            
            Map<String, Object> nodeStatus = Map.of(
                "nodeId", nodeId,
                "nodeType", nodeType,
                "status", status,
                "lastUpdate", LocalDateTime.now().toString(),
                "clusterSize", getClusterSize(),
                "additionalInfo", additionalInfo != null ? additionalInfo : Map.of()
            );
            
            nodeStatusMap.put(nodeId, nodeStatus);
            logger.debug("Updated node status for {}: {}", nodeId, status);
            
        } catch (Exception e) {
            logger.error("Failed to update node status for {}: {}", nodeId, e.getMessage());
        }
    }

    /**
     * Check if current node is the cluster leader (enhanced for multiple coordinators)
     */
    public boolean isLeader() {
        if (!ClusterConstants.NODE_TYPE_COORDINATOR.equals(nodeType)) {
            return false; // Only coordinators can be leaders
        }
        
        try {
            // For multiple coordinators, use a simple leader election:
            // The coordinator with the lowest node ID becomes the leader
            IMap<String, Object> nodeStatusMap = hazelcastInstance.getMap(ClusterConstants.NODE_STATUS_MAP);
            
            // Find all active coordinator nodes
            String earliestCoordinator = nodeStatusMap.entrySet().stream()
                .filter(entry -> {
                    Object nodeStatus = entry.getValue();
                    if (nodeStatus instanceof Map) {
                        Map<String, Object> status = (Map<String, Object>) nodeStatus;
                        return ClusterConstants.NODE_TYPE_COORDINATOR.equals(status.get("nodeType"));
                    }
                    return false;
                })
                .map(Map.Entry::getKey)
                .sorted() // Lexicographic sort - coordinator-1 comes before coordinator-2
                .findFirst()
                .orElse(nodeId);
                
            boolean isLeader = nodeId.equals(earliestCoordinator);
            logger.trace("Leadership check: {} is leader: {} (earliest coordinator: {})", 
                        nodeId, isLeader, earliestCoordinator);
            return isLeader;
            
        } catch (Exception e) {
            logger.warn("Failed to determine leadership, defaulting to false: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get the current cluster size
     */
    public int getClusterSize() {
        try {
            return hazelcastInstance.getCluster().getMembers().size();
        } catch (Exception e) {
            logger.warn("Failed to get cluster size: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Get all cluster members
     */
    public Set<String> getClusterMembers() {
        try {
            return hazelcastInstance.getCluster().getMembers()
                    .stream()
                    .map(member -> member.getAddress().toString())
                    .collect(java.util.stream.Collectors.toSet());
        } catch (Exception e) {
            logger.warn("Failed to get cluster members: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * Store a metric in the cluster metrics map
     */
    public void storeMetric(String metricName, Object value) {
        try {
            IMap<String, Object> metricsMap = hazelcastInstance.getMap(ClusterConstants.CLUSTER_METRICS_MAP);
            String key = nodeId + ":" + metricName;
            
            Map<String, Object> metric = Map.of(
                "nodeId", nodeId,
                "metricName", metricName,
                "value", value,
                "timestamp", LocalDateTime.now().toString()
            );
            
            metricsMap.put(key, metric);
            logger.trace("Stored metric {}: {}", key, value);
            
        } catch (Exception e) {
            logger.error("Failed to store metric {}={}: {}", metricName, value, e.getMessage());
        }
    }

    /**
     * Get cluster-wide status information
     */
    public Map<String, Object> getClusterStatus() {
        try {
            IMap<String, Object> nodeStatusMap = hazelcastInstance.getMap(ClusterConstants.NODE_STATUS_MAP);
            
            return Map.of(
                "currentNode", nodeId,
                "nodeType", nodeType,
                "isLeader", isLeader(),
                "clusterSize", getClusterSize(),
                "clusterMembers", getClusterMembers(),
                "allNodeStatuses", nodeStatusMap.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue
                        ))
            );
            
        } catch (Exception e) {
            logger.error("Failed to get cluster status: {}", e.getMessage());
            return Map.of(
                "currentNode", nodeId,
                "nodeType", nodeType,
                "error", e.getMessage()
            );
        }
    }

    /**
     * Publish a message to a cluster topic
     */
    public void publishToTopic(String topicName, Object message) {
        try {
            hazelcastInstance.getTopic(topicName).publish(message);
            logger.debug("Published message to topic {}: {}", topicName, message);
        } catch (Exception e) {
            logger.error("Failed to publish to topic {}: {}", topicName, e.getMessage());
        }
    }

    /**
     * Initialize node status on startup
     */
    public void initializeNodeStatus() {
        updateNodeStatus("STARTING", Map.of("startTime", LocalDateTime.now().toString()));
        logger.info("Initialized cluster node: {} (type: {}, port: {})", 
                   nodeId, nodeType, hazelcastInstance.getConfig().getNetworkConfig().getPort());
    }

    /**
     * Mark node as ready
     */
    public void markNodeReady() {
        updateNodeStatus("READY", Map.of(
            "readyTime", LocalDateTime.now().toString(),
            "isLeader", isLeader(),
            "clusterSize", getClusterSize()
        ));
        
        logger.info("Node {} is ready. Leader: {}, Cluster size: {}", 
                   nodeId, isLeader(), getClusterSize());
    }

    // Getters
    public String getNodeId() {
        return nodeId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }
} 