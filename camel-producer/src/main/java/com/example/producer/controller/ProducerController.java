package com.example.producer.controller;

import com.example.common.service.ClusterService;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/producer")
public class ProducerController {
    
    @Autowired
    private CamelContext camelContext;
    
    @Autowired
    private ProducerTemplate producerTemplate;
    
    @Autowired
    private ClusterService clusterService;
    
    @Value("${cluster.node.id:producer-1}")
    private String nodeId;
    
    private long orderCount = 0;
    private long fileCount = 0;
    private LocalDateTime startTime = LocalDateTime.now();
    
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("nodeId", nodeId);
        status.put("application", "Camel Producer");
        status.put("status", "RUNNING");
        status.put("startTime", startTime.toString());
        status.put("uptime", java.time.Duration.between(startTime, LocalDateTime.now()).toString());
        status.put("camelContext", camelContext.getName());
        status.put("camelStatus", camelContext.getStatus().toString());
        status.put("activeRoutes", camelContext.getRoutes().size());
        
        return status;
    }
    
    @GetMapping("/cluster")
    public Map<String, Object> getClusterInfo() {
        Map<String, Object> clusterInfo = new HashMap<>();
        clusterInfo.put("nodeId", nodeId);
        clusterInfo.put("clusterName", "camel-cluster");
        clusterInfo.put("isClusterMember", clusterService.getClusterSize() > 0);
        clusterInfo.put("clusterSize", clusterService.getClusterSize());
        clusterInfo.put("clusterMembers", clusterService.getClusterMembers());
        clusterInfo.put("isLeader", clusterService.isLeader());
        
        return clusterInfo;
    }
    
    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("nodeId", nodeId);
        metrics.put("ordersGenerated", orderCount);
        metrics.put("filesGenerated", fileCount);
        metrics.put("startTime", startTime.toString());
        
        // Get route statistics
        Map<String, Object> routeStats = new HashMap<>();
        camelContext.getRoutes().forEach(route -> {
            String routeId = route.getId();
            routeStats.put(routeId + "_status", "ACTIVE");
        });
        metrics.put("routeStatistics", routeStats);
        
        return metrics;
    }
    
    @PostMapping("/generate")
    public Map<String, Object> generateOrder() {
        try {
            producerTemplate.sendBody("direct:generate-order", null);
            orderCount++;
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Order generated successfully");
            response.put("nodeId", nodeId);
            response.put("timestamp", LocalDateTime.now().toString());
            response.put("totalOrders", orderCount);
            
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to generate order: " + e.getMessage());
            response.put("nodeId", nodeId);
            response.put("timestamp", LocalDateTime.now().toString());
            
            return response;
        }
    }
    
    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        try {
            String result = producerTemplate.requestBody("direct:health-check", null, String.class);
            
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("nodeId", nodeId);
            health.put("message", result);
            health.put("timestamp", LocalDateTime.now().toString());
            
            return health;
        } catch (Exception e) {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "DOWN");
            health.put("nodeId", nodeId);
            health.put("error", e.getMessage());
            health.put("timestamp", LocalDateTime.now().toString());
            
            return health;
        }
    }
    
    @GetMapping("/routes")
    public Map<String, Object> getRoutes() {
        Map<String, Object> routeInfo = new HashMap<>();
        routeInfo.put("nodeId", nodeId);
        routeInfo.put("totalRoutes", camelContext.getRoutes().size());
        
        Map<String, String> routes = new HashMap<>();
        camelContext.getRoutes().forEach(route -> {
            routes.put(route.getId(), "ACTIVE");
        });
        routeInfo.put("routes", routes);
        
        return routeInfo;
    }
} 