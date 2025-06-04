#!/bin/bash

# Apache Camel Cluster Testing Script
# Tests all applications and cluster functionality

set -e

echo "🧪 Starting Apache Camel Cluster Testing..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_status() {
    echo -e "${BLUE}[TEST]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[FAIL]${NC} $1"
}

# Test configuration
PRODUCER_URL="http://localhost:8081"
CONSUMER_URL="http://localhost:8082"
COORDINATOR_URL="http://localhost:8083"
NGINX_URL="http://localhost:80"

# Function to test HTTP endpoint
test_endpoint() {
    local url=$1
    local description=$2
    local expected_status=${3:-200}
    
    print_status "Testing $description: $url"
    
    response=$(curl -s -w "%{http_code}" -o /tmp/response.json "$url" 2>/dev/null || echo "000")
    
    if [ "$response" = "$expected_status" ]; then
        print_success "$description - HTTP $response"
        return 0
    else
        print_error "$description - HTTP $response (expected $expected_status)"
        return 1
    fi
}

# Function to wait for service
wait_for_service() {
    local url=$1
    local service_name=$2
    local max_attempts=30
    local attempt=1
    
    print_status "Waiting for $service_name to be ready..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s -f "$url" > /dev/null 2>&1; then
            print_success "$service_name is ready"
            return 0
        fi
        
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    print_error "$service_name failed to start within $((max_attempts * 2)) seconds"
    return 1
}

# Check if Docker Compose is running
print_status "Checking Docker Compose status..."
if ! docker-compose ps | grep -q "Up"; then
    print_error "Docker Compose services are not running. Please run 'docker-compose up -d' first."
    exit 1
fi

print_success "Docker Compose services are running"

# Wait for all services to be ready
wait_for_service "$PRODUCER_URL/api/producer/health" "Producer"
wait_for_service "$CONSUMER_URL/api/consumer/health" "Consumer"
wait_for_service "$COORDINATOR_URL/api/coordinator/health" "Coordinator"

echo ""
print_status "🔍 Running comprehensive cluster tests..."
echo ""

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0

# Producer Tests
echo "📤 PRODUCER TESTS"
echo "=================="

test_endpoint "$PRODUCER_URL/api/producer/status" "Producer Status" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$PRODUCER_URL/api/producer/health" "Producer Health" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$PRODUCER_URL/api/producer/cluster" "Producer Cluster Info" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$PRODUCER_URL/api/producer/metrics" "Producer Metrics" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$PRODUCER_URL/api/producer/routes" "Producer Routes" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test manual order generation
print_status "Testing manual order generation..."
response=$(curl -s -w "%{http_code}" -X POST "$PRODUCER_URL/api/producer/generate" -o /tmp/response.json 2>/dev/null || echo "000")
if [ "$response" = "200" ]; then
    print_success "Manual order generation - HTTP $response"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    print_error "Manual order generation - HTTP $response"
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

echo ""

# Consumer Tests
echo "📥 CONSUMER TESTS"
echo "================="

test_endpoint "$CONSUMER_URL/api/consumer/status" "Consumer Status" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$CONSUMER_URL/api/consumer/health" "Consumer Health" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$CONSUMER_URL/api/consumer/cluster" "Consumer Cluster Info" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$CONSUMER_URL/api/consumer/metrics" "Consumer Metrics" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$CONSUMER_URL/api/consumer/routes" "Consumer Routes" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$CONSUMER_URL/api/consumer/orders" "Consumer Orders" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$CONSUMER_URL/api/consumer/orders/stats" "Consumer Order Stats" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

echo ""

# Coordinator Tests
echo "🎯 COORDINATOR TESTS"
echo "===================="

test_endpoint "$COORDINATOR_URL/api/coordinator/status" "Coordinator Status" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$COORDINATOR_URL/api/coordinator/health" "Coordinator Health" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$COORDINATOR_URL/api/coordinator/cluster" "Coordinator Cluster Info" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$COORDINATOR_URL/api/coordinator/cluster/health" "Coordinator Cluster Health" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$COORDINATOR_URL/api/coordinator/reports" "Coordinator Reports" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

test_endpoint "$COORDINATOR_URL/api/coordinator/analytics" "Coordinator Analytics" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

echo ""

# Load Balancer Tests
echo "⚖️ LOAD BALANCER TESTS"
echo "======================"

test_endpoint "$NGINX_URL/api/consumer/health" "Nginx Load Balancer" && PASSED_TESTS=$((PASSED_TESTS + 1))
TOTAL_TESTS=$((TOTAL_TESTS + 1))

echo ""

# Cluster Integration Tests
echo "🔗 CLUSTER INTEGRATION TESTS"
echo "============================="

print_status "Testing cluster coordination..."

# Generate some orders and wait
print_status "Generating test orders..."
for i in {1..5}; do
    curl -s -X POST "$PRODUCER_URL/api/producer/generate" > /dev/null 2>&1 || true
    sleep 1
done

# Wait for processing
print_status "Waiting for order processing..."
sleep 10

# Check if orders were processed
print_status "Checking processed orders..."
response=$(curl -s "$CONSUMER_URL/api/consumer/orders" 2>/dev/null || echo "{}")
if echo "$response" | grep -q "orderId"; then
    print_success "Orders are being processed by consumers"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    print_warning "No processed orders found (may need more time)"
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test cluster health reporting
print_status "Testing cluster health reporting..."
response=$(curl -s "$COORDINATOR_URL/api/coordinator/cluster/health" 2>/dev/null || echo "{}")
if echo "$response" | grep -q "clusterSize"; then
    print_success "Cluster health reporting is working"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    print_error "Cluster health reporting failed"
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

echo ""

# Summary
echo "📊 TEST SUMMARY"
echo "==============="
echo "Total Tests: $TOTAL_TESTS"
echo "Passed: $PASSED_TESTS"
echo "Failed: $((TOTAL_TESTS - PASSED_TESTS))"

if [ $PASSED_TESTS -eq $TOTAL_TESTS ]; then
    print_success "🎉 All tests passed! Cluster is working correctly."
    exit 0
else
    print_warning "⚠️ Some tests failed. Check the logs for details."
    echo ""
    print_status "Useful commands for debugging:"
    echo "  docker-compose logs producer"
    echo "  docker-compose logs consumer"
    echo "  docker-compose logs coordinator"
    echo "  docker-compose ps"
    exit 1
fi 