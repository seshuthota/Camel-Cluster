package com.example.coordinator.repository;

import com.example.common.model.ProcessedOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrder, Long> {
    
    /**
     * Count orders processed in the last hour
     */
    @Query("SELECT COUNT(p) FROM ProcessedOrder p WHERE p.processedAt >= :since")
    long countOrdersSince(@Param("since") LocalDateTime since);
    
    /**
     * Get total revenue for a time period
     */
    @Query("SELECT COALESCE(SUM(p.orderTotal), 0) FROM ProcessedOrder p WHERE p.processedAt >= :since")
    BigDecimal getTotalRevenueSince(@Param("since") LocalDateTime since);
    
    /**
     * Get orders by processing node
     */
    @Query("SELECT p.processedBy, COUNT(p) as orderCount FROM ProcessedOrder p " +
           "WHERE p.processedAt >= :since GROUP BY p.processedBy")
    List<Object[]> getOrderCountByNode(@Param("since") LocalDateTime since);
    
    /**
     * Get top customers by order count
     */
    @Query("SELECT p.customerName, COUNT(p) as orderCount, SUM(p.orderTotal) as totalSpent " +
           "FROM ProcessedOrder p WHERE p.processedAt >= :since " +
           "GROUP BY p.customerName ORDER BY totalSpent DESC")
    List<Object[]> getTopCustomers(@Param("since") LocalDateTime since);
    
    /**
     * Get top products by quantity
     */
    @Query("SELECT p.productName, COUNT(p) as orderCount, SUM(p.quantity) as totalQuantity " +
           "FROM ProcessedOrder p WHERE p.processedAt >= :since " +
           "GROUP BY p.productName ORDER BY totalQuantity DESC")
    List<Object[]> getTopProducts(@Param("since") LocalDateTime since);
    
    /**
     * Get processing time statistics
     */
    @Query("SELECT " +
           "AVG(EXTRACT(EPOCH FROM (p.processedAt - p.createdAt))) as avgProcessingTime, " +
           "MIN(EXTRACT(EPOCH FROM (p.processedAt - p.createdAt))) as minProcessingTime, " +
           "MAX(EXTRACT(EPOCH FROM (p.processedAt - p.createdAt))) as maxProcessingTime " +
           "FROM ProcessedOrder p WHERE p.processedAt >= :since")
    Object[] getProcessingTimeStats(@Param("since") LocalDateTime since);
    
    /**
     * Get daily order trends
     */
    @Query("SELECT DATE(p.processedAt) as date, COUNT(p) as orderCount, SUM(p.orderTotal) as dailyRevenue " +
           "FROM ProcessedOrder p WHERE p.processedAt >= :since " +
           "GROUP BY DATE(p.processedAt) ORDER BY date")
    List<Object[]> getDailyTrends(@Param("since") LocalDateTime since);
    
    /**
     * Get hourly patterns
     */
    @Query("SELECT EXTRACT(HOUR FROM p.processedAt) as hour, COUNT(p) as orderCount, AVG(p.orderTotal) as avgOrderValue " +
           "FROM ProcessedOrder p WHERE p.processedAt >= :since " +
           "GROUP BY EXTRACT(HOUR FROM p.processedAt) ORDER BY hour")
    List<Object[]> getHourlyPatterns(@Param("since") LocalDateTime since);
    
    /**
     * Delete old orders for cleanup
     */
    @Query("DELETE FROM ProcessedOrder p WHERE p.processedAt < :cutoffDate")
    int deleteOldOrders(@Param("cutoffDate") LocalDateTime cutoffDate);
} 