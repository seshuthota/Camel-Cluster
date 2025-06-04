package com.example.consumer.service;

import com.example.common.model.Order;
import com.example.common.model.ProcessedOrder;
import com.example.common.service.ClusterService;
import com.example.consumer.repository.ProcessedOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderProcessor.class);
    
    @Autowired
    private ProcessedOrderRepository repository;
    
    @Autowired
    private ClusterService clusterService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${spring.application.name:camel-consumer}")
    private String applicationName;
    
    private final AtomicLong processedCount = new AtomicLong(0);
    private volatile LocalDateTime startTime = LocalDateTime.now();
    
    @Transactional
    public void processOrder(String orderJson) {
        try {
            // Parse the order
            Order order = objectMapper.readValue(orderJson, Order.class);
            
            logger.info("Processing order: {} for customer: {} by node: {}", 
                order.getOrderId(), order.getCustomerName(), clusterService.getNodeId());
            
            // Simulate processing time
            Thread.sleep(100 + (long)(Math.random() * 200)); // 100-300ms
            
            // Create processed order entity
            ProcessedOrder processedOrder = ProcessedOrder.fromOrder(order, clusterService.getNodeId());
            
            // Save to database
            repository.save(processedOrder);
            
            // Update local metrics
            long count = processedCount.incrementAndGet();
            
            // Update cluster metrics
            clusterService.storeMetric("orders_processed", count);
            clusterService.storeMetric("consumer_last_processed", System.currentTimeMillis());
            
            logger.info("Order {} processed successfully. Total processed by this node: {}", 
                order.getOrderId(), count);
                
        } catch (Exception e) {
            logger.error("Failed to process order: {}", orderJson, e);
            clusterService.storeMetric("orders_failed", processedCount.get());
            throw new RuntimeException("Order processing failed", e);
        }
    }
    
    public long getProcessedCount() {
        return processedCount.get();
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public double getProcessingRate() {
        long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toSeconds();
        return duration > 0 ? (double) processedCount.get() / duration : 0.0;
    }
    
    public void resetMetrics() {
        processedCount.set(0);
        startTime = LocalDateTime.now();
        logger.info("Processing metrics reset for node: {}", clusterService.getNodeId());
    }
} 