package com.example.consumer.repository;

import com.example.common.model.ProcessedOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrder, Long> {
    
    // Basic finders
    List<ProcessedOrder> findByProcessedBy(String processedBy);
    List<ProcessedOrder> findByOrderId(String orderId);
    List<ProcessedOrder> findByStatus(String status);
    
    // Search queries (simplified)
    @Query("SELECT p FROM ProcessedOrder p WHERE p.content LIKE %:customer%")
    List<ProcessedOrder> findByCustomerNameContainingIgnoreCase(@Param("customer") String customer);
    
    @Query("SELECT p FROM ProcessedOrder p WHERE p.content LIKE %:product%")
    List<ProcessedOrder> findByProductNameContainingIgnoreCase(@Param("product") String product);
    
    // Date-based queries (simplified)
    @Query("SELECT COUNT(p) FROM ProcessedOrder p WHERE p.processedAt >= :today")
    long countProcessedToday(@Param("today") LocalDateTime today);
    
    @Query("SELECT COUNT(p) FROM ProcessedOrder p WHERE p.processedAt >= :since")
    long countProcessedInLastHour(@Param("since") LocalDateTime since);
    
    // Statistics queries (simple count-based)
    @Query("SELECT COUNT(p) FROM ProcessedOrder p")
    long getTotalRevenue();
    
    @Query("SELECT COUNT(p) FROM ProcessedOrder p WHERE p.processedBy = :nodeId")
    long getTotalRevenueByNode(@Param("nodeId") String nodeId);
    
    // Basic aggregation queries
    @Query("SELECT p.processedBy, COUNT(p) FROM ProcessedOrder p GROUP BY p.processedBy")
    List<Object[]> getOrderCountByNode();
    
    @Query("SELECT p.status, COUNT(p) FROM ProcessedOrder p GROUP BY p.status")
    List<Object[]> getOrderCountByCustomer();
    
    @Query("SELECT DATE(p.processedAt), COUNT(p) FROM ProcessedOrder p GROUP BY DATE(p.processedAt)")
    List<Object[]> getOrderCountByProduct();
    
    // Simplified high value orders (just recent orders)
    @Query("SELECT p FROM ProcessedOrder p ORDER BY p.processedAt DESC")
    List<ProcessedOrder> getHighValueOrders(@Param("minValue") Double minValue);
    
    // Simple hourly stats
    @Query("SELECT COUNT(p) FROM ProcessedOrder p")
    List<Object[]> getHourlyStatistics();
    
    // Recent orders
    @Query("SELECT p FROM ProcessedOrder p ORDER BY p.processedAt DESC")
    List<ProcessedOrder> getRecentOrders();
    
    // Duplicate detection
    List<ProcessedOrder> findByOrderIdOrderByProcessedAtDesc(String orderId);
    
    // Find top by processed time
    ProcessedOrder findTopByOrderByProcessedAtDesc();
} 