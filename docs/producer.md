# Producer Service Documentation

## 🎯 Overview

The **Producer Service** is responsible for generating orders and files within the Apache Camel cluster. It acts as the data source, creating synthetic workloads for testing and demonstration purposes.

## 📊 Service Architecture

```
┌─────────────────────────────────────────┐
│           Producer Service              │
├─────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────────┐   │
│  │   REST API  │  │  Camel Routes   │   │
│  │             │  │                 │   │
│  │ • Orders    │  │ • Order Gen     │   │
│  │ • Files     │  │ • File Gen      │   │
│  │ • Health    │  │ • Health Check  │   │
│  │ • Metrics   │  │ • Node Status   │   │
│  └─────────────┘  └─────────────────┘   │
├─────────────────────────────────────────┤
│           Infrastructure                │
│ • ActiveMQ Producer                     │
│ • File System Writer                    │
│ • Cluster Registration                  │
│ • Database Connection                   │
└─────────────────────────────────────────┘
```

## 🔧 Configuration

### Application Properties
```yaml
# Server Configuration
server:
  port: 8081

# Cluster Configuration
cluster:
  node:
    id: producer-1
    type: producer

# Producer-specific Settings
producer:
  order:
    interval: 2000  # Generate orders every 2 seconds
  file:
    interval: 10000 # Generate files every 10 seconds

# File Processing
shared:
  file:
    path: /shared/input
```

### Environment Variables
| Variable | Default | Description |
|----------|---------|-------------|
| `CLUSTER_NODE_ID` | `producer-1` | Unique node identifier |
| `CLUSTER_NODE_TYPE` | `producer` | Node type for clustering |
| `HAZELCAST_PORT` | `5701` | Hazelcast clustering port |
| `ORDER_INTERVAL` | `2000` | Order generation interval (ms) |
| `FILE_INTERVAL` | `10000` | File generation interval (ms) |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile |

## 🛣️ Camel Routes

### 1. Order Generation Route
**Route ID**: `order-generator`

```java
from("timer:orderGenerator?period=" + orderInterval)
    .routeId("order-generator")
    .log("🏭 Generating order from producer: " + nodeId)
    .process(exchange -> {
        OrderDTO order = orderGenerator.generateRandomOrder();
        exchange.getIn().setBody(order);
    })
    .marshal().json(JsonLibrary.Jackson)
    .to("activemq:queue:" + ClusterConstants.ORDERS_QUEUE)
    .log("✅ Order sent to queue: ${body}");
```

**Functionality**:
- **Trigger**: Timer-based (configurable interval)
- **Action**: Generate random orders and send to ActiveMQ
- **Output**: JSON orders to `orders` queue

### 2. File Generation Route
**Route ID**: `file-generator`

```java
from("timer:fileGenerator?period=" + fileInterval)
    .routeId("file-generator")
    .log("📄 Generating file from producer: " + nodeId)
    .process(exchange -> {
        String csvContent = fileGenerator.generateOrdersCsv();
        String filename = "orders_" + System.currentTimeMillis() + ".csv";
        exchange.getIn().setBody(csvContent);
        exchange.getIn().setHeader(Exchange.FILE_NAME, filename);
    })
    .to("file:" + sharedFilePath + "?fileExist=Append")
    .log("✅ File generated: ${header.CamelFileName}");
```

**Functionality**:
- **Trigger**: Timer-based (configurable interval)
- **Action**: Generate CSV files with order data
- **Output**: CSV files to shared directory

### 3. Health Check Route
**Route ID**: `producer-health-check`

```java
from("timer:producer-health?period=30000")
    .routeId("producer-health-check")
    .process(exchange -> {
        clusterService.updateNodeStatus("RUNNING", Map.of(
            "ordersGenerated", orderCount,
            "filesGenerated", fileCount,
            "lastActivity", System.currentTimeMillis()
        ));
    })
    .log("💓 Producer health updated");
```

**Functionality**:
- **Trigger**: Every 30 seconds
- **Action**: Update cluster status with health metrics
- **Output**: Status updates to cluster

## 🌐 REST API Endpoints

### Order Management

#### POST `/api/producer/orders`
**Description**: Manually trigger order generation

**Request Body**:
```json
{
  "productId": "PROD-123",
  "quantity": 5,
  "price": 29.99,
  "customerId": "CUST-456"
}
```

**Response**:
```json
{
  "orderId": "ORD-789",
  "status": "SENT_TO_QUEUE",
  "timestamp": "2024-01-15T10:30:00Z",
  "queueName": "orders"
}
```

#### GET `/api/producer/orders/generate/{count}`
**Description**: Generate multiple orders at once

**Parameters**:
- `count`: Number of orders to generate (1-100)

**Response**:
```json
{
  "ordersGenerated": 10,
  "queueName": "orders",
  "generatedAt": "2024-01-15T10:30:00Z"
}
```

### File Management

#### POST `/api/producer/files/generate`
**Description**: Manually trigger file generation

**Request Body** (Optional):
```json
{
  "filename": "custom_orders.csv",
  "recordCount": 1000,
  "includeHeaders": true
}
```

**Response**:
```json
{
  "filename": "orders_1642248600000.csv",
  "filepath": "/shared/input/orders_1642248600000.csv",
  "recordCount": 500,
  "fileSize": "45.2 KB",
  "generatedAt": "2024-01-15T10:30:00Z"
}
```

### System Information

#### GET `/api/producer/status`
**Description**: Get producer service status

**Response**:
```json
{
  "nodeId": "producer-1",
  "nodeType": "producer",
  "status": "RUNNING",
  "uptime": "PT2H30M45S",
  "statistics": {
    "ordersGenerated": 1250,
    "filesGenerated": 15,
    "averageOrdersPerMinute": 30,
    "lastOrderGenerated": "2024-01-15T10:29:55Z",
    "lastFileGenerated": "2024-01-15T10:25:00Z"
  },
  "cluster": {
    "isConnected": true,
    "clusterSize": 5,
    "isLeader": false
  }
}
```

#### GET `/api/producer/health`
**Description**: Service health check

**Response**:
```json
{
  "status": "UP",
  "components": {
    "camelContext": {
      "status": "UP",
      "details": {
        "contextStatus": "Started",
        "routeCount": 3,
        "uptime": "PT2H30M45S"
      }
    },
    "database": {
      "status": "UP",
      "details": {
        "connectionPool": "Healthy",
        "activeConnections": 2
      }
    },
    "activemq": {
      "status": "UP",
      "details": {
        "brokerUrl": "tcp://activemq:61616",
        "connectionStatus": "Connected"
      }
    },
    "cluster": {
      "status": "UP",
      "details": {
        "hazelcastStatus": "Connected",
        "clusterSize": 5
      }
    }
  }
}
```

#### GET `/api/producer/metrics`
**Description**: Detailed service metrics

**Response**:
```json
{
  "producer": {
    "ordersGenerated": 1250,
    "filesGenerated": 15,
    "orderGenerationRate": 30.5,
    "fileGenerationRate": 0.5,
    "lastActivity": "2024-01-15T10:29:55Z"
  },
  "jvm": {
    "memoryUsed": "256 MB",
    "memoryMax": "512 MB",
    "cpuUsage": "15.3%",
    "gcCollections": 45,
    "threadsActive": 12
  },
  "routes": {
    "order-generator": {
      "status": "Started",
      "exchangesCompleted": 1250,
      "exchangesFailed": 0,
      "averageProcessingTime": "25ms"
    },
    "file-generator": {
      "status": "Started", 
      "exchangesCompleted": 15,
      "exchangesFailed": 0,
      "averageProcessingTime": "150ms"
    }
  }
}
```

## 📦 Data Models

### OrderDTO
```java
public class OrderDTO {
    private String orderId;
    private String customerId;
    private String productId;
    private Integer quantity;
    private BigDecimal price;
    private LocalDateTime orderDate;
    private String status;
    private String priority;
}
```

### Generated Order Format
```json
{
  "orderId": "ORD-1642248600123",
  "customerId": "CUST-789",
  "productId": "PROD-456",
  "quantity": 3,
  "price": 29.99,
  "orderDate": "2024-01-15T10:30:00Z",
  "status": "PENDING",
  "priority": "NORMAL"
}
```

### Generated CSV Format
```csv
OrderId,CustomerId,ProductId,Quantity,Price,OrderDate,Status,Priority
ORD-1642248600123,CUST-789,PROD-456,3,29.99,2024-01-15T10:30:00Z,PENDING,NORMAL
ORD-1642248600124,CUST-790,PROD-457,1,19.99,2024-01-15T10:30:01Z,PENDING,HIGH
```

## 🔧 Service Components

### OrderGenerator
**Location**: `com.example.producer.service.OrderGenerator`

**Responsibilities**:
- Generate random order data
- Ensure unique order IDs
- Simulate realistic order patterns

**Key Methods**:
```java
public OrderDTO generateRandomOrder()
public List<OrderDTO> generateOrders(int count)
public OrderDTO generateOrderWithTemplate(OrderTemplate template)
```

### FileGenerator
**Location**: `com.example.producer.service.FileGenerator`

**Responsibilities**:
- Generate CSV files with order data
- Handle file naming conventions
- Manage file output formatting

**Key Methods**:
```java
public String generateOrdersCsv(int recordCount)
public void writeOrdersToFile(String filename, List<OrderDTO> orders)
public FileMetadata getFileInfo(String filename)
```

## 🏗️ Deployment Configuration

### Docker Configuration
```dockerfile
FROM openjdk:17-jre-slim

COPY target/camel-producer-*.jar app.jar

EXPOSE 8081 5701

ENV SPRING_PROFILES_ACTIVE=docker
ENV CLUSTER_NODE_TYPE=producer

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Docker Compose
```yaml
producer:
  build: 
    context: .
    dockerfile: camel-producer/Dockerfile
  ports:
    - "8081:8081"
  environment:
    - CLUSTER_NODE_ID=producer-1
    - CLUSTER_NODE_TYPE=producer
    - HAZELCAST_PORT=5701
    - SPRING_PROFILES_ACTIVE=docker
  volumes:
    - shared_files:/app/data
```

### Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: camel-producer
spec:
  replicas: 1
  selector:
    matchLabels:
      app: camel-cluster
      component: producer
  template:
    spec:
      containers:
      - name: producer
        image: camel-producer:latest
        ports:
        - containerPort: 8081
        - containerPort: 5701
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

## 📊 Monitoring & Observability

### Health Checks
```bash
# Basic health check
curl http://localhost:8081/actuator/health

# Detailed status
curl http://localhost:8081/api/producer/status

# Metrics
curl http://localhost:8081/api/producer/metrics
```

### Key Metrics to Monitor
- **Order generation rate**: Orders per minute
- **File generation rate**: Files per hour
- **Queue depth**: Messages waiting in ActiveMQ
- **Memory usage**: JVM heap utilization
- **CPU usage**: System resource consumption
- **Route failures**: Failed exchanges per route

### Logging Configuration
```yaml
logging:
  level:
    com.example.producer: INFO
    org.apache.camel: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [producer-1] %logger{36} - %msg%n"
```

## 🔧 Configuration Profiles

### Development Profile (`dev`)
```yaml
producer:
  order:
    interval: 5000  # Slower generation for development
  file:
    interval: 30000

shared:
  file:
    path: ./shared/input
```

### Docker Profile (`docker`)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/camel_cluster
  activemq:
    broker-url: tcp://activemq:61616

shared:
  file:
    path: /app/shared/input
```

### Production Profile (`prod`)
```yaml
producer:
  order:
    interval: ${ORDER_INTERVAL:1000}  # Faster generation
  file:
    interval: ${FILE_INTERVAL:5000}

logging:
  level:
    com.example.producer: INFO
    root: WARN
```

## 🚨 Troubleshooting

### Common Issues

#### 1. Orders Not Being Generated
**Symptoms**: No orders appearing in ActiveMQ queue

**Causes & Solutions**:
```bash
# Check route status
curl http://localhost:8081/api/producer/status

# Verify ActiveMQ connection
curl http://localhost:8081/actuator/health

# Check logs
docker logs producer-container
```

#### 2. File Generation Failures
**Symptoms**: No CSV files in shared directory

**Causes & Solutions**:
```bash
# Check file permissions
ls -la /shared/input/

# Verify shared volume mount
docker inspect producer-container | grep Mounts

# Check disk space
df -h /shared/
```

#### 3. High Memory Usage
**Symptoms**: Service becomes unresponsive

**Solutions**:
```bash
# Increase JVM heap size
export JAVA_OPTS="-Xms512m -Xmx1024m"

# Enable GC logging
export JAVA_OPTS="$JAVA_OPTS -XX:+PrintGC -XX:+PrintGCDetails"

# Monitor memory usage
curl http://localhost:8081/api/producer/metrics | jq '.jvm'
```

### Performance Tuning

#### Order Generation Optimization
```yaml
producer:
  order:
    interval: 1000        # Reduce interval for higher throughput
    batch-size: 10        # Generate multiple orders per cycle
    async-processing: true # Enable async processing
```

#### File Generation Optimization
```yaml
producer:
  file:
    buffer-size: 8192     # Increase buffer size
    compression: true     # Enable compression
    async-write: true     # Async file operations
```

## 📈 Scaling Considerations

### Horizontal Scaling
- **Single Instance**: Recommended for data consistency
- **Multiple Instances**: Possible with coordination logic
- **Load Distribution**: Use different generation patterns per instance

### Vertical Scaling
- **CPU**: More cores improve concurrent processing
- **Memory**: Higher heap for larger batch operations
- **Storage**: Fast SSD for file generation performance

---

**Related Documentation**:
- [Consumer Service](./consumer.md) - Processes orders from Producer
- [Message Queues](./messaging.md) - ActiveMQ configuration
- [Clustering](./clustering.md) - Hazelcast integration 