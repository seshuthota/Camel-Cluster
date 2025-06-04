#!/bin/bash

# Dynamic Cluster Scaling Script
# Demonstrates service discovery and dynamic member management

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
COMPOSE_FILE="${1:-docker-compose-consul.yml}"
PROJECT_NAME="camel-cluster"

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check cluster status
check_cluster_status() {
    log_info "Checking cluster status..."
    
    # Check Consul
    if curl -sf http://localhost:8500/v1/status/leader > /dev/null 2>&1; then
        log_success "Consul is running and healthy"
    else
        log_error "Consul is not accessible"
        return 1
    fi
    
    # Check registered services
    services=$(curl -s http://localhost:8500/v1/agent/services | jq -r 'keys[]' 2>/dev/null || echo "")
    if [ -n "$services" ]; then
        log_info "Registered services in Consul:"
        echo "$services" | sed 's/^/  - /'
    else
        log_warning "No services registered in Consul yet"
    fi
}

# Scale consumer services
scale_consumers() {
    local replicas=$1
    log_info "Scaling consumers to $replicas replicas..."
    
    docker-compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d --scale consumer=$replicas
    
    # Wait for services to register
    sleep 10
    
    log_success "Consumer scaling completed"
    check_cluster_status
}

# Scale coordinator services
scale_coordinators() {
    local replicas=$1
    log_info "Scaling coordinators to $replicas replicas..."
    
    docker-compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d --scale coordinator=$replicas
    
    # Wait for services to register
    sleep 10
    
    log_success "Coordinator scaling completed"
    check_cluster_status
}

# Start cluster with service discovery
start_cluster() {
    log_info "Starting cluster with dynamic service discovery..."
    
    # Start infrastructure first
    docker-compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d consul postgres activemq
    
    # Wait for infrastructure
    log_info "Waiting for infrastructure to be ready..."
    sleep 20
    
    # Start application services
    docker-compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d
    
    # Wait for services to register
    log_info "Waiting for services to register with Consul..."
    sleep 30
    
    log_success "Cluster started successfully"
    check_cluster_status
}

# Stop cluster
stop_cluster() {
    log_info "Stopping cluster..."
    docker-compose -f $COMPOSE_FILE -p $PROJECT_NAME down
    log_success "Cluster stopped"
}

# Simulate traffic to test scaling
simulate_traffic() {
    log_info "Simulating traffic to test scaling..."
    
    # Generate orders through producer
    for i in {1..20}; do
        curl -sf -X POST http://localhost:8081/api/producer/orders \
             -H "Content-Type: application/json" \
             -d '{"productId":"PROD-'$i'","quantity":'$((RANDOM % 10 + 1))',"price":'$((RANDOM % 100 + 10))'}' \
             > /dev/null 2>&1 && echo -n "." || echo -n "x"
        sleep 0.5
    done
    echo
    
    log_success "Traffic simulation completed"
}

# Monitor cluster health
monitor_cluster() {
    log_info "Monitoring cluster health..."
    
    while true; do
        clear
        echo "=== Camel Cluster Status ==="
        echo "Timestamp: $(date)"
        echo
        
        # Consul services
        echo "--- Consul Services ---"
        curl -s http://localhost:8500/v1/agent/services | jq -r 'to_entries[] | "\(.key): \(.value.Address):\(.value.Port)"' 2>/dev/null || echo "Consul not accessible"
        echo
        
        # Docker services
        echo "--- Docker Services ---"
        docker-compose -f $COMPOSE_FILE -p $PROJECT_NAME ps --format "table {{.Service}}\t{{.State}}\t{{.Ports}}"
        echo
        
        # Cluster metrics
        echo "--- Cluster Metrics ---"
        coordinator_url=$(docker-compose -f $COMPOSE_FILE -p $PROJECT_NAME port coordinator 8083 2>/dev/null | head -1)
        if [ -n "$coordinator_url" ]; then
            curl -s "http://$coordinator_url/api/coordinator/cluster" | jq -r '.clusterSize, .nodeStatuses | keys | length' 2>/dev/null || echo "Coordinator not accessible"
        fi
        
        echo
        echo "Press Ctrl+C to stop monitoring..."
        sleep 10
    done
}

# Auto-scaling demonstration
demo_autoscaling() {
    log_info "Demonstrating auto-scaling capabilities..."
    
    # Start with minimal setup
    log_info "Step 1: Starting with minimal cluster (1 of each)"
    start_cluster
    
    # Scale up consumers
    log_info "Step 2: Scaling up consumers for high load"
    scale_consumers 4
    
    # Generate traffic
    log_info "Step 3: Generating traffic to test load balancing"
    simulate_traffic
    
    # Scale up coordinators
    log_info "Step 4: Adding coordinator for high availability"
    scale_coordinators 3
    
    # Scale down
    log_info "Step 5: Scaling down during low traffic"
    scale_consumers 2
    scale_coordinators 2
    
    log_success "Auto-scaling demonstration completed"
    check_cluster_status
}

# Show help
show_help() {
    echo "Dynamic Camel Cluster Management Script"
    echo
    echo "Usage: $0 [COMMAND] [OPTIONS]"
    echo
    echo "Commands:"
    echo "  start              Start cluster with service discovery"
    echo "  stop               Stop the entire cluster"
    echo "  status             Check cluster status"
    echo "  scale-consumers N  Scale consumer services to N replicas"
    echo "  scale-coords N     Scale coordinator services to N replicas"
    echo "  traffic            Simulate traffic for testing"
    echo "  monitor            Monitor cluster in real-time"
    echo "  demo               Run auto-scaling demonstration"
    echo "  help               Show this help message"
    echo
    echo "Examples:"
    echo "  $0 start                    # Start cluster"
    echo "  $0 scale-consumers 5        # Scale to 5 consumer instances"
    echo "  $0 scale-coords 3           # Scale to 3 coordinator instances"
    echo "  $0 demo                     # Run full auto-scaling demo"
    echo
    echo "Environment Variables:"
    echo "  COMPOSE_FILE               Docker compose file to use (default: docker-compose-consul.yml)"
}

# Main command handling
case "${1:-help}" in
    start)
        start_cluster
        ;;
    stop)
        stop_cluster
        ;;
    status)
        check_cluster_status
        ;;
    scale-consumers)
        if [ -z "$2" ]; then
            log_error "Please specify number of replicas"
            exit 1
        fi
        scale_consumers $2
        ;;
    scale-coords)
        if [ -z "$2" ]; then
            log_error "Please specify number of replicas"
            exit 1
        fi
        scale_coordinators $2
        ;;
    traffic)
        simulate_traffic
        ;;
    monitor)
        monitor_cluster
        ;;
    demo)
        demo_autoscaling
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        log_error "Unknown command: $1"
        show_help
        exit 1
        ;;
esac 