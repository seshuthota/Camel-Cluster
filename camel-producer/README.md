# Camel Cluster Producer

This is the producer application for the Apache Camel cluster setup. It generates orders and files periodically and sends them to ActiveMQ queues for processing by consumer applications.

## Features

- **Automatic Order Generation**: Creates random orders every 2 seconds
- **File Generation**: Creates CSV files with multiple orders every 10 seconds
- **ActiveMQ Integration**: Publishes orders to `orders` queue
- **Cluster Awareness**: Integrates with Hazelcast cluster services
- **REST API**: Provides status, metrics, and control endpoints
- **Health Monitoring**: Built-in health checks and metrics collection

## REST Endpoints

### Status & Monitoring
- `GET /api/producer/status` - Producer application status
- `GET /api/producer/health` - Health check endpoint
- `GET /api/producer/cluster` - Cluster information
- `GET /api/producer/metrics` - Generation metrics and statistics
- `GET /api/producer/routes` - Camel route information

### Control
- `POST /api/producer/generate` - Manually trigger order generation

## Configuration

### Application Properties
Key configuration in `application.yml`:

```yaml
# Producer timing
producer:
  order:
    interval: 2000  # Order generation interval (ms)
  file:
    interval: 10000 # File generation interval (ms)

# Cluster node identity
cluster:
  node:
    id: producer-1
    type: producer

# File output path
shared:
  file:
    path: /shared/input
```

### Profiles
- `default` - Local development
- `dev` - Development environment
- `docker` - Docker container deployment
- `prod` - Production deployment

## Running the Application

### Local Development
```bash
mvn spring-boot:run
```

### With Profile
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Docker
```bash
docker build -t camel-producer .
docker run -p 8081:8081 camel-producer
```

## Generated Data

### Order Structure
```json
{
  "orderId": "uuid",
  "customerName": "Customer Name",
  "productName": "Product Name",
  "quantity": 5,
  "price": 199.99,
  "status": "PENDING",
  "createdAt": "2023-12-01T10:30:00",
  "generatedBy": "producer-1"
}
```

### Generated Files
- Location: `/shared/input/`
- Format: `orders-YYYYMMDD-HHMMSS.csv`
- Content: CSV format with 5-15 orders per file

## Dependencies

- Spring Boot 3.x
- Apache Camel 4.x
- Hazelcast 5.x (cluster coordination)
- ActiveMQ (message broker)
- PostgreSQL (shared database)

## Port Configuration

- Application: `8081`
- Hazelcast: `5701`

## Cluster Integration

The producer integrates with the cluster through:
- **Hazelcast**: Distributed coordination and state sharing
- **ActiveMQ**: Message publishing to shared queues
- **Shared Database**: Cluster-wide data persistence
- **File System**: Shared directory for file processing

## Monitoring

Monitor the producer through:
- **Logs**: Detailed logging with node identification
- **REST Endpoints**: Status and metrics APIs
- **Actuator**: Spring Boot management endpoints
- **Cluster Status**: Real-time cluster membership info 