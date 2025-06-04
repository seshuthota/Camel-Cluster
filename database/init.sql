-- Database initialization for Camel Cluster
CREATE SCHEMA IF NOT EXISTS camel_cluster;

-- Enhanced Orders table for JPA entities with proper columns
CREATE TABLE IF NOT EXISTS processed_orders (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL UNIQUE,
    customer_name VARCHAR(200) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    order_total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    processed_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'COMPLETED'
);

-- Cluster state tracking
CREATE TABLE IF NOT EXISTS cluster_state (
    id BIGSERIAL PRIMARY KEY,
    node_id VARCHAR(100) NOT NULL,
    node_type VARCHAR(50) NOT NULL,
    is_master BOOLEAN DEFAULT FALSE,
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- File processing log
CREATE TABLE IF NOT EXISTS processed_files (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT,
    processed_by VARCHAR(100),
    lines_processed INTEGER DEFAULT 0,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Performance metrics
CREATE TABLE IF NOT EXISTS processing_metrics (
    id BIGSERIAL PRIMARY KEY,
    node_id VARCHAR(100) NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    metric_value DECIMAL(10,2),
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Cluster reports table for coordinator generated reports
CREATE TABLE IF NOT EXISTS cluster_reports (
    id BIGSERIAL PRIMARY KEY,
    report_id VARCHAR(100) NOT NULL UNIQUE,
    report_type VARCHAR(50) NOT NULL,
    report_data JSONB NOT NULL,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    generated_by VARCHAR(100) NOT NULL
);

-- Cluster analytics table for detailed analytics
CREATE TABLE IF NOT EXISTS cluster_analytics (
    id BIGSERIAL PRIMARY KEY,
    analytics_id VARCHAR(100) NOT NULL UNIQUE,
    analytics_data JSONB NOT NULL,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    generated_by VARCHAR(100) NOT NULL
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_processed_orders_order_id ON processed_orders(order_id);
CREATE INDEX IF NOT EXISTS idx_processed_orders_processed_at ON processed_orders(processed_at);
CREATE INDEX IF NOT EXISTS idx_processed_orders_customer ON processed_orders(customer_name);
CREATE INDEX IF NOT EXISTS idx_processed_orders_product ON processed_orders(product_name);
CREATE INDEX IF NOT EXISTS idx_processed_orders_processed_by ON processed_orders(processed_by);
CREATE INDEX IF NOT EXISTS idx_cluster_state_node_id ON cluster_state(node_id);
CREATE INDEX IF NOT EXISTS idx_cluster_state_is_master ON cluster_state(is_master);
CREATE INDEX IF NOT EXISTS idx_processed_files_processed_at ON processed_files(processed_at);
CREATE INDEX IF NOT EXISTS idx_processing_metrics_node_id ON processing_metrics(node_id);
CREATE INDEX IF NOT EXISTS idx_cluster_reports_generated_at ON cluster_reports(generated_at);
CREATE INDEX IF NOT EXISTS idx_cluster_reports_type ON cluster_reports(report_type);
CREATE INDEX IF NOT EXISTS idx_cluster_analytics_generated_at ON cluster_analytics(generated_at);

-- Insert initial cluster configuration
INSERT INTO cluster_state (node_id, node_type, is_master) VALUES 
('coordinator1', 'coordinator', TRUE),
('producer1', 'producer', FALSE),
('consumer1', 'consumer', FALSE),
('consumer2', 'consumer', FALSE)
ON CONFLICT DO NOTHING;

-- Sample processed orders data for testing
INSERT INTO processed_orders (order_id, customer_name, product_name, quantity, unit_price, order_total, processed_by) VALUES
('ORDER-001', 'John Smith', 'Laptop Pro', 1, 1299.99, 1299.99, 'consumer1'),
('ORDER-002', 'Jane Doe', 'Wireless Mouse', 2, 29.99, 59.98, 'consumer1'),
('ORDER-003', 'Bob Johnson', 'Mechanical Keyboard', 1, 149.99, 149.99, 'consumer2'),
('ORDER-004', 'Alice Brown', 'Monitor 4K', 1, 399.99, 399.99, 'consumer2'),
('ORDER-005', 'Charlie Wilson', 'USB Cable', 5, 9.99, 49.95, 'consumer1')
ON CONFLICT (order_id) DO NOTHING;

-- Create a view for cluster status
CREATE OR REPLACE VIEW cluster_status_view AS
SELECT 
    node_id,
    node_type,
    is_master,
    last_heartbeat,
    CASE 
        WHEN last_heartbeat > NOW() - INTERVAL '5 minutes' THEN 'ACTIVE'
        ELSE 'INACTIVE'
    END as status
FROM cluster_state
ORDER BY node_type, node_id;

-- Create a view for order analytics
CREATE OR REPLACE VIEW order_analytics_view AS
SELECT 
    DATE(processed_at) as order_date,
    COUNT(*) as daily_orders,
    SUM(order_total) as daily_revenue,
    AVG(order_total) as avg_order_value,
    processed_by
FROM processed_orders
WHERE processed_at >= NOW() - INTERVAL '30 days'
GROUP BY DATE(processed_at), processed_by
ORDER BY order_date DESC; 