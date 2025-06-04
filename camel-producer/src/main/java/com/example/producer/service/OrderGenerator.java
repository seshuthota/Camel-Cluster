package com.example.producer.service;

import com.example.common.model.Order;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class OrderGenerator {
    
    private final Random random = new Random();
    private static final List<String> PRODUCTS = Arrays.asList(
        "Laptop", "Smartphone", "Tablet", "Headphones", "Monitor", 
        "Keyboard", "Mouse", "Webcam", "Speaker", "Charger"
    );
    private static final List<String> CUSTOMERS = Arrays.asList(
        "John Smith", "Jane Doe", "Bob Johnson", "Alice Brown", "Charlie Wilson",
        "Diana Davis", "Eve Miller", "Frank Thompson", "Grace Lee", "Henry Clark"
    );
    
    public Order createOrder() {
        Order order = new Order();
        order.setOrderId(UUID.randomUUID().toString());
        order.setCustomerName(CUSTOMERS.get(random.nextInt(CUSTOMERS.size())));
        order.setProductName(PRODUCTS.get(random.nextInt(PRODUCTS.size())));
        order.setQuantity(random.nextInt(10) + 1); // 1-10 items
        order.setPrice(random.nextDouble() * 1000 + 10); // $10-$1010
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus("PENDING");
        
        return order;
    }
} 