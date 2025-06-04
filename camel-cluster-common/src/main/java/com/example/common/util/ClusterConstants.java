package com.example.common.util;

/**
 * Constants shared across the Camel cluster applications.
 * Contains queue names, topic names, and other configuration constants.
 */
public final class ClusterConstants {

    private ClusterConstants() {
        // Utility class - prevent instantiation
    }

    // ActiveMQ Queue Names
    public static final String ORDERS_QUEUE = "orders";
    public static final String COMPLETED_ORDERS_QUEUE = "completed-orders";
    public static final String FAILED_ORDERS_QUEUE = "failed-orders";

    // Hazelcast Topic Names
    public static final String ADMIN_NOTIFICATIONS_TOPIC = "admin-notifications";
    public static final String HEALTH_STATUS_TOPIC = "health-status";
    public static final String CLUSTER_EVENTS_TOPIC = "cluster-events";

    // Hazelcast Map Names
    public static final String PROCESSED_LINES_MAP = "processed-lines";
    public static final String CLUSTER_METRICS_MAP = "cluster-metrics";
    public static final String NODE_STATUS_MAP = "node-status";
    public static final String CLUSTER_NODES_MAP = "cluster-nodes";

    // Timer Configurations (in milliseconds)
    public static final long ORDER_GENERATION_INTERVAL = 2000L;  // 2 seconds
    public static final long FILE_GENERATION_INTERVAL = 10000L;  // 10 seconds
    public static final long HEALTH_CHECK_INTERVAL = 15000L;     // 15 seconds
    public static final long DAILY_REPORT_INTERVAL = 30000L;     // 30 seconds (for testing)
    public static final long CLEANUP_INTERVAL = 60000L;          // 1 minute

    // Node Types
    public static final String NODE_TYPE_PRODUCER = "producer";
    public static final String NODE_TYPE_CONSUMER = "consumer";
    public static final String NODE_TYPE_COORDINATOR = "coordinator";

    // Order Status
    public static final String ORDER_STATUS_PENDING = "PENDING";
    public static final String ORDER_STATUS_PROCESSING = "PROCESSING";
    public static final String ORDER_STATUS_COMPLETED = "COMPLETED";
    public static final String ORDER_STATUS_FAILED = "FAILED";

    // File Processing
    public static final String SHARED_INPUT_DIRECTORY = "shared/input";
    public static final String SHARED_OUTPUT_DIRECTORY = "shared/output";
    public static final String SHARED_ARCHIVE_DIRECTORY = "shared/archive";

    // Route IDs
    public static final String ROUTE_ORDER_GENERATOR = "order-generator";
    public static final String ROUTE_FILE_GENERATOR = "file-generator";
    public static final String ROUTE_ORDER_PROCESSOR = "order-processor";
    public static final String ROUTE_FILE_PROCESSOR = "file-processor";
    public static final String ROUTE_LINE_PROCESSOR = "line-processor";
    public static final String ROUTE_DAILY_REPORT_MASTER = "daily-report-master";
    public static final String ROUTE_HEALTH_CHECK_MASTER = "cluster-health-master";
    public static final String ROUTE_CLEANUP_MASTER = "cleanup-master";

    // Consumer Route IDs
    public static final String CONSUMER_ORDER_ROUTE_ID = "consumer-order-processor";
    public static final String CONSUMER_FILE_ROUTE_ID = "consumer-file-processor";
    public static final String CONSUMER_HEALTH_ROUTE_ID = "consumer-health-check";
    public static final String CONSUMER_CLUSTER_EVENTS_ROUTE_ID = "consumer-cluster-events";

    // Coordinator Route IDs
    public static final String COORDINATOR_HEALTH_ROUTE_ID = "coordinator-health-master";
    public static final String COORDINATOR_CLEANUP_ROUTE_ID = "coordinator-cleanup-master";
    public static final String COORDINATOR_REPORTING_ROUTE_ID = "coordinator-reporting-master";

    // Cluster Configuration
    public static final String CLUSTER_NAME = "camel-cluster";
    public static final int DEFAULT_HAZELCAST_PORT = 5701;
    public static final int CLUSTER_STARTUP_TIMEOUT_SECONDS = 30;

    // Processing Configuration
    public static final int MAX_CONCURRENT_CONSUMERS = 3;
    public static final int ORDER_PROCESSING_DELAY_MS = 1000;
    public static final int FILE_CLEANUP_AGE_MS = 300000; // 5 minutes for testing

    // Sample Data
    public static final String[] SAMPLE_PRODUCTS = {
        "Laptop", "Mouse", "Keyboard", "Monitor", "Headphones", 
        "Tablet", "Phone", "Camera", "Speaker", "Printer"
    };

    public static final String[] SAMPLE_CUSTOMERS = {
        "John Doe", "Jane Smith", "Bob Johnson", "Alice Brown", "Charlie Wilson",
        "Diana Davis", "Eve Miller", "Frank Garcia", "Grace Lee", "Henry Martinez"
    };

    // Logging Prefixes
    public static final String LOG_PREFIX_PRODUCER = "PRODUCER";
    public static final String LOG_PREFIX_CONSUMER = "CONSUMER";
    public static final String LOG_PREFIX_COORDINATOR = "COORDINATOR";
    public static final String LOG_PREFIX_MASTER = "MASTER";
} 