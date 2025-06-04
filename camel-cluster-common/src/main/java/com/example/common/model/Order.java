package com.example.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Order model representing an order in the system.
 * Used for message passing between producer and consumer applications.
 */
public class Order {
    
    @JsonProperty("orderId")
    private String orderId;
    
    @JsonProperty("productName")
    private String productName;
    
    @JsonProperty("quantity")
    private Integer quantity;
    
    @JsonProperty("price")
    private Double price;
    
    @JsonProperty("customerName")
    private String customerName;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonProperty("generatedBy")
    private String generatedBy;

    // Default constructor
    public Order() {
        this.orderId = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.status = "PENDING";
    }

    // Constructor with basic fields
    public Order(String productName, Integer quantity, Double price, String customerName, String generatedBy) {
        this();
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
        this.customerName = customerName;
        this.generatedBy = generatedBy;
    }

    // Getters and Setters
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(String generatedBy) {
        this.generatedBy = generatedBy;
    }

    public Double getTotalAmount() {
        return quantity != null && price != null ? quantity * price : 0.0;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", productName='" + productName + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                ", customerName='" + customerName + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                ", generatedBy='" + generatedBy + '\'' +
                ", totalAmount=" + getTotalAmount() +
                '}';
    }
} 