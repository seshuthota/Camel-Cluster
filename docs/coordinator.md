# Coordinator Service Documentation

## 🎯 Overview

The **Coordinator Service** is the cluster management brain of the Apache Camel cluster. It handles cluster leadership election, node monitoring, health checks, cleanup tasks, and load balancing decisions. Only the elected leader coordinator executes master routes to ensure single execution of critical cluster-wide tasks.

## 📊 Service Architecture

```
┌─────────────────────────────────────────┐
│         Coordinator Service            │
├─────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────────┐   │
│  │   REST API  │  │  Master Routes  │   │
│  │             │  │ (Leader Only)   │   │
│  │ • Cluster   │  │ • Health Check  │   │
│  │ • Monitor   │  │ • DB Cleanup    │   │
│  │ • Control   │  │ • Reporting     │   │
│  │ • Metrics   │  │ • Rebalancing   │   │
│  └─────────────┘  └─────────────────┘   │
├─────────────────────────────────────────┤
│           Infrastructure                │
│ • Hazelcast Leader Election            │
│ • Cluster State Management             │
│ • Node Health Monitoring               │
│ • Event Broadcasting                   │
└─────────────────────────────────────────┘
```

## 🔧 Configuration

### Application Properties
```yaml
# Server Configuration
server:
  port: 8083

# Cluster Configuration
cluster:
  node:
    id: coordinator-1
    type: coordinator
  monitor:
    health-check-interval: 30000
    node-timeout: 60000
    cleanup-interval: 3600000
    reporting-interval: 1800000
    rebalance-interval: 600000

# Coordinator-specific Settings
coordinator:
  leader-election:
    timeout: 30000
  monitoring:
    node-health-timeout: 120000
    failed-node-threshold: 3
  cleanup:
    old-records-retention-days: 30
    batch-size: 1000
```

### Environment Variables
| Variable | Default | Description |
|----------|---------|-------------|
| `CLUSTER_NODE_ID` | `coordinator-1` | Unique node identifier |
| `CLUSTER_NODE_TYPE` | `coordinator` | Node type for clustering |
| `HAZELCAST_PORT` | `5703` | Hazelcast clustering port |
| `HEALTH_CHECK_INTERVAL` | `30000` | Health check interval (ms) |
| `CLEANUP_INTERVAL` | `3600000` | Database cleanup interval (ms) |
| `REPORTING_INTERVAL` | `1800000` | Reporting interval (ms) |
| `REBALANCE_INTERVAL` | `600000` | Load rebalance interval (ms) |
| `NODE_TIMEOUT` | `60000` | Node timeout threshold (ms) |

## 🛣️ Camel Routes

### 1. Cluster Health Check Route (Master)
**Route ID**: `cluster-health-check`

```java
from("master:cluster-health:timer://healthCheck?period=" + healthCheckInterval)
    .routeId("cluster-health-check")
    .log("🏥 Running cluster health check from coordinator: " + nodeId)
    .process(exchange -> {
        ClusterHealthReport report = performClusterHealthCheck();
        
        // Update cluster health status
        clusterService.updateClusterHealth(report);
        
        // Detect and handle failed nodes
        handleFailedNodes(report.getFailedNodes());
        
        // Update cluster metrics
        updateClusterMetrics(report);
        
        exchange.getIn().setBody(report);
    })
    .log("✅ Cluster health check completed. Status: ${body.status}");
```

**Functionality**:
- **Master Route**: Only leader coordinator executes
- **Action**: Monitor all cluster nodes health
- **Frequency**: Every 30 seconds (configurable)
- **Output**: Cluster health reports and node status updates

### 2. Database Cleanup Route (Master)
**Route ID**: `database-cleanup`

```java
from("master:db-cleanup:timer://dbCleanup?period=" + cleanupInterval)
    .routeId("database-cleanup")
    .log("🧹 Running database cleanup from coordinator: " + nodeId)
    .process(exchange -> {
        CleanupReport report = new CleanupReport();
        
        // Clean old processed orders
        int cleanedOrders = cleanupOldOrders();
        report.setOrdersCleaned(cleanedOrders);
        
        // Clean old processed files
        int cleanedFiles = cleanupOldFiles();
        report.setFilesCleaned(cleanedFiles);
        
        // Clean old metrics
        int cleanedMetrics = cleanupOldMetrics();
        report.setMetricsCleaned(cleanedMetrics);
        
        // Update cleanup statistics
        clusterService.storeMetric("cleanup_last_run", System.currentTimeMillis());
        clusterService.storeMetric("cleanup_orders_cleaned", cleanedOrders);
        clusterService.storeMetric("cleanup_files_cleaned", cleanedFiles);
        
        exchange.getIn().setBody(report);
    })
    .log("✅ Database cleanup completed. Report: ${body}");
```

**Functionality**:
- **Master Route**: Only leader coordinator executes
- **Action**: Clean old data from database
- **Frequency**: Every hour (configurable)
- **Output**: Cleanup statistics and performance metrics

### 3. Cluster Reporting Route (Master)
**Route ID**: `cluster-reporting`

```java
from("master:reporting:timer://reporting?period=" + reportingInterval)
    .routeId("cluster-reporting")
    .log("📊 Generating cluster report from coordinator: " + nodeId)
    .process(exchange -> {
        ClusterReport report = generateClusterReport();
        
        // Generate performance statistics
        generatePerformanceReport(report);
        
        // Generate capacity planning recommendations
        generateCapacityReport(report);
        
        // Store report
        reportingService.storeReport(report);
        
        // Broadcast report to interested parties
        broadcastReport(report);
        
        exchange.getIn().setBody(report);
    })
    .log("✅ Cluster report generated and distributed");
```

**Functionality**:
- **Master Route**: Only leader coordinator executes
- **Action**: Generate comprehensive cluster reports
- **Frequency**: Every 30 minutes (configurable)
- **Output**: Performance reports and capacity recommendations

### 4. Load Rebalancing Route (Master)
**Route ID**: `load-rebalancing`

```java
from("master:rebalance:timer://rebalance?period=" + rebalanceInterval)
    .routeId("load-rebalancing")
    .log("⚖️ Running load rebalancing from coordinator: " + nodeId)
    .process(exchange -> {
        RebalanceReport report = performLoadRebalancing();
        
        // Analyze current load distribution
        LoadAnalysis analysis = analyzeClusterLoad();
        
        // Make rebalancing decisions
        List<RebalanceAction> actions = decideRebalanceActions(analysis);
        
        // Execute rebalancing actions
        executeRebalanceActions(actions);
        
        // Update rebalancing metrics
        clusterService.storeMetric("rebalance_last_run", System.currentTimeMillis());
        clusterService.storeMetric("rebalance_actions_taken", actions.size());
        
        exchange.getIn().setBody(report);
    })
    .log("✅ Load rebalancing completed. Actions: ${body.actionsTaken}");
```

**Functionality**:
- **Master Route**: Only leader coordinator executes
- **Action**: Monitor and rebalance cluster load
- **Frequency**: Every 10 minutes (configurable)
- **Output**: Load balancing decisions and actions

### 5. Cluster Events Route (Master)
**Route ID**: `cluster-events-handler`

```java
from("master:events:hazelcast-topic:" + ClusterConstants.CLUSTER_EVENTS_TOPIC)
    .routeId("cluster-events-handler")
    .log("📢 Coordinator received cluster event: ${body}")
    .process(exchange -> {
        String eventData = exchange.getIn().getBody(String.class);
        ClusterEvent event = ClusterEvent.fromJson(eventData);
        
        // Handle different event types
        switch (event.getType()) {
            case NODE_JOINED:
                handleNodeJoined(event);
                break;
            case NODE_LEFT:
                handleNodeLeft(event);
                break;
            case LEADERSHIP_CHANGE:
                handleLeadershipChange(event);
                break;
            case LOAD_WARNING:
                handleLoadWarning(event);
                break;
        }
        
        // Log event processing
        eventLog.logEvent(event);
    })
    .log("✅ Cluster event processed: ${body}");
```

**Functionality**:
- **Master Route**: Only leader coordinator handles events
- **Action**: Process cluster-wide events
- **Output**: Event handling and cluster state updates

### 6. Coordinator Health Check Route
**Route ID**: `coordinator-health-check`

```java
from("timer:coordinator-health?period=30000")
    .routeId("coordinator-health-check")
    .process(exchange -> {
        String nodeId = clusterService.getNodeId();
        boolean isLeader = clusterService.isLeader();
        
        Map<String, Object> statusInfo = Map.of(
            "status", "ACTIVE",
            "description", isLeader ? "Leader coordinator managing cluster" : "Standby coordinator ready",
            "isLeader", isLeader,
            "clusterSize", clusterService.getClusterSize(),
            "lastHealthCheck", System.currentTimeMillis()
        );
        
        clusterService.updateNodeStatus("ACTIVE", statusInfo);
        
        // Update coordinator-specific metrics
        clusterService.storeMetric("coordinator_last_heartbeat", System.currentTimeMillis());
        clusterService.storeMetric("coordinator_is_leader", isLeader ? 1L : 0L);
        clusterService.storeMetric("cluster_size", (long) clusterService.getClusterSize());
    });
```

**Functionality**:
- **Regular Route**: All coordinators execute
- **Action**: Update coordinator health status
- **Output**: Health status and leadership information

## 🌐 REST API Endpoints

### Cluster Management

#### GET `/api/coordinator/cluster/status`
**Description**: Get comprehensive cluster status

**Response**:
```json
{
  "clusterInfo": {
    "clusterSize": 5,
    "leaderNode": "coordinator-1",
    "isThisNodeLeader": true,
    "clusterHealth": "HEALTHY",
    "lastHealthCheck": "2024-01-15T10:30:00Z"
  },
  "nodes": [
    {
      "nodeId": "producer-1",
      "nodeType": "producer",
      "status": "RUNNING",
      "lastSeen": "2024-01-15T10:29:45Z",
      "metrics": {
        "ordersGenerated": 1250,
        "filesGenerated": 15
      }
    },
    {
      "nodeId": "consumer-1",
      "nodeType": "consumer",
      "status": "RUNNING",
      "lastSeen": "2024-01-15T10:29:50Z",
      "metrics": {
        "ordersProcessed": 2500,
        "filesProcessed": 25
      }
    }
  ],
  "queues": {
    "orders": {
      "messageCount": 15,
      "consumerCount": 3,
      "averageProcessingTime": "120ms"
    }
  },
  "performance": {
    "throughput": "45 orders/min",
    "latency": "avg 120ms",
    "errorRate": "0.5%"
  }
}
```

#### GET `/api/coordinator/cluster/health`
**Description**: Detailed cluster health information

**Response**:
```json
{
  "overallHealth": "HEALTHY",
  "lastHealthCheck": "2024-01-15T10:30:00Z",
  "components": {
    "database": {
      "status": "UP",
      "connectionPool": "Healthy",
      "activeConnections": 12
    },
    "messaging": {
      "status": "UP",
      "brokerStatus": "Connected",
      "queueHealth": "Normal"
    },
    "clustering": {
      "status": "UP",
      "hazelcastStatus": "Connected",
      "memberCount": 5
    }
  },
  "nodeHealth": [
    {
      "nodeId": "producer-1",
      "status": "HEALTHY",
      "lastContact": "2024-01-15T10:29:45Z",
      "responseTime": "15ms"
    }
  ],
  "alerts": []
}
```

#### POST `/api/coordinator/cluster/rebalance`
**Description**: Trigger manual cluster rebalancing

**Response**:
```json
{
  "rebalanceId": "rebal-20240115-103000",
  "status": "INITIATED",
  "startedAt": "2024-01-15T10:30:00Z",
  "actions": [
    {
      "type": "SCALE_CONSUMERS",
      "target": "consumer-instances",
      "from": 2,
      "to": 4,
      "reason": "High queue depth detected"
    }
  ],
  "estimatedCompletionTime": "2024-01-15T10:32:00Z"
}
```

### Node Management

#### GET `/api/coordinator/nodes`
**Description**: List all cluster nodes

**Response**:
```json
{
  "nodes": [
    {
      "nodeId": "coordinator-1",
      "nodeType": "coordinator",
      "status": "RUNNING",
      "isLeader": true,
      "uptime": "PT2H30M45S",
      "lastSeen": "2024-01-15T10:30:00Z",
      "address": "10.0.0.10:5703",
      "resources": {
        "cpuUsage": "25%",
        "memoryUsage": "60%"
      }
    }
  ],
  "totalNodes": 5,
  "healthyNodes": 5,
  "leader": "coordinator-1"
}
```

#### GET `/api/coordinator/nodes/{nodeId}`
**Description**: Get detailed information about a specific node

**Response**:
```json
{
  "nodeId": "consumer-1",
  "nodeType": "consumer",
  "status": "RUNNING",
  "uptime": "PT2H15M30S",
  "lastSeen": "2024-01-15T10:29:50Z",
  "address": "10.0.0.12:5702",
  "performance": {
    "ordersProcessed": 2500,
    "filesProcessed": 25,
    "averageProcessingTime": "120ms",
    "errorRate": "0.3%"
  },
  "resources": {
    "cpuUsage": "35%",
    "memoryUsage": "65%",
    "diskUsage": "45%"
  },
  "routes": [
    {
      "id": "consumer-order-processor",
      "status": "Started",
      "exchangesCompleted": 2500,
      "exchangesFailed": 8
    }
  ]
}
```

#### DELETE `/api/coordinator/nodes/{nodeId}`
**Description**: Remove a node from the cluster (graceful shutdown)

**Response**:
```json
{
  "nodeId": "consumer-2",
  "action": "GRACEFUL_SHUTDOWN",
  "status": "INITIATED",
  "estimatedShutdownTime": "2024-01-15T10:32:00Z",
  "drainStatus": "DRAINING_CONNECTIONS"
}
```

### Reports and Analytics

#### GET `/api/coordinator/reports/cluster`
**Description**: Get latest cluster performance report

**Response**:
```json
{
  "reportId": "cluster-report-20240115-103000",
  "generatedAt": "2024-01-15T10:30:00Z",
  "period": {
    "from": "2024-01-15T10:00:00Z",
    "to": "2024-01-15T10:30:00Z"
  },
  "performance": {
    "ordersProcessed": 1350,
    "filesProcessed": 12,
    "averageThroughput": "45 orders/min",
    "averageLatency": "120ms",
    "errorRate": "0.5%",
    "uptime": "99.8%"
  },
  "resources": {
    "totalCpuUsage": "25%",
    "totalMemoryUsage": "60%",
    "peakCpuUsage": "45%",
    "peakMemoryUsage": "75%"
  },
  "recommendations": [
    {
      "type": "SCALING",
      "priority": "MEDIUM",
      "description": "Consider adding one more consumer instance during peak hours",
      "impact": "Reduce queue depth by 30%"
    }
  ]
}
```

#### GET `/api/coordinator/metrics`
**Description**: Real-time cluster metrics

**Response**:
```json
{
  "coordinator": {
    "isLeader": true,
    "lastHealthCheck": "2024-01-15T10:29:55Z",
    "clusterEventsHandled": 156,
    "rebalanceActionsExecuted": 12,
    "lastCleanup": "2024-01-15T09:00:00Z"
  },
  "cluster": {
    "totalNodes": 5,
    "healthyNodes": 5,
    "averageNodeUptime": "PT2H30M45S",
    "totalOrdersProcessed": 25000,
    "totalFilesProcessed": 250,
    "currentThroughput": "45 orders/min"
  },
  "infrastructure": {
    "database": {
      "totalConnections": 25,
      "activeConnections": 12,
      "queryAverageTime": "15ms"
    },
    "messaging": {
      "totalQueues": 3,
      "totalMessages": 45,
      "averageMessageAge": "2.5s"
    }
  }
}
```

## 🔧 Service Components

### ClusterHealthMonitor
**Location**: `com.example.coordinator.service.ClusterHealthMonitor`

**Responsibilities**:
- Monitor all cluster nodes health
- Detect failed or unresponsive nodes
- Generate health reports
- Trigger recovery actions

**Key Methods**:
```java
public ClusterHealthReport performHealthCheck()
public void handleFailedNode(String nodeId)
public boolean isNodeHealthy(String nodeId)
public List<String> getFailedNodes()
public ClusterHealthStatus getOverallHealth()
```

### LoadBalancer
**Location**: `com.example.coordinator.service.LoadBalancer`

**Responsibilities**:
- Monitor cluster load distribution
- Make rebalancing decisions
- Execute scaling actions
- Optimize resource utilization

**Key Methods**:
```java
public LoadAnalysis analyzeClusterLoad()
public List<RebalanceAction> decideRebalanceActions(LoadAnalysis analysis)
public void executeRebalanceAction(RebalanceAction action)
public RebalanceReport getLastRebalanceReport()
```

### ClusterReportingService
**Location**: `com.example.coordinator.service.ClusterReportingService`

**Responsibilities**:
- Generate performance reports
- Collect cluster metrics
- Provide capacity planning insights
- Store and retrieve historical data

**Key Methods**:
```java
public ClusterReport generateClusterReport()
public PerformanceReport generatePerformanceReport()
public List<Recommendation> generateRecommendations()
public void storeReport(ClusterReport report)
```

### DatabaseCleanupService
**Location**: `com.example.coordinator.service.DatabaseCleanupService`

**Responsibilities**:
- Clean old processed orders
- Remove outdated metrics
- Manage storage space
- Optimize database performance

**Key Methods**:
```java
public int cleanupOldOrders(int retentionDays)
public int cleanupOldMetrics(int retentionDays)
public int cleanupOldFiles(int retentionDays)
public CleanupReport performFullCleanup()
```

## 🔄 Leader Election & Failover

### Leadership Election Process
```java
// Hazelcast-based leader election
@PostConstruct
public void initializeLeaderElection() {
    // Create leader latch
    String leaderLatchPath = "/cluster/leader";
    
    // Participate in leader election
    hazelcastInstance.getCPSubsystem()
        .getAtomicReference(leaderLatchPath)
        .compareAndSet(null, nodeId);
        
    // Monitor leadership changes
    hazelcastInstance.getTopic("leadership-events")
        .addMessageListener(this::handleLeadershipChange);
}
```

### Failover Scenarios

#### Leader Coordinator Failure
1. **Detection**: Remaining coordinators detect leader absence
2. **Election**: New leader elected via Hazelcast CP subsystem
3. **Recovery**: New leader resumes master routes
4. **Notification**: Cluster notified of leadership change

#### Standby Coordinator Promotion
```java
public void onLeadershipAcquired() {
    logger.info("This node has become the cluster leader");
    
    // Start master routes
    camelContext.getRouteController().startRoute("cluster-health-check");
    camelContext.getRouteController().startRoute("database-cleanup");
    camelContext.getRouteController().startRoute("cluster-reporting");
    camelContext.getRouteController().startRoute("load-rebalancing");
    
    // Broadcast leadership change
    broadcastLeadershipChange();
    
    // Initialize leader-specific services
    initializeLeaderServices();
}
```

## 🏗️ Deployment Configuration

### Docker Configuration
```dockerfile
FROM openjdk:17-jre-slim

COPY target/camel-coordinator-*.jar app.jar

EXPOSE 8083 5703

ENV SPRING_PROFILES_ACTIVE=docker
ENV CLUSTER_NODE_TYPE=coordinator

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Docker Compose (High Availability)
```yaml
coordinator1:
  build:
    context: .
    dockerfile: camel-coordinator/Dockerfile
  ports:
    - "8083:8083"
  environment:
    - CLUSTER_NODE_ID=coordinator-1
    - CLUSTER_NODE_TYPE=coordinator
    - HAZELCAST_PORT=5703
    - SPRING_PROFILES_ACTIVE=docker
  volumes:
    - coordinator_data:/app/data

coordinator2:
  build:
    context: .
    dockerfile: camel-coordinator/Dockerfile
  ports:
    - "8084:8083"  # Different host port
  environment:
    - CLUSTER_NODE_ID=coordinator-2
    - CLUSTER_NODE_TYPE=coordinator
    - HAZELCAST_PORT=5704
    - SPRING_PROFILES_ACTIVE=docker
  volumes:
    - coordinator_data:/app/data
```

### Kubernetes Deployment (HA)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: camel-coordinator
spec:
  replicas: 2  # Always run 2 coordinators for HA
  selector:
    matchLabels:
      app: camel-cluster
      component: coordinator
  template:
    spec:
      containers:
      - name: coordinator
        image: camel-coordinator:latest
        ports:
        - containerPort: 8083
        - containerPort: 5703
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8083
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /api/coordinator/cluster/status
            port: 8083
          initialDelaySeconds: 30
          periodSeconds: 10
```

## 📊 Monitoring & Alerting

### Key Metrics to Monitor
- **Leadership status**: Is this node the leader?
- **Cluster health**: Overall cluster status
- **Node count**: Number of active nodes
- **Failed nodes**: Count of unhealthy nodes
- **Queue depths**: Message backlogs
- **Processing rates**: Throughput metrics
- **Resource utilization**: CPU, memory, storage
- **Cleanup operations**: Database maintenance

### Alert Configuration
```yaml
alerts:
  - name: LeadershipLoss
    condition: is_leader = false AND was_leader = true
    severity: critical
    description: "Coordinator lost leadership"
    
  - name: ClusterNodeDown
    condition: healthy_nodes < total_nodes
    severity: warning
    description: "One or more cluster nodes are down"
    
  - name: HighQueueDepth
    condition: max_queue_depth > 100
    severity: warning
    description: "Message queue depth is high"
    
  - name: DatabaseCleanupFailed
    condition: last_cleanup_status = 'FAILED'
    severity: warning
    description: "Database cleanup operation failed"
```

### Health Check Script
```bash
#!/bin/bash
# coordinator-health-check.sh

COORDINATOR_URL="http://localhost:8083"

# Check coordinator health
HEALTH_STATUS=$(curl -s "$COORDINATOR_URL/actuator/health" | jq -r '.status')

if [ "$HEALTH_STATUS" != "UP" ]; then
    echo "❌ Coordinator health check failed: $HEALTH_STATUS"
    exit 1
fi

# Check cluster status
CLUSTER_HEALTH=$(curl -s "$COORDINATOR_URL/api/coordinator/cluster/health" | jq -r '.overallHealth')

if [ "$CLUSTER_HEALTH" != "HEALTHY" ]; then
    echo "⚠️ Cluster health warning: $CLUSTER_HEALTH"
    exit 1
fi

echo "✅ Coordinator and cluster are healthy"
exit 0
```

## 🚨 Troubleshooting

### Common Issues

#### 1. Leadership Election Problems
**Symptoms**: Multiple leaders or no leader

**Solutions**:
```bash
# Check Hazelcast cluster status
curl http://localhost:8083/api/coordinator/cluster/status

# Verify network connectivity between coordinators
ping coordinator-2

# Check Hazelcast configuration
grep -A 10 "hazelcast:" application.yml

# Restart coordinators in sequence
docker restart coordinator-1
sleep 30
docker restart coordinator-2
```

#### 2. Master Routes Not Executing
**Symptoms**: No health checks or cleanup operations

**Solutions**:
```bash
# Verify leadership status
curl http://localhost:8083/api/coordinator/cluster/status | jq '.clusterInfo.isThisNodeLeader'

# Check route status
curl http://localhost:8083/actuator/camel/routes

# Force leadership election
docker restart coordinator-1 coordinator-2
```

#### 3. High Memory Usage During Cleanup
**Symptoms**: OutOfMemoryError during database cleanup

**Solutions**:
```bash
# Increase JVM heap size
export JAVA_OPTS="-Xms1024m -Xmx2048m"

# Reduce cleanup batch size
export CLEANUP_BATCH_SIZE=500

# Schedule cleanup during off-peak hours
export CLEANUP_INTERVAL=86400000  # 24 hours
```

---

**Related Documentation**:
- [Producer Service](./producer.md) - Data generation service
- [Consumer Service](./consumer.md) - Data processing service
- [Clustering](./clustering.md) - Hazelcast configuration
- [Service Discovery](./service-discovery.md) - Dynamic member discovery 