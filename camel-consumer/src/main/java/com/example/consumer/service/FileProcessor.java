package com.example.consumer.service;

import com.example.common.model.Order;
import com.example.common.model.ProcessedOrder;
import com.example.common.service.ClusterService;
import com.example.consumer.repository.ProcessedOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class FileProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(FileProcessor.class);
    
    @Autowired
    private ProcessedOrderRepository repository;
    
    @Autowired
    private ClusterService clusterService;
    
    private final AtomicLong filesProcessed = new AtomicLong(0);
    private final AtomicLong ordersFromFiles = new AtomicLong(0);
    
    @Transactional
    public void processFile(File file) {
        String nodeId = clusterService.getNodeId();
        String fileName = file.getName();
        
        // Simple file locking mechanism (checking if file is being processed)
        // For now, we'll use a simple approach without distributed locking
        
        try {
            logger.info("Node {} processing file: {}", nodeId, fileName);
            
            List<Order> orders = parseOrdersFromCsv(file);
            
            if (orders.isEmpty()) {
                logger.warn("No valid orders found in file: {}", fileName);
                return;
            }
            
            // Process all orders in the file
            List<ProcessedOrder> processedOrders = new ArrayList<>();
            for (Order order : orders) {
                ProcessedOrder processedOrder = ProcessedOrder.fromOrder(order, nodeId);
                processedOrders.add(processedOrder);
            }
            
            // Batch save to database
            repository.saveAll(processedOrders);
            
            // Update metrics
            long fileCount = filesProcessed.incrementAndGet();
            long orderCount = ordersFromFiles.addAndGet(orders.size());
            
            // Update cluster metrics
            clusterService.storeMetric("files_processed", fileCount);
            clusterService.storeMetric("orders_from_files", orderCount);
            clusterService.storeMetric("consumer_last_file_processed", System.currentTimeMillis());
            
            logger.info("File {} processed successfully by node {}. {} orders saved. " +
                       "Total files: {}, Total orders from files: {}", 
                       fileName, nodeId, orders.size(), fileCount, orderCount);
            
            // Mark file as processed by moving it
            moveProcessedFile(file);
            
        } catch (Exception e) {
            logger.error("Failed to process file: {}", fileName, e);
            clusterService.storeMetric("files_failed", filesProcessed.get());
            throw new RuntimeException("File processing failed: " + fileName, e);
        }
    }
    
    private List<Order> parseOrdersFromCsv(File file) throws Exception {
        List<Order> orders = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isFirstLine = true;
            
            while ((line = reader.readLine()) != null) {
                // Skip header
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                
                try {
                    Order order = parseOrderFromCsvLine(line);
                    if (order != null) {
                        orders.add(order);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse CSV line: {} - {}", line, e.getMessage());
                }
            }
        }
        
        return orders;
    }
    
    private Order parseOrderFromCsvLine(String csvLine) {
        String[] parts = csvLine.split(",");
        if (parts.length < 5) {
            logger.warn("Invalid CSV line format: {}", csvLine);
            return null;
        }
        
        try {
            Order order = new Order();
            order.setOrderId(parts[0].trim());
            order.setCustomerName(parts[1].trim());
            order.setProductName(parts[2].trim());
            order.setQuantity(Integer.parseInt(parts[3].trim()));
            order.setPrice(Double.parseDouble(parts[4].trim()));
            
            // Set current timestamp since CSV doesn't include it
            order.setCreatedAt(LocalDateTime.now());
            
            return order;
        } catch (NumberFormatException e) {
            logger.warn("Invalid number format in CSV line: {}", csvLine);
            return null;
        }
    }
    
    private void moveProcessedFile(File file) {
        try {
            File processedDir = new File(file.getParent(), "processed");
            if (!processedDir.exists()) {
                processedDir.mkdirs();
            }
            
            File processedFile = new File(processedDir, file.getName());
            if (file.renameTo(processedFile)) {
                logger.info("File moved to processed directory: {}", processedFile.getName());
            } else {
                logger.warn("Failed to move file to processed directory: {}", file.getName());
            }
        } catch (Exception e) {
            logger.error("Error moving processed file: {}", file.getName(), e);
        }
    }
    
    public long getFilesProcessed() {
        return filesProcessed.get();
    }
    
    public long getOrdersFromFiles() {
        return ordersFromFiles.get();
    }
    
    public void resetMetrics() {
        filesProcessed.set(0);
        ordersFromFiles.set(0);
        logger.info("File processing metrics reset for node: {}", clusterService.getNodeId());
    }
} 