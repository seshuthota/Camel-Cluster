package com.example.producer.service;

import com.example.common.model.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileGenerator {
    
    @Autowired
    private OrderGenerator orderGenerator;
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    
    public FileData createOrderFile() {
        int orderCount = 5 + (int)(Math.random() * 10); // 5-15 orders per file
        List<Order> orders = new ArrayList<>();
        
        for (int i = 0; i < orderCount; i++) {
            orders.add(orderGenerator.createOrder());
        }
        
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String content = generateCsvContent(orders);
        
        FileData fileData = new FileData();
        fileData.setFilename("orders-" + timestamp + ".csv");
        fileData.setContent(content);
        fileData.setOrderCount(orderCount);
        fileData.setTimestamp(timestamp);
        
        return fileData;
    }
    
    private String generateCsvContent(List<Order> orders) {
        StringBuilder csv = new StringBuilder();
        csv.append("OrderId,CustomerName,ProductName,Quantity,Price,CreatedAt,Status\n");
        
        for (Order order : orders) {
            csv.append(String.format("%s,%s,%s,%d,%.2f,%s,%s\n",
                order.getOrderId(),
                order.getCustomerName(),
                order.getProductName(),
                order.getQuantity(),
                order.getPrice(),
                order.getCreatedAt().toString(),
                order.getStatus()
            ));
        }
        
        return csv.toString();
    }
    
    public static class FileData {
        private String filename;
        private String content;
        private int orderCount;
        private String timestamp;
        
        // Getters and setters
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public int getOrderCount() { return orderCount; }
        public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }
} 