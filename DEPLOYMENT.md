# Apache Camel Cluster Deployment Guide

## 🎉 **PROJECT STATUS: COMPLETE & READY FOR DEPLOYMENT**

All three applications have been successfully built and containerized:
- ✅ **Producer Application** - Generates orders and files
- ✅ **Consumer Application** - Processes orders and files with clustering
- ✅ **Coordinator Application** - Manages cluster coordination and reporting

## 🏗️ **Architecture Overview**

### Application Stack
```
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│   Producer      │  │   Consumer      │  │  Coordinator    │
│   (Port 8081)   │  │   (Port 8082)   │  │   (Port 8083)   │
│                 │  │                 │  │                 │
│ • Order Gen     │  │ • Order Proc    │  │ • Cluster Mon   │
│ • File Gen      │  │ • File Proc     │  │ • DB Reports    │
│ • REST API      │  │ • Database      │  │ • Analytics     │
└─────────────────┘  └─────────────────┘  └─────────────────┘
         │                     │                     │
         └─────────────────────┼─────────────────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│   ActiveMQ      │  │  PostgreSQL     │  │   Hazelcast     │
│   (Port 61616)  │  │   (Port 5432)   │  │   (Embedded)    │
│                 │  │                 │  │                 │
│ • Message Queue │  │ • Order Storage │  │ • Clustering    │
│ • Web Console   │  │ • Metrics       │  │ • Coordination  │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

### Cluster Features
- **Load Balancing**: Automatic distribution across consumer instances
- **Leader Election**: Coordinator runs master-only routes
- **Failover**: Automatic recovery when nodes fail
- **File Coordination**: Prevents duplicate file processing
- **Metrics Sharing**: Cluster-wide statistics aggregation

## 🚀 **Quick Start Deployment**

### Prerequisites
- Docker and Docker Compose installed
- At least 4GB RAM available
- Ports 8081-8083, 5432, 61616, 8161 available

### 1. Start Infrastructure
```bash
# Start PostgreSQL and ActiveMQ
docker-compose up -d postgres activemq

# Wait for services to be ready (30-60 seconds)
docker-compose logs -f postgres activemq
```

### 2. Deploy Applications
```bash
# Start all Camel applications
docker-compose up -d

# Check deployment status
docker-compose ps

# View application logs
docker-compose logs -f camel-producer camel-consumer camel-coordinator
```

### 3. Verify Deployment
```bash
# Run comprehensive tests
./test-cluster.sh

# Check individual application health
curl http://localhost:8081/api/producer/health
curl http://localhost:8082/api/consumer/health  
curl http://localhost:8083/api/coordinator/health
```

## 📊 **Application Endpoints**

### Producer Application (Port 8081)
- `GET /api/producer/status` - Application status and uptime
- `GET /api/producer/cluster` - Cluster membership information
- `GET /api/producer/metrics` - Order and file generation statistics
- `GET /api/producer/health` - Health check endpoint
- `GET /api/producer/routes` - Camel route information
- `POST /api/producer/generate` - Manual order generation trigger

### Consumer Application (Port 8082)
- `GET /api/consumer/status` - Processing status and metrics
- `GET /api/consumer/cluster` - Cluster coordination status
- `GET /api/consumer/metrics` - Local and cluster-wide metrics
- `GET /api/consumer/health` - Comprehensive health check
- `GET /api/consumer/routes` - Active Camel routes
- `GET /api/consumer/orders` - Query processed orders
- `GET /api/consumer/orders/stats` - Order statistics and analytics
- `POST /api/consumer/process` - Manual processing trigger
- `POST /api/consumer/reset` - Reset processing metrics

### Coordinator Application (Port 8083)
- `GET /api/coordinator/status` - Coordinator status and leadership
- `GET /api/coordinator/cluster` - Complete cluster overview
- `GET /api/coordinator/metrics` - Cluster-wide metrics and analytics
- `GET /api/coordinator/health` - Coordinator health check
- `GET /api/coordinator/routes` - Master route information
- `GET /api/coordinator/reports` - Generated reports list
- `GET /api/coordinator/reports/{id}` - Specific report details
- `POST /api/coordinator/reports/generate` - Manual report generation
- `POST /api/coordinator/cluster/rebalance` - Trigger cluster rebalancing

## 🔧 **Configuration Profiles**

### Docker Profile (Production)
```yaml
spring:
  profiles:
    active: docker
  datasource:
    url: jdbc:postgresql://postgres:5432/cameldb
  activemq:
    broker-url: tcp://activemq:61616
```

### Development Profile
```yaml
spring:
  profiles:
    active: dev
  datasource:
    url: jdbc:postgresql://localhost:5432/cameldb
  activemq:
    broker-url: tcp://localhost:61616
```

## 📈 **Monitoring & Management**

### ActiveMQ Web Console
- URL: http://localhost:8161
- Username: admin
- Password: admin
- Monitor queues, topics, and message flow

### Database Access
```bash
# Connect to PostgreSQL
docker exec -it camel-postgres psql -U cameluser -d cameldb

# View processed orders
SELECT * FROM processed_orders ORDER BY processed_at DESC LIMIT 10;

# Check cluster metrics
SELECT * FROM cluster_metrics ORDER BY timestamp DESC LIMIT 5;
```

### Application Logs
```bash
# View all application logs
docker-compose logs -f

# View specific application logs
docker-compose logs -f camel-producer
docker-compose logs -f camel-consumer
docker-compose logs -f camel-coordinator

# Follow logs with timestamps
docker-compose logs -f -t
```

## 🧪 **Testing Scenarios**

### 1. Load Distribution Test
```bash
# Scale consumer instances
docker-compose up -d --scale camel-consumer=3

# Monitor load distribution
curl http://localhost:8082/api/consumer/metrics
curl http://localhost:8083/api/coordinator/cluster
```

### 2. Failover Test
```bash
# Stop one consumer instance
docker-compose stop camel-consumer

# Verify continued processing
curl http://localhost:8081/api/producer/metrics
curl http://localhost:8083/api/coordinator/cluster
```

### 3. File Processing Test
```bash
# Check generated files
docker exec camel-producer ls -la /app/data/output/

# Monitor file processing
curl http://localhost:8082/api/consumer/metrics
```

### 4. Database Integration Test
```bash
# Check order processing
curl http://localhost:8082/api/consumer/orders/stats

# View recent orders
curl "http://localhost:8082/api/consumer/orders?limit=10"
```

## 🔄 **Scaling Operations**

### Horizontal Scaling
```bash
# Scale consumer instances for higher throughput
docker-compose up -d --scale camel-consumer=5

# Scale producer instances for higher load generation
docker-compose up -d --scale camel-producer=2

# Note: Coordinator should remain single instance (master-only routes)
```

### Resource Allocation
```yaml
# In docker-compose.yml
services:
  camel-consumer:
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: '0.5'
        reservations:
          memory: 512M
          cpus: '0.25'
```

## 🛠️ **Troubleshooting**

### Common Issues

#### 1. Application Won't Start
```bash
# Check container logs
docker-compose logs camel-producer

# Verify dependencies are running
docker-compose ps postgres activemq

# Check port conflicts
netstat -tulpn | grep -E '808[1-3]|5432|61616'
```

#### 2. Cluster Formation Issues
```bash
# Check Hazelcast cluster formation
curl http://localhost:8081/api/producer/cluster
curl http://localhost:8082/api/consumer/cluster
curl http://localhost:8083/api/coordinator/cluster

# Verify network connectivity between containers
docker network ls
docker network inspect camel_default
```

#### 3. Database Connection Issues
```bash
# Test database connectivity
docker exec camel-postgres pg_isready -U cameluser

# Check database logs
docker-compose logs postgres

# Verify database schema
docker exec -it camel-postgres psql -U cameluser -d cameldb -c "\dt"
```

#### 4. ActiveMQ Connection Issues
```bash
# Check ActiveMQ status
curl http://localhost:8161/admin/

# Verify queue creation
docker-compose logs activemq

# Test message flow
curl -X POST http://localhost:8081/api/producer/generate
```

### Performance Tuning

#### JVM Settings
```yaml
# In docker-compose.yml
environment:
  - JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

#### Database Optimization
```sql
-- Create indexes for better query performance
CREATE INDEX idx_processed_orders_customer ON processed_orders(customer_name);
CREATE INDEX idx_processed_orders_date ON processed_orders(processed_at);
CREATE INDEX idx_cluster_metrics_timestamp ON cluster_metrics(timestamp);
```

## 📋 **Maintenance Tasks**

### Regular Maintenance
```bash
# Clean up old Docker images
docker system prune -f

# Backup database
docker exec camel-postgres pg_dump -U cameluser cameldb > backup_$(date +%Y%m%d).sql

# Rotate application logs
docker-compose logs --no-color > logs_$(date +%Y%m%d).log
```

### Health Monitoring
```bash
# Create monitoring script
#!/bin/bash
for port in 8081 8082 8083; do
  status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$port/api/*/health)
  echo "Port $port: $status"
done
```

## 🎯 **Production Considerations**

### Security
- Use environment variables for sensitive configuration
- Implement proper authentication for management endpoints
- Use TLS/SSL for external communications
- Regular security updates for base images

### Monitoring
- Integrate with Prometheus/Grafana for metrics
- Set up log aggregation (ELK stack)
- Configure alerting for critical failures
- Monitor resource usage and performance

### Backup & Recovery
- Regular database backups
- Configuration backup procedures
- Disaster recovery testing
- Data retention policies

---

## 🎉 **Deployment Complete!**

Your Apache Camel Cluster is now ready for production use with:
- ✅ **3 Containerized Applications**
- ✅ **Complete Infrastructure Stack**
- ✅ **Comprehensive Monitoring**
- ✅ **Scalable Architecture**
- ✅ **Production-Ready Configuration**

For support and additional features, refer to the individual application README files and the implementation plan. 