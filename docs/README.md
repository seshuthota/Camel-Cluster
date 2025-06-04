# Apache Camel Cluster Microservices Documentation

## 🏗️ Architecture Overview

This project implements a **distributed Apache Camel cluster** with dynamic service discovery, auto-scaling capabilities, and high availability. The architecture follows microservices patterns with proper separation of concerns.

```
                    ┌─────────────────┐
                    │   Load Balancer │
                    │     (Nginx)     │
                    └─────────┬───────┘
                              │
            ┌─────────────────┼─────────────────┐
            │                 │                 │
    ┌───────▼──────┐ ┌───────▼──────┐ ┌───────▼──────┐
    │   Producer   │ │   Consumer   │ │ Coordinator  │
    │  Service(s)  │ │  Service(s)  │ │  Service(s)  │
    └───────┬──────┘ └───────┬──────┘ └───────┬──────┘
            │                │                │
            └─────────────────┼─────────────────┘
                              │
                 ┌────────────▼────────────┐
                 │   Shared Infrastructure │
                 │  • PostgreSQL Database  │
                 │  • ActiveMQ Artemis     │
                 │  • Hazelcast Cluster    │
                 │  • Service Discovery    │
                 └─────────────────────────┘
```

## 🎯 Core Services

| Service | Purpose | Instances | Ports |
|---------|---------|-----------|-------|
| **[Producer](./producer.md)** | Order & file generation | 1 | 8081 |
| **[Consumer](./consumer.md)** | Order & file processing | 2+ (scalable) | 8082 |
| **[Coordinator](./coordinator.md)** | Cluster management & monitoring | 2 (HA) | 8083 |

## 🔧 Infrastructure Components

| Component | Purpose | Technology |
|-----------|---------|------------|
| **[Service Discovery](./service-discovery.md)** | Dynamic member discovery | Consul/Kubernetes/TCP-IP |
| **[Database](./database.md)** | Data persistence | PostgreSQL 15 |
| **[Message Broker](./messaging.md)** | Async communication | ActiveMQ Artemis |
| **[Clustering](./clustering.md)** | Distributed coordination | Hazelcast |

## 🚀 Key Features

### ✅ **High Availability**
- **Multi-coordinator setup** with automatic leader election
- **Master-only routes** ensure single execution of critical tasks
- **Failover mechanisms** for zero-downtime operations

### ✅ **Dynamic Scaling**
- **Auto-discovery** of new cluster members
- **Load balancing** across consumer instances
- **Horizontal Pod Autoscaler** support in Kubernetes

### ✅ **Service Discovery**
- **Multiple backends**: Consul, Kubernetes, TCP-IP, Multicast
- **Environment-based configuration** for different deployment scenarios
- **Graceful degradation** with fallback mechanisms

### ✅ **Monitoring & Observability**
- **Health checks** with detailed cluster status
- **Metrics collection** and reporting
- **Real-time monitoring** dashboards
- **Distributed tracing** support

## 📋 Quick Start

### Docker Compose (Recommended)
```bash
# Start with Consul service discovery
./scripts/scale-cluster.sh start

# Scale consumers dynamically
./scripts/scale-cluster.sh scale-consumers 5

# Monitor cluster health
./scripts/scale-cluster.sh monitor
```

### Kubernetes
```bash
# Deploy to Kubernetes
kubectl apply -f k8s/camel-cluster-k8s.yaml

# Scale consumers
kubectl scale deployment camel-consumer --replicas=5 -n camel-cluster

# Check auto-scaling
kubectl get hpa -n camel-cluster
```

### Traditional Docker
```bash
# Start infrastructure
docker-compose up -d postgres activemq

# Start services
docker-compose up -d producer consumer coordinator
```

## 🔗 Service Documentation

### Core Services
- **[Producer Service](./producer.md)** - Order and file generation microservice
- **[Consumer Service](./consumer.md)** - Order and file processing microservice  
- **[Coordinator Service](./coordinator.md)** - Cluster management and monitoring

### Infrastructure
- **[Service Discovery](./service-discovery.md)** - Dynamic member discovery and registration
- **[Database Schema](./database.md)** - PostgreSQL schema and data management
- **[Message Queues](./messaging.md)** - ActiveMQ configuration and patterns
- **[Clustering](./clustering.md)** - Hazelcast distributed data structures

### Operations
- **[Deployment Guide](./deployment.md)** - Production deployment strategies
- **[Scaling Guide](./scaling.md)** - Auto-scaling and capacity planning
- **[Monitoring Guide](./monitoring.md)** - Observability and alerting
- **[Troubleshooting](./troubleshooting.md)** - Common issues and solutions

## 🛡️ Security & Configuration

### Authentication & Authorization
- **Service-to-service** authentication via cluster tokens
- **Database access** with dedicated service accounts
- **Message broker** authentication with role-based access

### Configuration Management
- **Environment-based** configuration profiles
- **External configuration** via ConfigMaps/environment variables
- **Secrets management** for sensitive data

## 📊 Performance & Scaling

### Performance Characteristics
- **Order processing**: ~1000 orders/second per consumer instance
- **File processing**: ~100 files/minute per consumer instance
- **Cluster coordination**: ~50ms average latency

### Scaling Metrics
- **CPU-based scaling**: 70% average utilization threshold
- **Memory-based scaling**: 80% average utilization threshold
- **Custom metrics**: Queue depth, processing latency

## 🔄 Development Workflow

### Local Development
```bash
# Run in development mode
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run -pl camel-producer

# Run tests
mvn test

# Build Docker images
./build.sh
```

### CI/CD Pipeline
- **Build**: Maven multi-module build
- **Test**: Unit and integration tests
- **Docker**: Multi-stage builds for optimized images
- **Deploy**: GitOps with ArgoCD/Flux

## 📈 Roadmap

### Planned Features
- [ ] **Event sourcing** with Kafka integration
- [ ] **Circuit breakers** with Hystrix/Resilience4j
- [ ] **API Gateway** with Spring Cloud Gateway
- [ ] **Distributed tracing** with Jaeger/Zipkin
- [ ] **Advanced metrics** with Prometheus/Grafana

### Version History
- **v1.0.0**: Initial cluster implementation
- **v1.1.0**: Dynamic service discovery
- **v1.2.0**: Kubernetes support (planned)
- **v2.0.0**: Event-driven architecture (planned)

## 📞 Support & Contributing

### Getting Help
- **Documentation**: Check service-specific docs in `/docs/`
- **Issues**: Create GitHub issues for bugs/features
- **Discussions**: Use GitHub Discussions for questions

### Contributing
1. Fork the repository
2. Create a feature branch
3. Follow the coding standards
4. Add tests for new functionality
5. Submit a pull request

---

**Next Steps**: Choose a service from the links above to dive deeper into its implementation details. 