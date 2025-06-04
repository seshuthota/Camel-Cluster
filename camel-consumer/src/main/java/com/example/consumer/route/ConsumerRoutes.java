package com.example.consumer.route;

import com.example.common.service.ClusterService;
import com.example.common.util.ClusterConstants;
import com.example.consumer.service.FileProcessor;
import com.example.consumer.service.OrderProcessor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;

@Component
public class ConsumerRoutes extends RouteBuilder {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsumerRoutes.class);
    
    @Autowired
    private OrderProcessor orderProcessor;
    
    @Autowired
    private FileProcessor fileProcessor;
    
    @Autowired
    private ClusterService clusterService;
    
    @Value("${app.consumer.file-polling-interval:10000}")
    private int filePollingInterval;
    
    @Value("${app.consumer.concurrent-consumers:3}")
    private int concurrentConsumers;
    
    @Value("${app.consumer.input-directory:/shared/orders}")
    private String inputDirectory;
    
    @Override
    public void configure() throws Exception {
        
        // Global error handling
        onException(Exception.class)
            .handled(true)
            .log("Consumer error: ${exception.message}")
            .to("log:error");
        
        // Route 1: Consume orders from ActiveMQ queue
        from("activemq:queue:" + ClusterConstants.ORDERS_QUEUE + 
             "?concurrentConsumers=" + concurrentConsumers +
             "&maxConcurrentConsumers=" + (concurrentConsumers * 2))
            .routeId(ClusterConstants.CONSUMER_ORDER_ROUTE_ID)
            .log("Consumer ${header.CamelJMSDestination} received order on node: " + 
                 clusterService.getNodeId())
            .process(exchange -> {
                String orderJson = exchange.getIn().getBody(String.class);
                orderProcessor.processOrder(orderJson);
                
                // Set response for potential monitoring
                exchange.getIn().setHeader("ProcessedBy", clusterService.getNodeId());
                exchange.getIn().setHeader("ProcessedAt", System.currentTimeMillis());
            })
            .log("Order processed successfully by consumer node: " + clusterService.getNodeId());
        
        // Route 2: File polling with cluster coordination
        from("file:" + inputDirectory + 
             "?delay=" + filePollingInterval +
             "&delete=false" +
             "&include=.*\\.csv" +
             "&readLock=changed" +
             "&readLockCheckInterval=1000" +
             "&readLockTimeout=10000")
            .routeId(ClusterConstants.CONSUMER_FILE_ROUTE_ID)
            .log("Consumer found file: ${header.CamelFileName} on node: " + 
                 clusterService.getNodeId())
            .process(exchange -> {
                File file = exchange.getIn().getBody(File.class);
                fileProcessor.processFile(file);
            })
            .log("File processing completed by node: " + clusterService.getNodeId());
        
        // Route 3: Health check and metrics publishing
        from("timer:consumer-health?period=30000")
            .routeId(ClusterConstants.CONSUMER_HEALTH_ROUTE_ID)
            .process(exchange -> {
                String nodeId = clusterService.getNodeId();
                boolean isLeader = clusterService.isLeader();
                
                // Update node status with proper format
                Map<String, Object> statusInfo = Map.of(
                    "status", "ACTIVE",
                    "description", "Consumer processing orders and files",
                    "ordersProcessed", orderProcessor.getProcessedCount(),
                    "filesProcessed", fileProcessor.getFilesProcessed()
                );
                clusterService.updateNodeStatus("ACTIVE", statusInfo);
                
                // Publish health metrics
                clusterService.storeMetric("consumer_last_heartbeat", System.currentTimeMillis());
                clusterService.storeMetric("consumer_processed_count", orderProcessor.getProcessedCount());
                clusterService.storeMetric("consumer_files_processed", fileProcessor.getFilesProcessed());
                
                logger.debug("Consumer health check - Node: {}, Leader: {}, " +
                           "Orders processed: {}, Files processed: {}", 
                           nodeId, isLeader, 
                           orderProcessor.getProcessedCount(),
                           fileProcessor.getFilesProcessed());
            });
        
        // Route 4: Cluster event handling
        from("hazelcast-topic:" + ClusterConstants.CLUSTER_EVENTS_TOPIC)
            .routeId(ClusterConstants.CONSUMER_CLUSTER_EVENTS_ROUTE_ID)
            .log("Consumer received cluster event: ${body}")
            .process(exchange -> {
                String event = exchange.getIn().getBody(String.class);
                handleClusterEvent(event);
            });
        
        // Route 5: Dead letter queue handling
        from("activemq:queue:" + ClusterConstants.ORDERS_QUEUE + ".DLQ")
            .routeId("consumer-dlq-handler")
            .log("Processing dead letter message: ${body}")
            .process(exchange -> {
                // Log dead letter for investigation
                String message = exchange.getIn().getBody(String.class);
                logger.error("Dead letter received: {}", message);
                
                // Update failure metrics
                clusterService.storeMetric("orders_dead_letter", 1L);
            });
        
        // Route 6: Manual processing endpoint (for testing)
        from("direct:process-order")
            .routeId("consumer-manual-process")
            .log("Manual order processing triggered")
            .process(exchange -> {
                String orderJson = exchange.getIn().getBody(String.class);
                orderProcessor.processOrder(orderJson);
            });
    }
    
    private void handleClusterEvent(String event) {
        try {
            if (event.contains("NODE_JOINED")) {
                logger.info("New node joined the cluster");
                // Could rebalance work here
            } else if (event.contains("NODE_LEFT")) {
                logger.info("Node left the cluster");
                // Could handle failover here
            } else if (event.contains("LEADER_CHANGED")) {
                logger.info("Cluster leadership changed");
                // Update local state if needed
            }
        } catch (Exception e) {
            logger.error("Error handling cluster event: {}", event, e);
        }
    }
} 