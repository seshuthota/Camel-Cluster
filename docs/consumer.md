# Consumer Service Documentation

## 🎯 Overview

The **Consumer Service** is the primary workload processor in the Apache Camel cluster. It consumes orders from ActiveMQ queues and processes files from shared directories, implementing load balancing and fault tolerance patterns.

## 📊 Service Architecture

```
┌─────────────────────────────────────────┐
│           Consumer Service              │
├─────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────────┐   │
│  │   REST API  │  │  Camel Routes   │   │
│  │             │  │                 │   │
│  │ • Status    │  │ • Order Proc    │   │
│  │ • Health    │  │ • File Proc     │   │
│  │ • Metrics   │  │ • Health Check  │   │
│  │ • Control   │  │ • Event Handle  │   │
│  └─────────────┘  └─────────────────┘   │
├─────────────────────────────────────────┤
│           Infrastructure                │
│ • ActiveMQ Consumer (3-6 concurrent)   │
│ • File System Watcher                  │
│ • Database Persistence                 │
│ • Cluster Event Handler                │
└─────────────────────────────────────────┘
```

## 🔧 Configuration

### Application Properties
```yaml
# Server Configuration
server:
  port: 8082

# Cluster Configuration
cluster:
  node:
    id: consumer-1
    type: consumer

# Consumer-specific Settings
app:
  consumer:
    concurrent-consumers: 3
    max-concurrent-consumers: 6
    file-polling-interval: 10000
    input-directory: /shared/orders
    batch-size: 10
    processing-timeout: 30000
```

### Environment Variables
| Variable | Default | Description |
|----------|---------|-------------|
| `CLUSTER_NODE_ID` | `consumer-1` | Unique node identifier |
| `CLUSTER_NODE_TYPE` | `consumer` | Node type for clustering |
| `HAZELCAST_PORT` | `5702` | Hazelcast clustering port |
| `CONCURRENT_CONSUMERS` | `3` | Initial consumer threads |
| `MAX_CONCURRENT_CONSUMERS` | `6` | Maximum consumer threads |
| `FILE_POLLING_INTERVAL` | `10000` | File polling interval (ms) |
| `INPUT_DIRECTORY` | `/shared/orders` | File input directory |

## 🛣️ Camel Routes

### 1. Order Processing Route
**Route ID**: `consumer-order-processor`

```java
from("activemq:queue:" + ClusterConstants.ORDERS_QUEUE + 
     "?concurrentConsumers=" + concurrentConsumers +
     "&maxConcurrentConsumers=" + (concurrentConsumers * 2))
    .routeId(ClusterConstants.CONSUMER_ORDER_ROUTE_ID)
    .log("Consumer ${header.CamelJMSDestination} received order on node: " + 
         clusterService.getNodeId())
    .process(exchange -> {
        String orderJson = exchange.getIn().getBody(String.class);
        orderProcessor.processOrder(orderJson);
        
        // Set response for potential monitoring
        exchange.getIn().setHeader("ProcessedBy", clusterService.getNodeId());
        exchange.getIn().setHeader("ProcessedAt", System.currentTimeMillis());
    })
    .log("Order processed successfully by consumer node: " + clusterService.getNodeId());
```

**Functionality**:
- **Source**: ActiveMQ `orders` queue
- **Concurrency**: 3-6 concurrent consumers
- **Action**: Process orders and persist to database
- **Output**: Processed order records

### 2. File Processing Route
**Route ID**: `consumer-file-processor`

```java
from("file:" + inputDirectory + 
     "?delay=" + filePollingInterval +
     "&delete=false" +
     "&include=.*\\.csv" +
     "&readLock=changed" +
     "&readLockCheckInterval=1000" +
     "&readLockTimeout=10000")
    .routeId(ClusterConstants.CONSUMER_FILE_ROUTE_ID)
    .log("Consumer found file: ${header.CamelFileName} on node: " + 
         clusterService.getNodeId())
    .process(exchange -> {
        File file = exchange.getIn().getBody(File.class);
        fileProcessor.processFile(file);
    })
    .log("File processing completed by node: " + clusterService.getNodeId());
```

**Functionality**:
- **Source**: File system polling (CSV files)
- **Locking**: File locks prevent duplicate processing
- **Action**: Parse CSV and process contained orders
- **Output**: Processed file records and individual orders

### 3. Health Check Route
**Route ID**: `consumer-health-check`

```java
from("timer:consumer-health?period=30000")
    .routeId(ClusterConstants.CONSUMER_HEALTH_ROUTE_ID)
    .process(exchange -> {
        String nodeId = clusterService.getNodeId();
        boolean isLeader = clusterService.isLeader();
        
        // Update node status with proper format
        Map<String, Object> statusInfo = Map.of(
            "status", "ACTIVE",
            "description", "Consumer processing orders and files",
            "ordersProcessed", orderProcessor.getProcessedCount(),
            "filesProcessed", fileProcessor.getFilesProcessed()
        );
        clusterService.updateNodeStatus("ACTIVE", statusInfo);
        
        // Publish health metrics
        clusterService.storeMetric("consumer_last_heartbeat", System.currentTimeMillis());
        clusterService.storeMetric("consumer_processed_count", orderProcessor.getProcessedCount());
        clusterService.storeMetric("consumer_files_processed", fileProcessor.getFilesProcessed());
    });
```

**Functionality**:
- **Trigger**: Every 30 seconds
- **Action**: Update cluster status with processing metrics
- **Output**: Status and metrics to cluster

### 4. Cluster Event Handler Route
**Route ID**: `consumer-cluster-events`

```java
from("hazelcast-topic:" + ClusterConstants.CLUSTER_EVENTS_TOPIC)
    .routeId(ClusterConstants.CONSUMER_CLUSTER_EVENTS_ROUTE_ID)
    .log("Consumer received cluster event: ${body}")
    .process(exchange -> {
        String event = exchange.getIn().getBody(String.class);
        handleClusterEvent(event);
    });
```

**Functionality**:
- **Source**: Hazelcast cluster events topic
- **Action**: Handle node joins, leaves, and rebalancing
- **Output**: Event processing and local adjustments

### 5. Dead Letter Queue Handler
**Route ID**: `consumer-dlq-handler`

```java
from("activemq:queue:" + ClusterConstants.ORDERS_QUEUE + ".DLQ")
    .routeId("consumer-dlq-handler")
    .log("Processing dead letter message: ${body}")
    .process(exchange -> {
        // Log dead letter for investigation
        String message = exchange.getIn().getBody(String.class);
        logger.error("Dead letter received: {}", message);
        
        // Update failure metrics
        clusterService.storeMetric("orders_dead_letter", 1L);
    });
```

**Functionality**:
- **Source**: Dead Letter Queue (DLQ)
- **Action**: Handle failed message processing
- **Output**: Error logging and metrics

## 🌐 REST API Endpoints

### Processing Control

#### POST `/api/consumer/process/order`
**Description**: Manually process a single order

**Request Body**:
```json
{
  "orderId": "ORD-123",
  "customerId": "CUST-456",
  "productId": "PROD-789",
  "quantity": 2,
  "price": 49.99
}
```

**Response**:
```json
{
  "orderId": "ORD-123",
  "status": "PROCESSED",
  "processedBy": "consumer-1",
  "processedAt": "2024-01-15T10:30:00Z",
  "processingTimeMs": 150
}
```

#### POST `/api/consumer/process/file`
**Description**: Manually trigger file processing

**Request Body**:
```json
{
  "filename": "orders_20240115.csv",
  "filepath": "/shared/orders/orders_20240115.csv"
}
```

**Response**:
```json
{
  "filename": "orders_20240115.csv",
  "status": "PROCESSED",
  "recordsProcessed": 1000,
  "recordsFailed": 5,
  "processedBy": "consumer-1",
  "processingTimeMs": 5000,
  "processedAt": "2024-01-15T10:30:00Z"
}
```

### System Information

#### GET `/api/consumer/status`
**Description**: Get consumer service status

**Response**:
```json
{
  "nodeId": "consumer-1",
  "nodeType": "consumer",
  "status": "RUNNING",
  "uptime": "PT2H30M45S",
  "processing": {
    "ordersProcessed": 2500,
    "filesProcessed": 25,
    "averageOrdersPerMinute": 45,
    "averageProcessingTimeMs": 120,
    "lastOrderProcessed": "2024-01-15T10:29:55Z",
    "lastFileProcessed": "2024-01-15T10:25:00Z",
    "currentLoad": "65%"
  },
  "cluster": {
    "isConnected": true,
    "clusterSize": 5,
    "isLeader": false,
    "consumerNodes": 3
  },
  "queues": {
    "orders": {
      "pending": 15,
      "processing": 3,
      "dlq": 2
    }
  }
}
```

#### GET `/api/consumer/health`
**Description**: Detailed health check

**Response**:
```json
{
  "status": "UP",
  "components": {
    "camelContext": {
      "status": "UP",
      "details": {
        "contextStatus": "Started",
        "routeCount": 5,
        "uptime": "PT2H30M45S"
      }
    },
    "database": {
      "status": "UP",
      "details": {
        "connectionPool": "Healthy",
        "activeConnections": 5,
        "maxConnections": 10
      }
    },
    "activemq": {
      "status": "UP",
      "details": {
        "brokerUrl": "tcp://activemq:61616",
        "connectionStatus": "Connected",
        "consumerCount": 3
      }
    },
    "fileSystem": {
      "status": "UP",
      "details": {
        "inputDirectory": "/shared/orders",
        "accessible": true,
        "freeSpace": "15.2 GB"
      }
    },
    "cluster": {
      "status": "UP",
      "details": {
        "hazelcastStatus": "Connected",
        "clusterSize": 5,
        "nodeRole": "consumer"
      }
    }
  }
}
```

#### GET `/api/consumer/metrics`
**Description**: Detailed processing metrics

**Response**:
```json
{
  "consumer": {
    "ordersProcessed": 2500,
    "filesProcessed": 25,
    "orderProcessingRate": 45.2,
    "fileProcessingRate": 2.1,
    "averageOrderProcessingTime": 120,
    "averageFileProcessingTime": 3500,
    "successRate": 98.5,
    "lastActivity": "2024-01-15T10:29:55Z"
  },
  "jvm": {
    "memoryUsed": "512 MB",
    "memoryMax": "1024 MB",
    "cpuUsage": "35.7%",
    "gcCollections": 125,
    "threadsActive": 18
  },
  "routes": {
    "consumer-order-processor": {
      "status": "Started",
      "exchangesCompleted": 2500,
      "exchangesFailed": 12,
      "averageProcessingTime": "120ms",
      "lastExchange": "2024-01-15T10:29:55Z"
    },
    "consumer-file-processor": {
      "status": "Started",
      "exchangesCompleted": 25,
      "exchangesFailed": 1,
      "averageProcessingTime": "3.5s",
      "lastExchange": "2024-01-15T10:25:00Z"
    }
  },
  "queues": {
    "ordersQueue": {
      "messagesEnqueued": 2512,
      "messagesDequeued": 2500,
      "messagesPending": 12,
      "consumerCount": 3
    }
  }
}
```

### Queue Management

#### GET `/api/consumer/queues`
**Description**: Get queue information

**Response**:
```json
{
  "queues": [
    {
      "name": "orders",
      "messageCount": 15,
      "consumerCount": 3,
      "avgProcessingTime": "120ms",
      "status": "ACTIVE"
    },
    {
      "name": "orders.DLQ",
      "messageCount": 2,
      "consumerCount": 1,
      "status": "MONITORING"
    }
  ],
  "totalPendingMessages": 17,
  "totalConsumers": 4
}
```

#### GET `/api/consumer/routes`
**Description**: Get route information

**Response**:
```json
{
  "routes": [
    {
      "id": "consumer-order-processor",
      "status": "Started",
      "endpoint": "activemq:queue:orders",
      "uptime": "PT2H30M45S",
      "statistics": {
        "exchangesCompleted": 2500,
        "exchangesFailed": 12,
        "averageProcessingTime": "120ms"
      }
    },
    {
      "id": "consumer-file-processor",
      "status": "Started",
      "endpoint": "file:/shared/orders",
      "uptime": "PT2H30M45S",
      "statistics": {
        "exchangesCompleted": 25,
        "exchangesFailed": 1,
        "averageProcessingTime": "3.5s"
      }
    }
  ],
  "totalRoutes": 5,
  "activeRoutes": 5
}
```

## 📦 Data Models

### Order Processing
```java
@Entity
@Table(name = "orders")
public class Order {
    @Id
    private String orderId;
    private String customerId;
    private String productId;
    private Integer quantity;
    private BigDecimal price;
    private LocalDateTime orderDate;
    private String status;
    private String priority;
    private String processedBy;
    private LocalDateTime processedAt;
}
```

### File Processing Record
```java
@Entity
@Table(name = "processed_files")
public class ProcessedFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String filename;
    private String filepath;
    private Integer recordCount;
    private Integer successCount;
    private Integer failureCount;
    private String processedBy;
    private LocalDateTime processedAt;
    private Long processingTimeMs;
}
```

## 🔧 Service Components

### OrderProcessor
**Location**: `com.example.consumer.service.OrderProcessor`

**Responsibilities**:
- Parse and validate order JSON
- Persist orders to database
- Handle processing errors
- Track processing metrics

**Key Methods**:
```java
public void processOrder(String orderJson)
public Order parseOrder(String orderJson)
public void validateOrder(Order order)
public void persistOrder(Order order)
public long getProcessedCount()
public OrderProcessingStats getStats()
```

### FileProcessor
**Location**: `com.example.consumer.service.FileProcessor`

**Responsibilities**:
- Read and parse CSV files
- Process orders in batches
- Handle file locking
- Track file processing metrics

**Key Methods**:
```java
public void processFile(File file)
public List<Order> parseCsvFile(File file)
public void processBatch(List<Order> orders)
public long getFilesProcessed()
public FileProcessingStats getStats()
```

## 🏗️ Deployment Configuration

### Docker Configuration
```dockerfile
FROM openjdk:17-jre-slim

COPY target/camel-consumer-*.jar app.jar

EXPOSE 8082 5702

ENV SPRING_PROFILES_ACTIVE=docker
ENV CLUSTER_NODE_TYPE=consumer

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Docker Compose
```yaml
consumer:
  build:
    context: .
    dockerfile: camel-consumer/Dockerfile
  ports:
    - "8082"  # Dynamic port assignment
  environment:
    - CLUSTER_NODE_ID=consumer-${HOSTNAME:-1}
    - CLUSTER_NODE_TYPE=consumer
    - HAZELCAST_PORT=5702
    - SPRING_PROFILES_ACTIVE=docker
    - CONCURRENT_CONSUMERS=3
    - MAX_CONCURRENT_CONSUMERS=6
  volumes:
    - shared_files:/app/data
  deploy:
    replicas: 2  # Start with 2, can scale up
```

### Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: camel-consumer
spec:
  replicas: 2
  selector:
    matchLabels:
      app: camel-cluster
      component: consumer
  template:
    spec:
      containers:
      - name: consumer
        image: camel-consumer:latest
        ports:
        - containerPort: 8082
        - containerPort: 5702
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        env:
        - name: CONCURRENT_CONSUMERS
          value: "5"
        - name: MAX_CONCURRENT_CONSUMERS
          value: "10"
```

## 📊 Load Balancing & Scaling

### ActiveMQ Load Balancing
```yaml
# Automatic load balancing via concurrent consumers
app:
  consumer:
    concurrent-consumers: 3      # Initial consumers
    max-concurrent-consumers: 6  # Scale up to 6 under load
```

### Horizontal Pod Autoscaler (Kubernetes)
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: camel-consumer-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: camel-consumer
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### Manual Scaling
```bash
# Docker Compose
docker-compose up --scale consumer=5

# Kubernetes
kubectl scale deployment camel-consumer --replicas=5 -n camel-cluster
```

## 📊 Monitoring & Observability

### Health Checks
```bash
# Basic health check
curl http://localhost:8082/actuator/health

# Detailed status
curl http://localhost:8082/api/consumer/status

# Processing metrics
curl http://localhost:8082/api/consumer/metrics

# Queue status
curl http://localhost:8082/api/consumer/queues
```

### Key Metrics to Monitor
- **Order processing rate**: Orders per second
- **File processing rate**: Files per hour
- **Processing latency**: Average time per order/file
- **Queue depth**: Pending messages in ActiveMQ
- **Error rate**: Failed vs successful processing
- **Memory usage**: JVM heap utilization
- **CPU usage**: System resource consumption
- **Concurrent consumers**: Active consumer threads

### Alerts Configuration
```yaml
alerts:
  - name: HighQueueDepth
    condition: queue_depth > 100
    severity: warning
    
  - name: HighErrorRate
    condition: error_rate > 5%
    severity: critical
    
  - name: HighMemoryUsage
    condition: memory_usage > 85%
    severity: warning
    
  - name: HighProcessingLatency
    condition: avg_processing_time > 5s
    severity: warning
```

## 🚨 Troubleshooting

### Common Issues

#### 1. Messages Not Being Consumed
**Symptoms**: Orders stuck in queue

**Causes & Solutions**:
```bash
# Check consumer status
curl http://localhost:8082/api/consumer/status

# Verify queue connection
curl http://localhost:8082/api/consumer/queues

# Check ActiveMQ console
open http://localhost:8161

# Restart consumers
docker restart consumer-container
```

#### 2. File Processing Failures
**Symptoms**: CSV files not being processed

**Causes & Solutions**:
```bash
# Check file permissions
ls -la /shared/orders/

# Verify file format
head -5 /shared/orders/problematic_file.csv

# Check file locks
lsof /shared/orders/

# Monitor file processing
curl http://localhost:8082/api/consumer/metrics | jq '.routes."consumer-file-processor"'
```

#### 3. Database Connection Issues
**Symptoms**: Orders processed but not saved

**Causes & Solutions**:
```bash
# Check database health
curl http://localhost:8082/actuator/health | jq '.components.database'

# Verify connection pool
curl http://localhost:8082/api/consumer/metrics | jq '.database'

# Check PostgreSQL logs
docker logs postgres-container

# Test database connection
psql -h localhost -U camel_user -d camel_cluster -c "SELECT COUNT(*) FROM orders;"
```

#### 4. High Memory Usage
**Symptoms**: OutOfMemoryError or slow processing

**Solutions**:
```bash
# Increase JVM heap size
export JAVA_OPTS="-Xms1024m -Xmx2048m"

# Monitor memory usage
curl http://localhost:8082/api/consumer/metrics | jq '.jvm'

# Enable GC logging
export JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC -XX:+PrintGCDetails"

# Reduce batch sizes
export BATCH_SIZE=5
```

### Performance Tuning

#### Consumer Optimization
```yaml
app:
  consumer:
    concurrent-consumers: 5      # More consumers for higher throughput
    max-concurrent-consumers: 10 # Allow scaling under load
    batch-size: 20              # Process in larger batches
    processing-timeout: 60000   # Increase timeout for large files
```

#### Database Optimization
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20     # Increase connection pool
      minimum-idle: 5           # Maintain minimum connections
      connection-timeout: 30000 # Connection timeout
  jpa:
    properties:
      hibernate:
        jdbc.batch_size: 25     # Batch database operations
        order_inserts: true     # Optimize insert order
        order_updates: true     # Optimize update order
```

## 📈 Scaling Strategies

### Vertical Scaling
- **CPU**: More cores for concurrent processing
- **Memory**: Higher heap for larger batches
- **Storage**: Faster I/O for file processing

### Horizontal Scaling
- **Instance Count**: Add more consumer instances
- **Consumer Threads**: Increase concurrent consumers per instance
- **Queue Partitioning**: Distribute load across multiple queues

### Auto-Scaling Triggers
- **CPU Utilization**: > 70%
- **Memory Utilization**: > 80%
- **Queue Depth**: > 50 messages
- **Processing Latency**: > 2 seconds average

---

**Related Documentation**:
- [Producer Service](./producer.md) - Generates orders for Consumer
- [Message Queues](./messaging.md) - ActiveMQ configuration
- [Database Schema](./database.md) - Data persistence layer
- [Monitoring Guide](./monitoring.md) - Observability setup 