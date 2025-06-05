#!/bin/bash

# Simplified Apache Camel Cluster Failover Testing Script
# Tests cluster behavior when consumers are shut down

set -e

echo "🧪 Starting Simplified Apache Camel Cluster Failover Testing..."
echo "=============================================================="

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

print_test() {
    echo -e "${CYAN}[TEST]${NC} $(date '+%H:%M:%S') $1"
}

# Configuration
PRODUCER_URL="http://localhost:8081"
CONSUMER1_URL="http://localhost:8082"
CONSUMER2_URL="http://localhost:8085"

LOG_DIR="./logs/simple-failover-test-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$LOG_DIR"

print_status "Log directory created: $LOG_DIR"

# Function to capture service logs
capture_logs() {
    local service_name=$1
    print_status "Starting log capture for $service_name"
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
    fi
}

# Function to wait for container to be running
wait_for_container() {
    local container_name=$1
    local max_attempts=30
    local attempt=1
    
    print_status "Waiting for $container_name container to be running..."
    
    while [ $attempt -le $max_attempts ]; do
        if docker ps | grep -q "$container_name.*Up"; then
            print_success "$container_name container is running"
            return 0
        fi
        
        if [ $((attempt % 5)) -eq 0 ]; then
            print_status "$container_name not running yet (attempt $attempt/$max_attempts)"
        fi
        sleep 2
        attempt=$((attempt + 1))
    done
    
    print_error "$container_name failed to start within $((max_attempts * 2)) seconds"
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

# Function to check service status
check_service_status() {
    local service_url=$1
    local service_name=$2
    
    print_test "Checking $service_name status"
    
    local response=$(curl -s -w "%{http_code}" "$service_url/api/${service_name,,}/status" -o /tmp/status_response.json 2>/dev/null || echo "000")
    
    if [ "$response" = "200" ]; then
        print_success "$service_name is responding (HTTP $response)"
        cat /tmp/status_response.json | jq . 2>/dev/null || cat /tmp/status_response.json
    else
        print_warning "$service_name status check failed (HTTP $response)"
    fi
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

print_status "Starting simplified cluster failover test..."
echo ""

# Step 1: Check current container status
print_test "STEP 1: Current Container Status"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""

# Step 2: Start log capture for all services
print_test "STEP 2: Starting Log Capture"
for service in producer consumer1 consumer2 coordinator1 coordinator2 activemq postgres nginx-lb; do
    if docker ps | grep -q "$service"; then
        capture_logs "$service"
    else
        print_warning "Container $service not found"
    fi
done

echo ""

# Step 3: Wait for key services to be running
print_test "STEP 3: Waiting for Key Services"
wait_for_container "producer"
wait_for_container "consumer1"
wait_for_container "consumer2"

echo ""

# Step 4: Check service status
print_test "STEP 4: Service Status Check"
check_service_status "$PRODUCER_URL" "producer"
check_service_status "$CONSUMER1_URL" "consumer"
check_service_status "$CONSUMER2_URL" "consumer"

echo ""

# Step 5: Generate initial load
print_test "STEP 5: Initial Load Generation"
generate_test_load 5 2

print_status "Waiting 20 seconds for initial processing..."
sleep 20

echo ""

# Step 6: Monitor processing before failover
print_test "STEP 6: Pre-Failover Monitoring"
print_status "Checking consumer processing stats..."

# Check consumer1 stats
consumer1_response=$(curl -s "$CONSUMER1_URL/api/consumer/orders/stats" 2>/dev/null || echo "{}")
echo "Consumer1 stats: $consumer1_response" | tee "$LOG_DIR/consumer1-pre-failover-stats.json"

# Check consumer2 stats
consumer2_response=$(curl -s "$CONSUMER2_URL/api/consumer/orders/stats" 2>/dev/null || echo "{}")
echo "Consumer2 stats: $consumer2_response" | tee "$LOG_DIR/consumer2-pre-failover-stats.json"

echo ""

# Step 7: The main test - shutdown consumer1
print_test "STEP 7: CONSUMER FAILOVER TEST"
print_warning "🚨 SHUTTING DOWN CONSUMER1 (Simulating Node Failure)"

docker stop consumer1
print_error "Consumer1 stopped at $(date)"

# Wait a moment for the cluster to detect the failure
print_status "Waiting 10 seconds for cluster to detect failure..."
sleep 10

echo ""

# Step 8: Generate load during failover
print_test "STEP 8: Load Generation During Failover"
print_status "Generating orders while consumer1 is down..."
generate_test_load 10 1

print_status "Waiting 30 seconds for processing..."
sleep 30

echo ""

# Step 9: Monitor during failover
print_test "STEP 9: During-Failover Monitoring"
print_status "Checking remaining consumer processing..."

# Check consumer2 stats (should handle all load now)
consumer2_failover_response=$(curl -s "$CONSUMER2_URL/api/consumer/orders/stats" 2>/dev/null || echo "{}")
echo "Consumer2 stats during failover: $consumer2_failover_response" | tee "$LOG_DIR/consumer2-during-failover-stats.json"

echo ""

# Step 10: Restart consumer1
print_test "STEP 10: CONSUMER RECOVERY TEST"
print_success "🔄 RESTARTING CONSUMER1 (Simulating Node Recovery)"

docker start consumer1
print_success "Consumer1 restarted at $(date)"

# Wait for it to come back up
print_status "Waiting 30 seconds for Consumer1 to recover..."
sleep 30

echo ""

# Step 11: Final load test
print_test "STEP 11: Final Load Test"
print_status "Testing with recovered cluster..."
generate_test_load 10 1

print_status "Waiting 30 seconds for final processing..."
sleep 30

echo ""

# Step 12: Final monitoring
print_test "STEP 12: Final Monitoring"
print_status "Checking final processing stats..."

# Final stats
consumer1_final_response=$(curl -s "$CONSUMER1_URL/api/consumer/orders/stats" 2>/dev/null || echo "{}")
echo "Consumer1 final stats: $consumer1_final_response" | tee "$LOG_DIR/consumer1-final-stats.json"

consumer2_final_response=$(curl -s "$CONSUMER2_URL/api/consumer/orders/stats" 2>/dev/null || echo "{}")
echo "Consumer2 final stats: $consumer2_final_response" | tee "$LOG_DIR/consumer2-final-stats.json"

echo ""

# Summary
print_test "🎯 SIMPLIFIED FAILOVER TEST COMPLETED"
echo "============================================="
print_success "✅ Consumer shutdown simulation completed"
print_success "✅ Consumer recovery simulation completed"  
print_success "✅ Load generation during failover completed"
print_success "✅ All logs captured in: $LOG_DIR"

echo ""
print_status "📊 Key Files to Analyze:"
echo "- $LOG_DIR/consumer1.log (shutdown/recovery logs)"
echo "- $LOG_DIR/consumer2.log (increased load handling)"
echo "- $LOG_DIR/*stats*.json (processing statistics)"

echo ""
print_success "🎉 Simplified failover test completed successfully!"

# Keep logs accessible
print_status "Log files will remain available in: $LOG_DIR" 