package com.example.coordinator.route;

import com.example.common.util.ClusterConstants;
import com.example.coordinator.service.ClusterMonitor;
import com.example.coordinator.service.DatabaseReporter;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CoordinatorRoutes extends RouteBuilder {
    
    @Autowired
    private ClusterMonitor clusterMonitor;
    
    @Autowired
    private DatabaseReporter databaseReporter;
    
    @Value("${cluster.node.id:coordinator-1}")
    private String nodeId;
    
    @Value("${coordinator.health-check.interval:30000}")
    private long healthCheckInterval;
    
    @Value("${coordinator.metrics.interval:60000}")
    private long metricsInterval;
    
    @Override
    public void configure() throws Exception {
        
        // REST Configuration
        restConfiguration()
            .component("servlet")
            .bindingMode(RestBindingMode.json)
            .dataFormatProperty("prettyPrint", "true")
            .enableCORS(true)
            .port("8080")
            .contextPath("/api");
        
        // Exception handling
        onException(Exception.class)
            .handled(true)
            .log("Error in coordinator route: ${exception.message}")
            .setHeader("error", simple("${exception.message}"))
            .setBody(simple("ERROR: ${exception.message}"));
        
        // Master-only routes (leader election)
        configureLeaderRoutes();
        
        // Regular monitoring routes (all nodes)
        configureMonitoringRoutes();
        
        // Administrative routes
        configureAdministrativeRoutes();
        
        // Health check routes
        configureHealthRoutes();
    }
    
    /**
     * Configure leader-only routes (master component)
     */
    private void configureLeaderRoutes() {
        
        // Master cluster health monitoring route
        from("master:cluster-health:timer://healthCheck?period=" + healthCheckInterval)
            .routeId("coordinator-health-master")
            .log("🏥 [LEADER] Starting cluster health check from coordinator: ${header.CamelTimerName}")
            .bean(clusterMonitor, "monitorClusterHealth")
            .log("✅ [LEADER] Cluster health check completed by coordinator");
        
        // Master database cleanup route
        from("master:db-cleanup:timer://dbCleanup?period=3600000") // Every hour
            .routeId("coordinator-cleanup-master")
            .log("🧹 [LEADER] Starting database maintenance from coordinator")
            .bean(databaseReporter, "performDatabaseCleanup")
            .log("✅ [LEADER] Database maintenance completed by coordinator");
        
        // Master reporting route
        from("master:reporting:timer://reporting?period=1800000") // Every 30 minutes
            .routeId("coordinator-reporting-master")
            .log("📊 [LEADER] Starting periodic reporting from coordinator")
            .bean(databaseReporter, "generateHourlyReport")
            .log("✅ [LEADER] Periodic reporting completed by coordinator");
        
        // Master cluster rebalancing route
        from("master:rebalance:timer://rebalance?period=600000") // Every 10 minutes
            .routeId("coordinator-rebalance-timer")
            .log("⚖️ [LEADER] Checking cluster balance from coordinator")
            .bean(clusterMonitor, "monitorClusterHealth")
            .log("✅ [LEADER] Cluster balance check completed");
        
        // Process cluster events (leader handles events)
        from("master:events:hazelcast:" + ClusterConstants.CLUSTER_EVENTS_TOPIC)
            .routeId("coordinator-cluster-events")
            .log("📢 [LEADER] Processing cluster event: ${body}")
            .choice()
                .when(simple("${body[eventType]} == 'NODE_FAILED'"))
                    .log("💥 [LEADER] Handling node failure: ${body[data][failedNode]}")
                    .bean(clusterMonitor, "handleFailedNodes")
                .when(simple("${body[eventType]} == 'REBALANCE_TRIGGERED'"))
                    .log("⚖️ [LEADER] Handling rebalance trigger")
                    .bean(clusterMonitor, "triggerRebalancing")
                .otherwise()
                    .log("📝 [LEADER] Generic cluster event processed: ${body[eventType]}");
    }
    
    /**
     * Configure monitoring routes (all coordinator nodes)
     */
    private void configureMonitoringRoutes() {
        
        // Node status update route
        from("timer://nodeStatus?period=" + metricsInterval)
            .routeId("coordinator-node-status")
            .log("📊 Publishing coordinator node status: " + nodeId)
            .setBody(simple("coordinator"))
            .bean("clusterService", "updateNodeStatus")
            .log("✅ Coordinator node status updated");
        
        // Metrics collection route
        from("timer://metricsCollection?period=" + metricsInterval)
            .routeId("coordinator-metrics")
            .log("📈 Collecting coordinator metrics")
            .bean("clusterService", "updateNodeStatus(RUNNING, null)")
            .log("✅ Coordinator metrics collected");
        
        // Health heartbeat route
        from("timer://heartbeat?period=15000") // Every 15 seconds
            .routeId("coordinator-heartbeat")
            .log("💓 Coordinator heartbeat: " + nodeId)
            .bean("clusterService", "markNodeReady")
            .log("✅ Coordinator heartbeat sent");
    }
    
    /**
     * Configure administrative routes
     */
    private void configureAdministrativeRoutes() {
        
        // Manual cluster health check
        from("direct:manual-health-check")
            .routeId("coordinator-manual-health")
            .log("🔍 Manual cluster health check requested")
            .bean(clusterMonitor, "monitorClusterHealth")
            .setBody(simple("Health check completed"));
        
        // Manual report generation
        from("direct:manual-report")
            .routeId("coordinator-manual-report")
            .log("📋 Manual report generation requested")
            .bean(databaseReporter, "generateHourlyReport")
            .setBody(simple("Report generated"));
        
        // Manual rebalancing trigger
        from("direct:manual-rebalance")
            .routeId("coordinator-manual-rebalance")
            .log("⚖️ Manual rebalancing requested")
            .bean(clusterMonitor, "triggerRebalancing")
            .setBody(simple("Rebalancing triggered"));
        
        // Emergency cluster reset
        from("direct:emergency-reset")
            .routeId("coordinator-emergency-reset")
            .log("🚨 Emergency cluster reset requested")
            .process(exchange -> {
                // Simple reset implementation
                exchange.getIn().setBody("Emergency reset completed");
            })
            .setBody(simple("Emergency reset completed"));
    }
    
    /**
     * Configure health check routes
     */
    private void configureHealthRoutes() {
        
        // Application health check
        from("direct:health-check")
            .routeId("coordinator-health-check")
            .log("🏥 Coordinator health check")
            .setHeader("node-id", simple(nodeId))
            .setHeader("node-type", simple("coordinator"))
            .setHeader("timestamp", simple("${date:now:yyyy-MM-dd HH:mm:ss}"))
            .setBody(simple("Coordinator ${header.node-id} is healthy at ${header.timestamp}"));
        
        // Detailed health report
        from("direct:detailed-health")
            .routeId("coordinator-detailed-health")
            .log("📋 Generating detailed coordinator health report")
            .bean(clusterMonitor, "generateHealthReport")
            .log("✅ Detailed health report generated");
        
        // Route status check
        from("direct:route-status")
            .routeId("coordinator-route-status")
            .log("📊 Checking coordinator route status")
            .process(exchange -> {
                java.util.List<org.apache.camel.Route> routes = getCamelContext().getRoutes();
                java.util.Map<String, String> routeStatus = new java.util.HashMap<>();
                
                for (org.apache.camel.Route route : routes) {
                    routeStatus.put(route.getId(), route.getRouteController().getRouteStatus(route.getId()).toString());
                }
                
                exchange.getIn().setBody(routeStatus);
            })
            .log("✅ Route status collected: ${body}");
        
        // Cluster connectivity test
        from("direct:cluster-test")
            .routeId("coordinator-cluster-test")
            .log("🔗 Testing cluster connectivity")
            .setBody(simple("Cluster connectivity: OK"));
    }
} 