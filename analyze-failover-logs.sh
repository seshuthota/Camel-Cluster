#!/bin/bash

# Camel Cluster Failover Log Analysis Script
# Analyzes logs from failover testing to extract key insights

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

print_header() {
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}============================================${NC}"
}

print_section() {
    echo -e "${CYAN}--- $1 ---${NC}"
}

print_finding() {
    echo -e "${GREEN}✓${NC} $1"
}

print_issue() {
    echo -e "${RED}✗${NC} $1"
}

print_info() {
    echo -e "${YELLOW}ℹ${NC} $1"
}

# Check if log directory is provided
if [ $# -eq 0 ]; then
    echo "Usage: $0 <log-directory>"
    echo "Example: $0 ./logs/failover-test-20231201-143022"
    exit 1
fi

LOG_DIR="$1"

if [ ! -d "$LOG_DIR" ]; then
    echo "Error: Log directory '$LOG_DIR' does not exist"
    exit 1
fi

print_header "CAMEL CLUSTER FAILOVER LOG ANALYSIS"
echo "Log Directory: $LOG_DIR"
echo "Analysis Time: $(date)"
echo ""

# Function to analyze cluster membership changes
analyze_cluster_membership() {
    print_section "CLUSTER MEMBERSHIP ANALYSIS"
    
    # Look for cluster join/leave events in coordinator logs
    if [ -f "$LOG_DIR/coordinator1.log" ]; then
        print_info "Analyzing cluster membership changes..."
        
        # Extract cluster join events
        grep -n "joined\|left\|Member.*added\|Member.*removed" "$LOG_DIR/coordinator1.log" 2>/dev/null | head -20 | while read line; do
            echo "  $line"
        done
        
        # Count cluster size changes
        local join_count=$(grep -c "joined\|Member.*added" "$LOG_DIR/coordinator1.log" 2>/dev/null || echo "0")
        local leave_count=$(grep -c "left\|Member.*removed" "$LOG_DIR/coordinator1.log" 2>/dev/null || echo "0")
        
        print_finding "Cluster join events: $join_count"
        print_finding "Cluster leave events: $leave_count"
    fi
    
    echo ""
}

# Function to analyze consumer shutdown and recovery
analyze_consumer_failover() {
    print_section "CONSUMER FAILOVER ANALYSIS"
    
    if [ -f "$LOG_DIR/consumer1.log" ]; then
        print_info "Analyzing Consumer1 shutdown/recovery..."
        
        # Look for shutdown indicators
        local shutdown_time=$(grep -n "Stopping\|Shutdown\|Closing" "$LOG_DIR/consumer1.log" 2>/dev/null | tail -1 | cut -d: -f1)
        if [ -n "$shutdown_time" ]; then
            print_finding "Consumer1 shutdown detected around line: $shutdown_time"
        fi
        
        # Look for recovery indicators
        local recovery_time=$(grep -n "Started\|Ready\|Initialized" "$LOG_DIR/consumer1.log" 2>/dev/null | tail -1 | cut -d: -f1)
        if [ -n "$recovery_time" ]; then
            print_finding "Consumer1 recovery detected around line: $recovery_time"
        fi
        
        # Check for message processing before/after
        local pre_shutdown_msgs=$(grep -c "Processing order\|Received message" "$LOG_DIR/consumer1.log" 2>/dev/null || echo "0")
        print_finding "Consumer1 processed messages: $pre_shutdown_msgs"
    fi
    
    if [ -f "$LOG_DIR/consumer2.log" ]; then
        print_info "Analyzing Consumer2 load handling..."
        
        # Look for increased load handling
        local msgs_processed=$(grep -c "Processing order\|Received message" "$LOG_DIR/consumer2.log" 2>/dev/null || echo "0")
        print_finding "Consumer2 processed messages: $msgs_processed"
        
        # Look for load balancing messages
        grep -n "rebalancing\|taking over\|increased load" "$LOG_DIR/consumer2.log" 2>/dev/null | head -10 | while read line; do
            echo "  $line"
        done
    fi
    
    echo ""
}

# Function to analyze message routing and queue behavior
analyze_message_routing() {
    print_section "MESSAGE ROUTING ANALYSIS"
    
    # Analyze ActiveMQ queue states
    if ls "$LOG_DIR"/activemq-queues-*.json >/dev/null 2>&1; then
        print_info "Analyzing ActiveMQ queue states..."
        
        for queue_file in "$LOG_DIR"/activemq-queues-*.json; do
            local timestamp=$(basename "$queue_file" .json | cut -d- -f3)
            print_info "Queue state at $timestamp:"
            
            if command -v jq >/dev/null 2>&1; then
                jq -r '.value[]?' "$queue_file" 2>/dev/null | head -5 | while read queue; do
                    echo "  - $queue"
                done
            else
                echo "  $(head -3 "$queue_file")"
            fi
        done
    fi
    
    # Analyze message distribution
    print_info "Message distribution analysis..."
    
    # Count messages per consumer from processing stats
    if ls "$LOG_DIR"/consumer*-stats-*.json >/dev/null 2>&1; then
        for stats_file in "$LOG_DIR"/consumer*-stats-*.json; do
            local consumer=$(basename "$stats_file" | cut -d- -f1)
            local timestamp=$(basename "$stats_file" .json | cut -d- -f3)
            
            if command -v jq >/dev/null 2>&1; then
                local processed=$(jq -r '.totalProcessed // 0' "$stats_file" 2>/dev/null)
                local failed=$(jq -r '.totalFailed // 0' "$stats_file" 2>/dev/null)
                print_finding "$consumer at $timestamp: Processed=$processed, Failed=$failed"
            else
                print_info "$consumer stats at $timestamp: $(head -1 "$stats_file")"
            fi
        done
    fi
    
    echo ""
}

# Function to analyze timing and performance
analyze_performance() {
    print_section "PERFORMANCE ANALYSIS"
    
    # Analyze response times during failover
    print_info "Response time analysis during failover..."
    
    # Look for timeouts or delays
    for log_file in "$LOG_DIR"/*.log; do
        if [ -f "$log_file" ]; then
            local service=$(basename "$log_file" .log)
            local timeout_count=$(grep -c "timeout\|slow\|delay" "$log_file" 2>/dev/null || echo "0")
            
            if [ "$timeout_count" -gt 0 ]; then
                print_issue "$service had $timeout_count timeout/delay events"
            else
                print_finding "$service had no timeout/delay events"
            fi
        fi
    done
    
    # Analyze error rates
    print_info "Error analysis..."
    
    for log_file in "$LOG_DIR"/*.log; do
        if [ -f "$log_file" ]; then
            local service=$(basename "$log_file" .log)
            local error_count=$(grep -c "ERROR\|Exception\|Failed" "$log_file" 2>/dev/null || echo "0")
            local warn_count=$(grep -c "WARN" "$log_file" 2>/dev/null || echo "0")
            
            if [ "$error_count" -gt 0 ]; then
                print_issue "$service had $error_count errors"
            fi
            
            if [ "$warn_count" -gt 0 ]; then
                print_info "$service had $warn_count warnings"
            fi
        fi
    done
    
    echo ""
}

# Function to analyze cluster state changes
analyze_cluster_states() {
    print_section "CLUSTER STATE ANALYSIS"
    
    # Analyze cluster info snapshots
    if ls "$LOG_DIR"/cluster-info-*.json >/dev/null 2>&1; then
        print_info "Cluster state timeline:"
        
        for cluster_file in "$LOG_DIR"/cluster-info-*.json; do
            local service=$(basename "$cluster_file" .json | cut -d- -f3)
            local timestamp=$(basename "$cluster_file" .json | cut -d- -f4)
            
            if command -v jq >/dev/null 2>&1; then
                local node_id=$(jq -r '.nodeId // "unknown"' "$cluster_file" 2>/dev/null)
                local cluster_size=$(jq -r '.clusterSize // 0' "$cluster_file" 2>/dev/null)
                local members=$(jq -r '.members | length' "$cluster_file" 2>/dev/null || echo "0")
                
                print_finding "$timestamp - $service: NodeId=$node_id, ClusterSize=$cluster_size, Members=$members"
            else
                print_info "$timestamp - $service: $(head -1 "$cluster_file")"
            fi
        done
    fi
    
    echo ""
}

# Function to generate recommendations
generate_recommendations() {
    print_section "RECOMMENDATIONS"
    
    # Check for common issues and provide recommendations
    local has_errors=false
    
    # Check for message loss
    if grep -q "message.*lost\|failed to deliver" "$LOG_DIR"/*.log 2>/dev/null; then
        print_issue "Potential message loss detected - Review message persistence settings"
        has_errors=true
    fi
    
    # Check for long failover times
    if grep -q "timeout.*exceeded\|failover.*slow" "$LOG_DIR"/*.log 2>/dev/null; then
        print_issue "Slow failover detected - Consider tuning cluster heartbeat settings"
        has_errors=true
    fi
    
    # Check for split brain scenarios
    if grep -q "split.*brain\|multiple.*coordinators" "$LOG_DIR"/*.log 2>/dev/null; then
        print_issue "Potential split-brain scenario - Review quorum settings"
        has_errors=true
    fi
    
    if [ "$has_errors" = false ]; then
        print_finding "No critical issues detected in failover test"
        print_finding "Cluster appears to be handling failover correctly"
    fi
    
    echo ""
    print_info "For detailed analysis, examine the following files:"
    echo "  - $LOG_DIR/consumer1.log (failover behavior)"
    echo "  - $LOG_DIR/consumer2.log (load redistribution)"
    echo "  - $LOG_DIR/coordinator*.log (cluster management)"
    echo "  - $LOG_DIR/*stats*.json (processing statistics)"
    
    echo ""
}

# Function to create summary report
create_summary_report() {
    local report_file="$LOG_DIR/failover-analysis-report.txt"
    
    print_header "GENERATING SUMMARY REPORT"
    
    {
        echo "CAMEL CLUSTER FAILOVER TEST ANALYSIS REPORT"
        echo "=========================================="
        echo "Generated: $(date)"
        echo "Log Directory: $LOG_DIR"
        echo ""
        
        echo "CLUSTER SERVICES ANALYZED:"
        for log_file in "$LOG_DIR"/*.log; do
            if [ -f "$log_file" ]; then
                local service=$(basename "$log_file" .log)
                local line_count=$(wc -l < "$log_file")
                echo "  - $service: $line_count log lines"
            fi
        done
        
        echo ""
        echo "KEY METRICS:"
        
        # Extract key metrics
        local total_orders_generated=$(grep -c "Order.*generated\|Generated order" "$LOG_DIR"/*.log 2>/dev/null || echo "0")
        local total_orders_processed=$(grep -c "Processing order\|Order.*processed" "$LOG_DIR"/*.log 2>/dev/null || echo "0")
        local total_errors=$(grep -c "ERROR" "$LOG_DIR"/*.log 2>/dev/null || echo "0")
        
        echo "  - Total orders generated: $total_orders_generated"
        echo "  - Total orders processed: $total_orders_processed"
        echo "  - Total errors: $total_errors"
        
        if [ "$total_orders_generated" -gt 0 ]; then
            local success_rate=$((total_orders_processed * 100 / total_orders_generated))
            echo "  - Success rate: $success_rate%"
        fi
        
    } > "$report_file"
    
    print_finding "Summary report generated: $report_file"
}

# Run all analyses
analyze_cluster_membership
analyze_consumer_failover
analyze_message_routing
analyze_performance
analyze_cluster_states
generate_recommendations
create_summary_report

print_header "ANALYSIS COMPLETE"
print_finding "Failover test analysis completed successfully"
print_info "Check the generated report for detailed findings" 