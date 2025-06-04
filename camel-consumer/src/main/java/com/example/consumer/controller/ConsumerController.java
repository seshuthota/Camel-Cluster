package com.example.consumer.controller;

import com.example.common.model.ProcessedOrder;
import com.example.common.service.ClusterService;
import com.example.consumer.repository.ProcessedOrderRepository;
import com.example.consumer.service.FileProcessor;
import com.example.consumer.service.OrderProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/consumer")
public class ConsumerController {
    
    @Autowired
    private OrderProcessor orderProcessor;
    
    @Autowired
    private FileProcessor fileProcessor;
    
    @Autowired
    private ProcessedOrderRepository repository;
    
    @Autowired
    private ClusterService clusterService;
    
    @Autowired
    private CamelContext camelContext;
    
    private final LocalDateTime startTime = LocalDateTime.now();
    
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("application", "camel-consumer");
        status.put("nodeId", clusterService.getNodeId());
        status.put("status", "RUNNING");
        status.put("startTime", startTime);
        status.put("uptime", java.time.Duration.between(startTime, LocalDateTime.now()).toSeconds());
        status.put("ordersProcessed", orderProcessor.getProcessedCount());
        status.put("filesProcessed", fileProcessor.getFilesProcessed());
        status.put("processingRate", orderProcessor.getProcessingRate());
        return status;
    }
    
    @GetMapping("/cluster")
    public Map<String, Object> getClusterInfo() {
        Map<String, Object> cluster = new HashMap<>();
        cluster.put("localNode", clusterService.getNodeId());
        cluster.put("isLeader", clusterService.isLeader());
        cluster.put("clusterSize", clusterService.getClusterSize());
        cluster.put("clusterMembers", clusterService.getClusterMembers());
        cluster.put("clusterStatus", clusterService.getClusterStatus());
        return cluster;
    }
    
    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Local metrics
        metrics.put("local", Map.of(
            "ordersProcessed", orderProcessor.getProcessedCount(),
            "filesProcessed", fileProcessor.getFilesProcessed(),
            "ordersFromFiles", fileProcessor.getOrdersFromFiles(),
            "processingRate", orderProcessor.getProcessingRate(),
            "startTime", orderProcessor.getStartTime()
        ));
        
        // Cluster metrics - simplified for now
        metrics.put("cluster", clusterService.getClusterStatus());
        
        // Database metrics
        try {
            metrics.put("database", Map.of(
                "totalOrders", repository.count(),
                "ordersToday", repository.countProcessedToday(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0)),
                "ordersLastHour", repository.countProcessedInLastHour(LocalDateTime.now().minusHours(1)),
                "totalRevenue", repository.getTotalRevenue(),
                "recentOrder", repository.findTopByOrderByProcessedAtDesc()
            ));
        } catch (Exception e) {
            metrics.put("database", Map.of("error", e.getMessage()));
        }
        
        return metrics;
    }
    
    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("nodeId", clusterService.getNodeId());
        health.put("timestamp", System.currentTimeMillis());
        
        // Check Camel context
        health.put("camelContext", Map.of(
            "status", camelContext.getStatus().toString(),
            "routeCount", camelContext.getRoutes().size(),
            "uptime", camelContext.getUptime()
        ));
        
        // Check database connectivity
        try {
            long count = repository.count();
            health.put("database", Map.of("status", "UP", "orderCount", count));
        } catch (Exception e) {
            health.put("database", Map.of("status", "DOWN", "error", e.getMessage()));
        }
        
        // Check cluster connectivity
        health.put("cluster", Map.of(
            "connected", clusterService.getClusterSize() > 0,
            "size", clusterService.getClusterSize(),
            "isLeader", clusterService.isLeader()
        ));
        
        return health;
    }
    
    @GetMapping("/routes")
    public Map<String, Object> getRoutes() {
        Map<String, Object> routes = new HashMap<>();
        List<Map<String, Object>> routeList = new ArrayList<>();
        
        for (Route route : camelContext.getRoutes()) {
            Map<String, Object> routeInfo = new HashMap<>();
            routeInfo.put("id", route.getId());
            routeInfo.put("endpoint", route.getEndpoint().getEndpointUri());
            routeInfo.put("status", route.getRouteController().getRouteStatus(route.getId()).toString());
            routeInfo.put("uptime", route.getUptime());
            routeList.add(routeInfo);
        }
        
        routes.put("routes", routeList);
        routes.put("totalRoutes", routeList.size());
        return routes;
    }
    
    @GetMapping("/orders")
    public Map<String, Object> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String product) {
        
        Map<String, Object> result = new HashMap<>();
        List<ProcessedOrder> orders;
        
        if (customer != null && !customer.isEmpty()) {
            orders = repository.findByCustomerNameContainingIgnoreCase(customer);
        } else if (product != null && !product.isEmpty()) {
            orders = repository.findByProductNameContainingIgnoreCase(product);
        } else {
            orders = repository.getRecentOrders();
        }
        
        result.put("orders", orders);
        result.put("totalCount", orders.size());
        result.put("page", page);
        result.put("size", size);
        
        return result;
    }
    
    @GetMapping("/orders/stats")
    public Map<String, Object> getOrderStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Basic counts
        stats.put("totalOrders", repository.count());
        stats.put("ordersToday", repository.countProcessedToday(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0)));
        stats.put("ordersLastHour", repository.countProcessedInLastHour(LocalDateTime.now().minusHours(1)));
        
        // Revenue
        stats.put("totalRevenue", repository.getTotalRevenue());
        stats.put("revenueByNode", repository.getTotalRevenueByNode(clusterService.getNodeId()));
        
        // Top customers and products
        stats.put("topCustomers", repository.getOrderCountByCustomer());
        stats.put("topProducts", repository.getOrderCountByProduct());
        stats.put("ordersByNode", repository.getOrderCountByNode());
        
        // High value orders
        stats.put("highValueOrders", repository.getHighValueOrders(1000.0));
        
        // Hourly statistics
        stats.put("hourlyStats", repository.getHourlyStatistics());
        
        return stats;
    }
    
    @GetMapping("/orders/{nodeId}")
    public List<ProcessedOrder> getOrdersByNode(@PathVariable String nodeId) {
        return repository.findByProcessedBy(nodeId);
    }
    
    @PostMapping("/process")
    public Map<String, Object> processOrder(@RequestBody String orderJson) {
        try {
            orderProcessor.processOrder(orderJson);
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Order processed successfully");
            result.put("processedBy", clusterService.getNodeId());
            result.put("timestamp", System.currentTimeMillis());
            
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
            
            return result;
        }
    }
    
    @PostMapping("/reset")
    public Map<String, Object> resetMetrics() {
        orderProcessor.resetMetrics();
        fileProcessor.resetMetrics();
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("message", "Metrics reset successfully");
        result.put("nodeId", clusterService.getNodeId());
        result.put("timestamp", System.currentTimeMillis());
        
        return result;
    }
} 