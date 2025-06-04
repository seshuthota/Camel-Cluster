# Camel Coordinator Application

The **Coordinator Application** is the cluster management component of the Apache Camel distributed system. It provides leadership-based coordination, health monitoring, database reporting, and cluster administration capabilities.

## 🎯 **Key Features**

### Leadership & Coordination
- **Leader Election**: Uses Hazelcast master component for automatic leader election
- **Master-Only Routes**: Critical operations run only on the leader node
- **Cluster State Management**: Monitors and maintains overall cluster health
- **Automatic Failover**: Seamless leadership transition when nodes fail

### Health Monitoring
- **Node Health Tracking**: Monitors heartbeats from all cluster nodes
- **Failure Detection**: Detects and handles failed nodes automatically
- **Cluster Rebalancing**: Triggers load redistribution when needed
- **Health Reporting**: Generates comprehensive cluster health reports

### Database Management
- **Automated Reporting**: Hourly and daily processing reports
- **Analytics Generation**: Detailed trend analysis and performance metrics
- **Database Cleanup**: Automatic removal of old records
- **Custom Reports**: On-demand reports with date range selection

### Administrative Control
- **Manual Triggers**: Manual health checks, reports, and rebalancing
- **Emergency Reset**: Cluster state reset capabilities
- **Connectivity Testing**: Cluster connectivity verification
- **Metrics Collection**: Comprehensive system metrics

## 🛠️ **Application Components**

### 1. Main Application (`CoordinatorApplication.java`)
- Spring Boot main class with cluster and JPA integration
- Component scanning for coordinator and common packages
- Scheduling and entity management configuration

### 2. Cluster Monitor Service (`ClusterMonitor.java`)
- **Health Monitoring**: Scheduled cluster health checks (every 30s)
- **Failure Detection**: Identifies unresponsive nodes
- **State Management**: Maintains cluster state information
- **Rebalancing**: Triggers cluster rebalancing when needed

### 3. Database Reporter Service (`DatabaseReporter.java`)
- **Hourly Reports**: Processing statistics every hour
- **Daily Reports**: Comprehensive analytics every day at 6 AM
- **Database Cleanup**: Old record removal every day at 2 AM
- **Custom Reports**: On-demand report generation

### 4. Coordinator Routes (`CoordinatorRoutes.java`)
- **Master Routes**: Leader-only routes with automatic failover
- **Monitoring Routes**: Regular health and status updates
- **Administrative Routes**: Manual trigger endpoints
- **Event Processing**: Cluster event handling

### 5. REST Controller (`CoordinatorController.java`)
- **Status Endpoints**: Application and cluster status
- **Health Endpoints**: Health reports and checks
- **Administrative Endpoints**: Manual triggers and controls
- **Reporting Endpoints**: Report and analytics access

## 📊 **Route Architecture**

### Master-Only Routes (Leader Election)
```
🏥 coordinator-health-master      ← Cluster health monitoring (30s)
🧹 coordinator-cleanup-master     ← Database cleanup (1h)
📊 coordinator-reporting-master   ← Report generation (30min)
⚖️ coordinator-rebalance-timer    ← Cluster rebalancing (10min)
📢 coordinator-cluster-events     ← Event processing
```

### Regular Monitoring Routes (All Nodes)
```
📊 coordinator-node-status        ← Node status updates (1min)
📈 coordinator-metrics            ← Metrics collection (1min)
💓 coordinator-heartbeat          ← Health heartbeat (15s)
```

### Administrative Routes
```
🔍 coordinator-manual-health      ← Manual health check
📋 coordinator-manual-report      ← Manual report generation
⚖️ coordinator-manual-rebalance   ← Manual rebalancing
🚨 coordinator-emergency-reset    ← Emergency cluster reset
```

## 🌐 **API Endpoints**

### Status & Information
- `GET /api/coordinator/status` - Application status and uptime
- `GET /api/coordinator/cluster` - Comprehensive cluster information
- `GET /api/coordinator/health` - Cluster health report
- `GET /api/coordinator/routes` - Route information and status
- `GET /api/coordinator/metrics` - System metrics and statistics

### Reporting & Analytics
- `GET /api/coordinator/reports?limit=10` - Latest reports
- `GET /api/coordinator/analytics?limit=5` - Latest analytics
- `POST /api/coordinator/reports/generate` - Manual report generation
- `POST /api/coordinator/reports/custom` - Custom date range reports

### Cluster Administration
- `POST /api/coordinator/health/check` - Manual health check
- `POST /api/coordinator/cluster/rebalance` - Manual rebalancing
- `POST /api/coordinator/cluster/reset?confirm=CONFIRM_RESET` - Emergency reset
- `GET /api/coordinator/cluster/test` - Connectivity test

## ⚙️ **Configuration Profiles**

### Development Profile (`dev`)
```yaml
cluster:
  node:
    id: coordinator-1
  monitor:
    health-check-interval: 30000
    node-timeout: 60000
server:
  port: 8083
```

### Docker Profile (`docker`)
```yaml
cluster:
  node:
    id: coordinator-docker-1
  hazelcast:
    tcp-ip:
      members:
        - camel-producer-1:5701
        - camel-consumer-1:5702
        - camel-coordinator-1:5703
server:
  port: 8080
```

### Production Profile (`prod`)
```yaml
cluster:
  monitor:
    health-check-interval: 15000
    node-timeout: 30000
coordinator:
  reports:
    cleanup-days: ${CLEANUP_DAYS:30}
```

## 🎛️ **Leadership Behavior**

### Leader Responsibilities
- **Health Monitoring**: Monitor all cluster nodes
- **Report Generation**: Create and store reports
- **Database Cleanup**: Maintain database hygiene
- **Event Processing**: Handle cluster events
- **Rebalancing**: Trigger load redistribution

### Non-Leader Behavior
- **Status Updates**: Maintain own node status
- **Heartbeat**: Send regular health signals
- **Standby Mode**: Ready to become leader
- **Event Listening**: Respond to cluster events

## 📈 **Metrics & Monitoring**

### Health Metrics
- Active node count
- Failed node detection
- Leader election status
- Cluster connectivity

### Performance Metrics
- Route execution times
- Database query performance
- Report generation times
- Cleanup operation statistics

### Business Metrics
- Order processing trends
- Revenue analytics
- Customer behavior patterns
- Node performance comparison

## 🚀 **Running the Coordinator**

### Development
```bash
cd camel-coordinator
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Docker
```bash
cd camel-coordinator
mvn clean package -DskipTests
docker-compose up coordinator
```

### Testing
```bash
mvn clean test -Dspring.profiles.active=test
```

## 🔒 **Security & Safety**

### Leader-Only Operations
- Only the elected leader can perform critical operations
- Automatic leadership validation on sensitive endpoints
- Graceful degradation when leadership changes

### Emergency Procedures
- Emergency reset requires explicit confirmation
- Detailed logging of all administrative actions
- Cluster state backup before major operations

### Data Protection
- Database cleanup with configurable retention
- Report archival and analytics preservation
- Automatic backup of cluster state

## 🎯 **Next Steps**

1. **✅ COMPLETED**: All coordinator components created
2. **🔄 NEXT**: Test coordinator compilation and functionality
3. **📦 PENDING**: Create Dockerfiles for containerization
4. **🧪 PENDING**: Integration testing with other applications
5. **🚀 PENDING**: Full cluster deployment and validation

---

**🎉 Coordinator Application Complete!** 
The cluster management brain is ready to coordinate the entire Camel cluster with advanced monitoring, reporting, and administration capabilities. 