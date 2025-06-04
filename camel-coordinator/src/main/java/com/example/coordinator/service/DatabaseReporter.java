package com.example.coordinator.service;

import com.example.common.service.ClusterService;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DatabaseReporter {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseReporter.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ClusterService clusterService;
    
    @Autowired
    private HazelcastInstance hazelcastInstance;
    
    @Value("${cluster.node.id:coordinator-1}")
    private String nodeId;
    
    @Value("${coordinator.reports.cleanup-days:30}")
    private int cleanupDays;
    
    @Value("${coordinator.reports.batch-size:1000}")
    private int batchSize;
    
    /**
     * Generate hourly processing report (leader only)
     */
    @Scheduled(cron = "0 0 * * * ?") // Every hour
    public void generateHourlyReport() {
        if (!clusterService.isLeader()) {
            logger.debug("Skipping hourly report - not leader");
            return;
        }
        
        logger.info("Generating hourly processing report...");
        
        try {
            Map<String, Object> report = createProcessingReport("HOURLY");
            
            // Store report in database
            storeReport(report);
            
            // Share report with cluster
            shareReportWithCluster(report);
            
            logger.info("Hourly report generated successfully");
            
        } catch (Exception e) {
            logger.error("Error generating hourly report", e);
        }
    }
    
    /**
     * Generate daily processing report (leader only)
     */
    @Scheduled(cron = "0 0 6 * * ?") // Every day at 6 AM
    public void generateDailyReport() {
        if (!clusterService.isLeader()) {
            logger.debug("Skipping daily report - not leader");
            return;
        }
        
        logger.info("Generating daily processing report...");
        
        try {
            Map<String, Object> report = createProcessingReport("DAILY");
            
            // Store report in database
            storeReport(report);
            
            // Share report with cluster
            shareReportWithCluster(report);
            
            // Generate detailed analytics
            Map<String, Object> analytics = generateAnalytics();
            storeAnalytics(analytics);
            
            logger.info("Daily report and analytics generated successfully");
            
        } catch (Exception e) {
            logger.error("Error generating daily report", e);
        }
    }
    
    /**
     * Database cleanup - remove old records (leader only)
     */
    @Scheduled(cron = "0 0 2 * * ?") // Every day at 2 AM
    public void performDatabaseCleanup() {
        if (!clusterService.isLeader()) {
            logger.debug("Skipping database cleanup - not leader");
            return;
        }
        
        logger.info("Starting database cleanup...");
        
        try {
            // Clean up old processed orders
            int deletedOrders = cleanupOldProcessedOrders();
            
            // Clean up old reports
            int deletedReports = cleanupOldReports();
            
            // Clean up old analytics
            int deletedAnalytics = cleanupOldAnalytics();
            
            // Update cluster metrics
            updateCleanupMetrics(deletedOrders, deletedReports, deletedAnalytics);
            
            logger.info("Database cleanup completed. Deleted: {} orders, {} reports, {} analytics", 
                       deletedOrders, deletedReports, deletedAnalytics);
            
        } catch (Exception e) {
            logger.error("Error during database cleanup", e);
        }
    }
    
    /**
     * Create processing report for specified period
     */
    private Map<String, Object> createProcessingReport(String period) {
        Map<String, Object> report = new HashMap<>();
        
        try {
            // Basic report info
            report.put("reportId", UUID.randomUUID().toString());
            report.put("period", period);
            report.put("generatedAt", LocalDateTime.now());
            report.put("generatedBy", nodeId);
            
            // Calculate date range based on period
            String dateCondition = period.equals("HOURLY") ? 
                "processed_at >= NOW() - INTERVAL '1 hour'" : 
                "processed_at >= NOW() - INTERVAL '1 day'";
            
            // Total processed orders
            Integer totalOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_orders WHERE " + dateCondition,
                Integer.class
            );
            report.put("totalOrders", totalOrders != null ? totalOrders : 0);
            
            // Total revenue
            BigDecimal totalRevenue = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(order_total), 0) FROM processed_orders WHERE " + dateCondition,
                BigDecimal.class
            );
            report.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);
            
            // Orders by node
            List<Map<String, Object>> ordersByNode = jdbcTemplate.queryForList(
                "SELECT processed_by, COUNT(*) as order_count, SUM(order_total) as revenue " +
                "FROM processed_orders WHERE " + dateCondition + " GROUP BY processed_by"
            );
            report.put("ordersByNode", ordersByNode);
            
            // Top customers
            List<Map<String, Object>> topCustomers = jdbcTemplate.queryForList(
                "SELECT customer_name, COUNT(*) as order_count, SUM(order_total) as total_spent " +
                "FROM processed_orders WHERE " + dateCondition + " " +
                "GROUP BY customer_name ORDER BY total_spent DESC LIMIT 10"
            );
            report.put("topCustomers", topCustomers);
            
            // Top products
            List<Map<String, Object>> topProducts = jdbcTemplate.queryForList(
                "SELECT product_name, COUNT(*) as order_count, SUM(quantity) as total_quantity " +
                "FROM processed_orders WHERE " + dateCondition + " " +
                "GROUP BY product_name ORDER BY total_quantity DESC LIMIT 10"
            );
            report.put("topProducts", topProducts);
            
            // Processing times
            Map<String, Object> processingTimes = jdbcTemplate.queryForMap(
                "SELECT " +
                "  AVG(EXTRACT(EPOCH FROM (processed_at - created_at))) as avg_processing_time, " +
                "  MIN(EXTRACT(EPOCH FROM (processed_at - created_at))) as min_processing_time, " +
                "  MAX(EXTRACT(EPOCH FROM (processed_at - created_at))) as max_processing_time " +
                "FROM processed_orders WHERE " + dateCondition
            );
            report.put("processingTimes", processingTimes);
            
        } catch (Exception e) {
            logger.error("Error creating processing report", e);
            report.put("error", e.getMessage());
        }
        
        return report;
    }
    
    /**
     * Generate detailed analytics
     */
    private Map<String, Object> generateAnalytics() {
        Map<String, Object> analytics = new HashMap<>();
        
        try {
            analytics.put("analyticsId", UUID.randomUUID().toString());
            analytics.put("generatedAt", LocalDateTime.now());
            analytics.put("generatedBy", nodeId);
            
            // Revenue trends (last 7 days)
            List<Map<String, Object>> revenueTrends = jdbcTemplate.queryForList(
                "SELECT " +
                "  DATE(processed_at) as date, " +
                "  COUNT(*) as order_count, " +
                "  SUM(order_total) as daily_revenue " +
                "FROM processed_orders " +
                "WHERE processed_at >= NOW() - INTERVAL '7 days' " +
                "GROUP BY DATE(processed_at) " +
                "ORDER BY date"
            );
            analytics.put("revenueTrends", revenueTrends);
            
            // Hourly patterns
            List<Map<String, Object>> hourlyPatterns = jdbcTemplate.queryForList(
                "SELECT " +
                "  EXTRACT(HOUR FROM processed_at) as hour, " +
                "  COUNT(*) as order_count, " +
                "  AVG(order_total) as avg_order_value " +
                "FROM processed_orders " +
                "WHERE processed_at >= NOW() - INTERVAL '7 days' " +
                "GROUP BY EXTRACT(HOUR FROM processed_at) " +
                "ORDER BY hour"
            );
            analytics.put("hourlyPatterns", hourlyPatterns);
            
            // Customer behavior analysis
            Map<String, Object> customerAnalysis = jdbcTemplate.queryForMap(
                "SELECT " +
                "  COUNT(DISTINCT customer_name) as unique_customers, " +
                "  AVG(order_total) as avg_order_value, " +
                "  AVG(quantity) as avg_quantity " +
                "FROM processed_orders " +
                "WHERE processed_at >= NOW() - INTERVAL '7 days'"
            );
            analytics.put("customerAnalysis", customerAnalysis);
            
            // Node performance comparison
            List<Map<String, Object>> nodePerformance = jdbcTemplate.queryForList(
                "SELECT " +
                "  processed_by, " +
                "  COUNT(*) as orders_processed, " +
                "  AVG(EXTRACT(EPOCH FROM (processed_at - created_at))) as avg_processing_time, " +
                "  MIN(processed_at) as first_order, " +
                "  MAX(processed_at) as last_order " +
                "FROM processed_orders " +
                "WHERE processed_at >= NOW() - INTERVAL '7 days' " +
                "GROUP BY processed_by " +
                "ORDER BY orders_processed DESC"
            );
            analytics.put("nodePerformance", nodePerformance);
            
        } catch (Exception e) {
            logger.error("Error generating analytics", e);
            analytics.put("error", e.getMessage());
        }
        
        return analytics;
    }
    
    /**
     * Store report in database
     */
    private void storeReport(Map<String, Object> report) {
        try {
            String reportJson = convertToJson(report);
            
            jdbcTemplate.update(
                "INSERT INTO cluster_reports (report_id, report_type, report_data, generated_at, generated_by) " +
                "VALUES (?, ?, ?::jsonb, ?, ?)",
                report.get("reportId"),
                report.get("period"),
                reportJson,
                report.get("generatedAt"),
                report.get("generatedBy")
            );
            
        } catch (Exception e) {
            logger.error("Error storing report in database", e);
        }
    }
    
    /**
     * Store analytics in database
     */
    private void storeAnalytics(Map<String, Object> analytics) {
        try {
            String analyticsJson = convertToJson(analytics);
            
            jdbcTemplate.update(
                "INSERT INTO cluster_analytics (analytics_id, analytics_data, generated_at, generated_by) " +
                "VALUES (?, ?::jsonb, ?, ?)",
                analytics.get("analyticsId"),
                analyticsJson,
                analytics.get("generatedAt"),
                analytics.get("generatedBy")
            );
            
        } catch (Exception e) {
            logger.error("Error storing analytics in database", e);
        }
    }
    
    /**
     * Share report with cluster members
     */
    private void shareReportWithCluster(Map<String, Object> report) {
        try {
            IMap<String, Object> reportsMap = hazelcastInstance.getMap("cluster_reports");
            String reportKey = String.format("%s_%s_%s", 
                report.get("period"), 
                report.get("generatedAt").toString().replaceAll(":", "-"),
                nodeId);
            
            reportsMap.put(reportKey, report, 24, java.util.concurrent.TimeUnit.HOURS);
            
            // Publish cluster event using publishToTopic
            Map<String, Object> eventData = Map.of(
                "eventType", "REPORT_GENERATED",
                "reportId", report.get("reportId"), 
                "period", report.get("period"),
                "timestamp", System.currentTimeMillis(),
                "sourceNode", nodeId
            );
            clusterService.publishToTopic("cluster-events", eventData);
            
        } catch (Exception e) {
            logger.error("Error sharing report with cluster", e);
        }
    }
    
    /**
     * Cleanup old processed orders
     */
    private int cleanupOldProcessedOrders() {
        return jdbcTemplate.update(
            "DELETE FROM processed_orders WHERE processed_at < NOW() - INTERVAL ? DAY",
            cleanupDays
        );
    }
    
    /**
     * Cleanup old reports
     */
    private int cleanupOldReports() {
        return jdbcTemplate.update(
            "DELETE FROM cluster_reports WHERE generated_at < NOW() - INTERVAL ? DAY",
            cleanupDays
        );
    }
    
    /**
     * Cleanup old analytics
     */
    private int cleanupOldAnalytics() {
        return jdbcTemplate.update(
            "DELETE FROM cluster_analytics WHERE generated_at < NOW() - INTERVAL ? DAY",
            cleanupDays
        );
    }
    
    /**
     * Update cleanup metrics in cluster
     */
    private void updateCleanupMetrics(int deletedOrders, int deletedReports, int deletedAnalytics) {
        try {
            IMap<String, Object> metricsMap = hazelcastInstance.getMap("cluster_metrics");
            
            Map<String, Object> cleanupMetrics = new HashMap<>();
            cleanupMetrics.put("lastCleanupAt", LocalDateTime.now());
            cleanupMetrics.put("deletedOrders", deletedOrders);
            cleanupMetrics.put("deletedReports", deletedReports);
            cleanupMetrics.put("deletedAnalytics", deletedAnalytics);
            cleanupMetrics.put("cleanupBy", nodeId);
            
            metricsMap.put("lastCleanup", cleanupMetrics);
            
        } catch (Exception e) {
            logger.error("Error updating cleanup metrics", e);
        }
    }
    
    /**
     * Get latest reports from cluster
     */
    public List<Map<String, Object>> getLatestReports(int limit) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT report_id, report_type, report_data, generated_at, generated_by " +
                "FROM cluster_reports " +
                "ORDER BY generated_at DESC " +
                "LIMIT ?",
                limit
            );
        } catch (Exception e) {
            logger.error("Error getting latest reports", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get latest analytics from cluster
     */
    public List<Map<String, Object>> getLatestAnalytics(int limit) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT analytics_id, analytics_data, generated_at, generated_by " +
                "FROM cluster_analytics " +
                "ORDER BY generated_at DESC " +
                "LIMIT ?",
                limit
            );
        } catch (Exception e) {
            logger.error("Error getting latest analytics", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Generate on-demand report
     */
    public Map<String, Object> generateOnDemandReport(String period, String startDate, String endDate) {
        if (!clusterService.isLeader()) {
            throw new IllegalStateException("Only leader can generate on-demand reports");
        }
        
        Map<String, Object> report = new HashMap<>();
        
        try {
            report.put("reportId", UUID.randomUUID().toString());
            report.put("period", "ON_DEMAND");
            report.put("startDate", startDate);
            report.put("endDate", endDate);
            report.put("generatedAt", LocalDateTime.now());
            report.put("generatedBy", nodeId);
            
            String dateCondition = "processed_at >= ? AND processed_at <= ?";
            
            // Execute queries with date range
            Integer totalOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_orders WHERE " + dateCondition,
                Integer.class, startDate, endDate
            );
            report.put("totalOrders", totalOrders != null ? totalOrders : 0);
            
            BigDecimal totalRevenue = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(order_total), 0) FROM processed_orders WHERE " + dateCondition,
                BigDecimal.class, startDate, endDate
            );
            report.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);
            
            // Store and share report
            storeReport(report);
            shareReportWithCluster(report);
            
        } catch (Exception e) {
            logger.error("Error generating on-demand report", e);
            report.put("error", e.getMessage());
        }
        
        return report;
    }
    
    /**
     * Convert map to JSON string (simple implementation)
     */
    private String convertToJson(Map<String, Object> data) {
        // Simple JSON conversion - in production, use Jackson or similar
        return data.toString();
    }
} 