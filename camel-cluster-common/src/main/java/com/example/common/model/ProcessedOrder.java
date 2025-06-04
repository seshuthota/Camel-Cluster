package com.example.common.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * JPA Entity representing a processed order in the database.
 * Used by consumer applications to persist completed order processing.
 */
@Entity
@Table(name = "processed_orders")
public class ProcessedOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "processed_by", length = 100)
    private String processedBy;

    @Column(name = "processed_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime processedAt;

    @Column(name = "status", length = 50)
    private String status;

    // Constructors
    public ProcessedOrder() {
        this.processedAt = LocalDateTime.now();
        this.status = "COMPLETED";
    }

    public ProcessedOrder(String orderId, String content, String processedBy) {
        this();
        this.orderId = orderId;
        this.content = content;
        this.processedBy = processedBy;
    }

    // Static factory method to create from Order
    public static ProcessedOrder fromOrder(Order order, String processedBy) {
        ProcessedOrder processedOrder = new ProcessedOrder();
        processedOrder.setOrderId(order.getOrderId());
        processedOrder.setContent(order.toString());
        processedOrder.setProcessedBy(processedBy);
        return processedOrder;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "ProcessedOrder{" +
                "id=" + id +
                ", orderId='" + orderId + '\'' +
                ", processedBy='" + processedBy + '\'' +
                ", processedAt=" + processedAt +
                ", status='" + status + '\'' +
                '}';
    }
} 