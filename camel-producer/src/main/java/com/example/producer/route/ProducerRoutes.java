package com.example.producer.route;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProducerRoutes extends RouteBuilder {
    
    @Value("${cluster.node.id:producer-1}")
    private String nodeId;
    
    @Value("${producer.order.interval:2000}")
    private String orderInterval;
    
    @Value("${producer.file.interval:10000}")
    private String fileInterval;
    
    @Value("${shared.file.path:/shared/input}")
    private String sharedFilePath;
    
    @Autowired
    private Environment environment;
    
    @Override
    public void configure() throws Exception {
        
        // Determine target endpoint based on active profile
        String orderEndpoint = "mock:orders";
        if (!java.util.Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            orderEndpoint = "activemq:queue:orders?exchangePattern=InOnly";
        }
        
        // Order generation route - generates orders every 2 seconds
        from("timer:order-generator?period=" + orderInterval)
            .routeId("order-generator")
            .log("PRODUCER ${exchangeProperty.CamelTimerName}: Starting order generation on node " + nodeId)
            .bean("orderGenerator", "createOrder")
            .setHeader("nodeId", constant(nodeId))
            .setHeader("CamelJmsDestinationName", constant("orders"))
            .log("PRODUCER " + nodeId + ": Generated order ${body.orderId} for customer ${body.customerName}")
            .convertBodyTo(String.class)
            .to(orderEndpoint)
            .log("PRODUCER " + nodeId + ": Order sent to queue/mock");
        
        // File generation route - creates CSV files every 10 seconds
        from("timer:file-generator?period=" + fileInterval)
            .routeId("file-generator")
            .log("PRODUCER ${exchangeProperty.CamelTimerName}: Starting file generation on node " + nodeId)
            .bean("fileGenerator", "createOrderFile")
            .setHeader("timestamp", simple("${body.timestamp}"))
            .setHeader("CamelFileName", simple("${body.filename}"))
            .setHeader("orderCount", simple("${body.orderCount}"))
            .log("PRODUCER " + nodeId + ": Generated file ${header.CamelFileName} with ${header.orderCount} orders")
            .transform(simple("${body.content}"))
            .to("file:" + sharedFilePath + "?fileName=${header.CamelFileName}")
            .log("PRODUCER " + nodeId + ": File ${header.CamelFileName} written to shared directory");
        
        // Manual order generation endpoint
        from("direct:generate-order")
            .routeId("manual-order-generator")
            .log("PRODUCER " + nodeId + ": Manual order generation triggered")
            .bean("orderGenerator", "createOrder")
            .setHeader("nodeId", constant(nodeId))
            .setHeader("manual", constant(true))
            .log("PRODUCER " + nodeId + ": Manual order ${body.orderId} generated")
            .convertBodyTo(String.class)
            .to(orderEndpoint)
            .log("PRODUCER " + nodeId + ": Manual order sent to queue/mock");
        
        // Health check route
        from("direct:health-check")
            .routeId("producer-health")
            .setBody(simple("Producer " + nodeId + " is healthy at ${date:now:yyyy-MM-dd HH:mm:ss}"))
            .log("PRODUCER " + nodeId + ": Health check performed");
    }
} 