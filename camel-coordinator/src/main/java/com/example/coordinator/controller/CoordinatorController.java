package com.example.coordinator.controller;

import com.example.coordinator.service.ClusterMonitor;
import com.example.coordinator.service.DatabaseReporter;
import com.example.common.service.ClusterService;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/coordinator")
@CrossOrigin(origins = "*")
public class CoordinatorController {
    
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorController.class);
    
    @Autowired
    private ClusterService clusterService;
    
    @Autowired
    private ClusterMonitor clusterMonitor;
    
    @Autowired
    private DatabaseReporter databaseReporter;
    
    @Autowired
    private CamelContext camelContext;
    
    @Autowired
    private ProducerTemplate producerTemplate;
    
    @Value("${cluster.node.id:coordinator-1}")
    private String nodeId;
    
    @Value("${spring.application.name:camel-coordinator}")
    private String applicationName;
    
    private final LocalDateTime startTime = LocalDateTime.now();
    
    /**
     * Get coordinator application status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            status.put("application", applicationName);
            status.put("nodeId", nodeId);
            status.put("nodeType", "coordinator");
            status.put("status", "RUNNING");
            status.put("startTime", startTime);
            status.put("uptime", Duration.between(startTime, LocalDateTime.now()).toString());
            status.put("timestamp", LocalDateTime.now());
            
            // Camel context status
            status.put("camelContextStatus", camelContext.getStatus().name());
            status.put("activeRoutes", camelContext.getRoutes().size());
            
            // Cluster role
            status.put("isLeader", clusterService.isLeader());
            status.put("leaderNodeId", getLeaderNodeId());
            
            // JVM info
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> jvm = new HashMap<>();
            jvm.put("totalMemory", runtime.totalMemory());
            jvm.put("freeMemory", runtime.freeMemory());
            jvm.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
            jvm.put("maxMemory", runtime.maxMemory());
            jvm.put("processors", runtime.availableProcessors());
            status.put("jvm", jvm);
            
            logger.debug("Coordinator status requested: {}", status);
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Error getting coordinator status", e);
            status.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(status);
        }
    }
    
    /**
     * Get comprehensive cluster information
     */
    @GetMapping("/cluster")
    public ResponseEntity<Map<String, Object>> getClusterInfo() {
        try {
            Map<String, Object> clusterInfo = clusterMonitor.getClusterState();
            logger.debug("Cluster info requested from coordinator");
            return ResponseEntity.ok(clusterInfo);
        } catch (Exception e) {
            logger.error("Error getting cluster information", e);
            Map<String, Object> error = Map.of("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Get cluster health report
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealthReport() {
        try {
            Map<String, Object> health = clusterMonitor.generateHealthReport();
            logger.debug("Health report requested from coordinator");
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            logger.error("Error generating health report", e);
            Map<String, Object> error = Map.of("healthy", false, "error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Get coordinator route information
     */
    @GetMapping("/routes")
    public ResponseEntity<List<Map<String, Object>>> getRoutes() {
        try {
            List<Map<String, Object>> routes = camelContext.getRoutes().stream()
                .map(this::getRouteInfo)
                .collect(Collectors.toList());
            
            logger.debug("Routes info requested from coordinator: {} routes", routes.size());
            return ResponseEntity.ok(routes);
        } catch (Exception e) {
            logger.error("Error getting routes information", e);
            return ResponseEntity.internalServerError().body(Arrays.asList(
                Map.of("error", e.getMessage())
            ));
        }
    }
    
    /**
     * Get latest cluster reports
     */
    @GetMapping("/reports")
    public ResponseEntity<List<Map<String, Object>>> getLatestReports(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<Map<String, Object>> reports = databaseReporter.getLatestReports(limit);
            logger.debug("Latest reports requested: {} reports", reports.size());
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            logger.error("Error getting latest reports", e);
            return ResponseEntity.internalServerError().body(Arrays.asList(
                Map.of("error", e.getMessage())
            ));
        }
    }
    
    /**
     * Get latest analytics
     */
    @GetMapping("/analytics")
    public ResponseEntity<List<Map<String, Object>>> getLatestAnalytics(
            @RequestParam(defaultValue = "5") int limit) {
        try {
            List<Map<String, Object>> analytics = databaseReporter.getLatestAnalytics(limit);
            logger.debug("Latest analytics requested: {} items", analytics.size());
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            logger.error("Error getting latest analytics", e);
            return ResponseEntity.internalServerError().body(Arrays.asList(
                Map.of("error", e.getMessage())
            ));
        }
    }
    
    /**
     * Manual cluster health check trigger
     */
    @PostMapping("/health/check")
    public ResponseEntity<Map<String, Object>> triggerHealthCheck() {
        try {
            if (!clusterService.isLeader()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Only leader can trigger health checks", "nodeId", nodeId)
                );
            }
            
            String result = producerTemplate.requestBody("direct:manual-health-check", "", String.class);
            
            Map<String, Object> response = new HashMap<>();
            response.put("result", result);
            response.put("triggeredBy", nodeId);
            response.put("timestamp", LocalDateTime.now());
            
            logger.info("Manual health check triggered by coordinator: {}", nodeId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error triggering manual health check", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", e.getMessage())
            );
        }
    }
    
    /**
     * Manual report generation trigger
     */
    @PostMapping("/reports/generate")
    public ResponseEntity<Map<String, Object>> triggerReportGeneration() {
        try {
            if (!clusterService.isLeader()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Only leader can generate reports", "nodeId", nodeId)
                );
            }
            
            String result = producerTemplate.requestBody("direct:manual-report", "", String.class);
            
            Map<String, Object> response = new HashMap<>();
            response.put("result", result);
            response.put("triggeredBy", nodeId);
            response.put("timestamp", LocalDateTime.now());
            
            logger.info("Manual report generation triggered by coordinator: {}", nodeId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error triggering manual report generation", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", e.getMessage())
            );
        }
    }
    
    /**
     * Generate on-demand report with custom date range
     */
    @PostMapping("/reports/custom")
    public ResponseEntity<Map<String, Object>> generateCustomReport(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "CUSTOM") String period) {
        try {
            if (!clusterService.isLeader()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Only leader can generate custom reports", "nodeId", nodeId)
                );
            }
            
            Map<String, Object> report = databaseReporter.generateOnDemandReport(period, startDate, endDate);
            
            logger.info("Custom report generated by coordinator: {} - {} to {}", nodeId, startDate, endDate);
            return ResponseEntity.ok(report);
            
        } catch (Exception e) {
            logger.error("Error generating custom report", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", e.getMessage())
            );
        }
    }
    
    /**
     * Manual cluster rebalancing trigger
     */
    @PostMapping("/cluster/rebalance")
    public ResponseEntity<Map<String, Object>> triggerRebalancing() {
        try {
            if (!clusterService.isLeader()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Only leader can trigger rebalancing", "nodeId", nodeId)
                );
            }
            
            String result = producerTemplate.requestBody("direct:manual-rebalance", "", String.class);
            
            Map<String, Object> response = new HashMap<>();
            response.put("result", result);
            response.put("triggeredBy", nodeId);
            response.put("timestamp", LocalDateTime.now());
            
            logger.info("Manual cluster rebalancing triggered by coordinator: {}", nodeId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error triggering manual rebalancing", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", e.getMessage())
            );
        }
    }
    
    /**
     * Emergency cluster reset (use with caution)
     */
    @PostMapping("/cluster/reset")
    public ResponseEntity<Map<String, Object>> emergencyReset(
            @RequestParam(required = true) String confirm) {
        try {
            if (!"CONFIRM_RESET".equals(confirm)) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Emergency reset requires confirmation parameter: CONFIRM_RESET")
                );
            }
            
            if (!clusterService.isLeader()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Only leader can perform emergency reset", "nodeId", nodeId)
                );
            }
            
            String result = producerTemplate.requestBody("direct:emergency-reset", "", String.class);
            
            Map<String, Object> response = new HashMap<>();
            response.put("result", result);
            response.put("triggeredBy", nodeId);
            response.put("timestamp", LocalDateTime.now());
            response.put("warning", "Emergency reset performed - cluster state has been reset");
            
            logger.warn("EMERGENCY RESET triggered by coordinator: {}", nodeId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error performing emergency reset", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", e.getMessage())
            );
        }
    }
    
    /**
     * Get coordinator metrics and statistics
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();
            
            // Basic metrics
            metrics.put("nodeId", nodeId);
            metrics.put("nodeType", "coordinator");
            metrics.put("uptime", Duration.between(startTime, LocalDateTime.now()).toString());
            metrics.put("isLeader", clusterService.isLeader());
            
            // Route metrics
            List<Route> routes = camelContext.getRoutes();
            metrics.put("totalRoutes", routes.size());
            
            Map<String, Object> routeMetrics = new HashMap<>();
            for (Route route : routes) {
                Map<String, Object> routeInfo = new HashMap<>();
                routeInfo.put("status", route.getRouteController().getRouteStatus(route.getId()).toString());
                routeInfo.put("uptime", route.getUptime());
                routeMetrics.put(route.getId(), routeInfo);
            }
            metrics.put("routes", routeMetrics);
            
            // Cluster status from cluster service
            Map<String, Object> clusterStatus = clusterService.getClusterStatus();
            metrics.put("cluster", clusterStatus);
            
            metrics.put("timestamp", LocalDateTime.now());
            
            logger.debug("Coordinator metrics requested");
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            logger.error("Error getting coordinator metrics", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", e.getMessage())
            );
        }
    }
    
    /**
     * Test cluster connectivity
     */
    @GetMapping("/cluster/test")
    public ResponseEntity<Map<String, Object>> testClusterConnectivity() {
        try {
            String result = producerTemplate.requestBody("direct:cluster-test", "", String.class);
            
            Map<String, Object> response = new HashMap<>();
            response.put("result", result);
            response.put("nodeId", nodeId);
            response.put("timestamp", LocalDateTime.now());
            
            logger.debug("Cluster connectivity test performed");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error testing cluster connectivity", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", e.getMessage(), "result", "FAILED")
            );
        }
    }
    
    /**
     * Get detailed route information
     */
    private Map<String, Object> getRouteInfo(Route route) {
        Map<String, Object> info = new HashMap<>();
        
        info.put("routeId", route.getId());
        info.put("status", route.getRouteController().getRouteStatus(route.getId()).toString());
        info.put("uptime", route.getUptime());
        info.put("description", route.getDescription());
        
        // Additional route details
        if (route.getEndpoint() != null) {
            info.put("endpoint", route.getEndpoint().getEndpointUri());
        }
        
        // Add basic route statistics note
        info.put("statisticsNote", "Route is active and monitored");
        
        return info;
    }
    
    /**
     * Get leader node ID (helper method)
     */
    private String getLeaderNodeId() {
        // For simplicity, return coordinator node if it's leader
        return clusterService.isLeader() ? nodeId : "unknown";
    }
} 