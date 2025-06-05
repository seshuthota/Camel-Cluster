#!/bin/bash

# Apache Camel Cluster Failover Testing Script
# Tests cluster behavior when consumers are shut down

set -e

echo "🧪 Starting Apache Camel Cluster Failover Testing..."
echo "===================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

print_status() {
    echo -e "${BLUE}[INFO]${NC} $(date '+%H:%M:%S') $1"
}

print_success() {
    echo -e "${GREEN}[PASS]${NC} $(date '+%H:%M:%S') $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $(date '+%H:%M:%S') $1"
}

print_error() {
    echo -e "${RED}[FAIL]${NC} $(date '+%H:%M:%S') $1"
}

print_log() {
    echo -e "${PURPLE}[LOG]${NC} $(date '+%H:%M:%S') $1"
}

print_test() {
    echo -e "${CYAN}[TEST]${NC} $(date '+%H:%M:%S') $1"
}

# Configuration
PRODUCER_URL="http://localhost:8081"
CONSUMER1_URL="http://localhost:8082"
CONSUMER2_URL="http://localhost:8085"
COORDINATOR1_URL="http://localhost:8083"
COORDINATOR2_URL="http://localhost:8086"
NGINX_URL="http://localhost:8090"

LOG_DIR="./logs/failover-test-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$LOG_DIR"

print_status "Log directory created: $LOG_DIR"

# Function to capture service logs
capture_logs() {
    local service_name=$1
    print_log "Starting log capture for $service_name"
    docker logs -f "$service_name" > "$LOG_DIR/$service_name.log" 2>&1 &
    echo $! > "$LOG_DIR/$service_name.pid"
}

# Function to stop log capture
stop_log_capture() {
    local service_name=$1
    if [ -f "$LOG_DIR/$service_name.pid" ]; then
        local pid=$(cat "$LOG_DIR/$service_name.pid")
        kill $pid 2>/dev/null || true
        rm "$LOG_DIR/$service_name.pid"
        print_log "Stopped log capture for $service_name"
    fi
}

# Function to get cluster info
get_cluster_info() {
    local service_url=$1
    local service_name=$2
    
    print_test "Getting cluster info from $service_name"
    
    local response=$(curl -s "$service_url/api/${service_name,,}/cluster" 2>/dev/null || echo "{}")
    echo "$response" | jq . > "$LOG_DIR/cluster-info-$service_name-$(date +%H%M%S).json" 2>/dev/null || echo "$response" > "$LOG_DIR/cluster-info-$service_name-$(date +%H%M%S).json"
    
    if echo "$response" | grep -q "nodeId"; then
        print_success "$service_name cluster info retrieved"
        echo "$response" | jq '.nodeId, .clusterSize, .members' 2>/dev/null || echo "$response"
    else
        print_warning "$service_name cluster info unavailable"
    fi
}

# Function to monitor queue metrics
monitor_queue_metrics() {
    print_test "Monitoring ActiveMQ queue metrics"
    
    # Get ActiveMQ console info (if accessible)
    curl -s -u admin:admin "http://localhost:8161/console/jolokia/read/org.apache.activemq.artemis:broker=\"0.0.0.0\"/QueueNames" 2>/dev/null > "$LOG_DIR/activemq-queues-$(date +%H%M%S).json" || print_warning "ActiveMQ metrics unavailable"
}

# Function to wait for service
wait_for_service() {
    local url=$1
    local service_name=$2
    local max_attempts=60
    local attempt=1
    
    print_status "Waiting for $service_name to be ready..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s -f "$url" > /dev/null 2>&1; then
            print_success "$service_name is ready (attempt $attempt)"
            return 0
        fi
        
        if [ $((attempt % 5)) -eq 0 ]; then
            print_status "$service_name not ready yet (attempt $attempt/$max_attempts)"
        fi
        sleep 2
        attempt=$((attempt + 1))
    done
    
    print_error "$service_name failed to start within $((max_attempts * 2)) seconds"
    return 1
}

# Function to generate test load
generate_test_load() {
    local count=$1
    local delay=$2
    
    print_test "Generating $count test orders with ${delay}s delay between each"
    
    for i in $(seq 1 $count); do
        local response=$(curl -s -w "%{http_code}" -X POST "$PRODUCER_URL/api/producer/generate" -o /tmp/order_response.json 2>/dev/null || echo "000")
        
        if [ "$response" = "200" ]; then
            print_success "Order $i generated successfully"
        else
            print_warning "Order $i generation failed (HTTP $response)"
        fi
        
        sleep $delay
    done
}

# Function to check consumer processing
check_consumer_processing() {
    local consumer_url=$1
    local consumer_name=$2
    
    print_test "Checking $consumer_name processing status"
    
    local response=$(curl -s "$consumer_url/api/consumer/orders/stats" 2>/dev/null || echo "{}")
    echo "$response" > "$LOG_DIR/$consumer_name-stats-$(date +%H%M%S).json"
    
    if echo "$response" | grep -q "totalProcessed"; then
        local processed=$(echo "$response" | jq '.totalProcessed' 2>/dev/null || echo "0")
        print_success "$consumer_name has processed $processed orders"
    else
        print_warning "$consumer_name processing stats unavailable"
    fi
}

# Function to perform comprehensive monitoring
comprehensive_monitoring() {
    print_test "=== COMPREHENSIVE CLUSTER MONITORING ==="
    
    # Capture cluster state from all services
    get_cluster_info "$PRODUCER_URL" "producer"
    get_cluster_info "$CONSUMER1_URL" "consumer"
    get_cluster_info "$CONSUMER2_URL" "consumer" 
    get_cluster_info "$COORDINATOR1_URL" "coordinator"
    get_cluster_info "$COORDINATOR2_URL" "coordinator"
    
    # Monitor queue metrics
    monitor_queue_metrics
    
    # Check processing stats
    check_consumer_processing "$CONSUMER1_URL" "consumer1"
    check_consumer_processing "$CONSUMER2_URL" "consumer2"
    
    # Get coordinator reports
    print_test "Getting coordinator reports"
    curl -s "$COORDINATOR1_URL/api/coordinator/reports" 2>/dev/null > "$LOG_DIR/coordinator1-reports-$(date +%H%M%S).json" || print_warning "Coordinator1 reports unavailable"
    curl -s "$COORDINATOR2_URL/api/coordinator/reports" 2>/dev/null > "$LOG_DIR/coordinator2-reports-$(date +%H%M%S).json" || print_warning "Coordinator2 reports unavailable"
    
    print_test "=== END MONITORING ==="
}

# Cleanup function
cleanup() {
    print_status "Cleaning up..."
    
    # Stop all log captures
    for service in producer consumer1 consumer2 coordinator1 coordinator2 activemq postgres nginx-lb; do
        stop_log_capture "$service"
    done
    
    print_status "Cleanup completed. Logs saved in: $LOG_DIR"
}

# Set trap for cleanup
trap cleanup EXIT

print_status "Starting cluster failover test..."
echo ""

# Step 1: Clean start
print_test "STEP 1: Clean Environment Setup"
print_status "Stopping any existing containers..."
docker-compose down 2>/dev/null || true

print_status "Starting all services..."
docker-compose up -d

echo ""

# Step 2: Start log capture for all services
print_test "STEP 2: Starting Log Capture"
for service in producer consumer1 consumer2 coordinator1 coordinator2 activemq postgres nginx-lb; do
    capture_logs "$service"
done

echo ""

# Step 3: Wait for services to be ready
print_test "STEP 3: Waiting for Services to Start"
wait_for_service "$PRODUCER_URL/api/producer/health" "Producer"
wait_for_service "$CONSUMER1_URL/api/consumer/health" "Consumer1"
wait_for_service "$CONSUMER2_URL/api/consumer/health" "Consumer2"
wait_for_service "$COORDINATOR1_URL/api/coordinator/health" "Coordinator1"
wait_for_service "$COORDINATOR2_URL/api/coordinator/health" "Coordinator2"

echo ""

# Step 4: Initial cluster state monitoring
print_test "STEP 4: Initial Cluster State"
comprehensive_monitoring

echo ""

# Step 5: Generate initial load
print_test "STEP 5: Initial Load Generation"
generate_test_load 10 1

print_status "Waiting 30 seconds for initial processing..."
sleep 30

echo ""

# Step 6: Monitor pre-shutdown state
print_test "STEP 6: Pre-Shutdown Monitoring"
comprehensive_monitoring

echo ""

# Step 7: The main test - shutdown consumer1
print_test "STEP 7: CONSUMER SHUTDOWN TEST"
print_warning "🚨 SHUTTING DOWN CONSUMER1 (Simulating Node Failure)"

docker stop consumer1
print_error "Consumer1 stopped at $(date)"

# Immediately start monitoring the failover
print_test "Monitoring immediate cluster response..."
sleep 5

comprehensive_monitoring

echo ""

# Step 8: Generate load during failover
print_test "STEP 8: Load Generation During Failover"
print_status "Generating orders while one consumer is down..."
generate_test_load 15 2

echo ""

# Step 9: Monitor during failover
print_test "STEP 9: During-Failover Monitoring"
comprehensive_monitoring

print_status "Waiting 60 seconds for rebalancing and processing..."
sleep 60

echo ""

# Step 10: Extended monitoring
print_test "STEP 10: Extended Failover Monitoring"
comprehensive_monitoring

echo ""

# Step 11: Restart consumer1
print_test "STEP 11: CONSUMER RECOVERY TEST"
print_success "🔄 RESTARTING CONSUMER1 (Simulating Node Recovery)"

docker start consumer1
print_success "Consumer1 restarted at $(date)"

# Wait for it to rejoin
print_status "Waiting for Consumer1 to rejoin cluster..."
wait_for_service "$CONSUMER1_URL/api/consumer/health" "Consumer1 (recovered)"

echo ""

# Step 12: Post-recovery monitoring
print_test "STEP 12: Post-Recovery Monitoring"
comprehensive_monitoring

print_status "Waiting 30 seconds for cluster rebalancing..."
sleep 30

echo ""

# Step 13: Final load test
print_test "STEP 13: Final Load Test"
print_status "Testing with fully recovered cluster..."
generate_test_load 20 1

print_status "Waiting 45 seconds for final processing..."
sleep 45

echo ""

# Step 14: Final comprehensive monitoring
print_test "STEP 14: Final Cluster State"
comprehensive_monitoring

echo ""

# Summary
print_test "🎯 FAILOVER TEST COMPLETED"
echo "==========================================="
print_success "✅ Consumer shutdown simulation completed"
print_success "✅ Consumer recovery simulation completed"  
print_success "✅ Load generation during failover completed"
print_success "✅ Comprehensive monitoring completed"
print_success "✅ All logs captured in: $LOG_DIR"

echo ""
print_status "🔍 ANALYSIS SUMMARY:"
echo "-------------------"
print_status "1. Check the log files in $LOG_DIR for detailed analysis"
print_status "2. Look for cluster rebalancing events in coordinator logs"
print_status "3. Monitor message redistribution in consumer logs"
print_status "4. Verify no message loss occurred during failover"

echo ""
print_status "📊 Key Files to Analyze:"
echo "- $LOG_DIR/consumer1.log (shutdown/recovery logs)"
echo "- $LOG_DIR/consumer2.log (increased load handling)"
echo "- $LOG_DIR/coordinator*.log (cluster management)"
echo "- $LOG_DIR/*stats*.json (processing statistics)"
echo "- $LOG_DIR/cluster-info*.json (cluster state changes)"

echo ""
print_success "🎉 Failover test completed successfully!"

# Keep logs accessible
print_status "Log files will remain available in: $LOG_DIR"
print_status "Use 'tail -f $LOG_DIR/*.log' to continue monitoring if needed" 