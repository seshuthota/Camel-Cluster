# Common Library Documentation

## 🎯 Overview

The **Common Library** (`camel-cluster-common`) is the shared foundation for all microservices in the Apache Camel cluster. It provides shared configuration, utilities, constants, services, and data models that ensure consistency and reduce code duplication across the cluster.

## 📊 Library Architecture

```
┌─────────────────────────────────────────┐
│         camel-cluster-common            │
├─────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────────┐   │
│  │ Configuration│  │    Services     │   │
│  │              │  │                 │   │
│  │ • Cluster    │  │ • ClusterSvc    │   │
│  │ • Hazelcast  │  │ • ServiceDisc   │   │
│  │ • Database   │  │ • MetricsSvc    │   │
│  │ • Security   │  │ • EventSvc      │   │
│  └─────────────┘  └─────────────────┘   │
├─────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────────┐   │
│  │   Models     │  │   Utilities     │   │
│  │              │  │                 │   │
│  │ • DTOs       │  │ • Constants     │   │
│  │ • Entities   │  │ • Helpers       │   │
│  │ • Events     │  │ • Converters    │   │
│  │ • Reports    │  │ • Validators    │   │
│  └─────────────┘  └─────────────────┘   │
└─────────────────────────────────────────┘
```

## 📂 Package Structure

```
src/main/java/com/example/common/
├── config/
│   ├── ClusterConfig.java              # Hazelcast cluster configuration
│   ├── DatabaseConfig.java             # Database connection setup
│   ├── SecurityConfig.java             # Authentication & authorization
│   └── CamelConfig.java                # Camel context configuration
├── service/
│   ├── ClusterService.java             # Core cluster operations
│   ├── MetricsService.java             # Metrics collection & storage
│   ├── EventService.java               # Cluster event handling
│   └── HealthService.java              # Health check utilities
├── discovery/
│   ├── ServiceDiscovery.java           # Dynamic service discovery
│   ├── ConsulServiceRegistry.java      # Consul integration
│   └── KubernetesServiceRegistry.java  # K8s integration
├── model/
│   ├── dto/                           # Data Transfer Objects
│   ├── entity/                        # JPA Entities
│   ├── event/                         # Cluster Events
│   └── report/                        # Reporting Models
├── util/
│   ├── ClusterConstants.java          # Shared constants
│   ├── JsonUtils.java                 # JSON utilities
│   ├── TimeUtils.java                 # Time utilities
│   └── ValidationUtils.java           # Validation helpers
└── exception/
    ├── ClusterException.java          # Custom exceptions
    └── ServiceDiscoveryException.java # Discovery exceptions
```

## 🔧 Core Configuration Classes

### ClusterConfig
**Location**: `com.example.common.config.ClusterConfig`

**Purpose**: Configures Hazelcast clustering with dynamic discovery support

**Key Features**:
- **Multiple Discovery Modes**: TCP-IP, Consul, Kubernetes, Multicast
- **Environment-based Configuration**: Adapts to deployment environment
- **Failover Support**: Graceful degradation with fallback mechanisms

```java
@Configuration
@EnableConfigurationProperties
public class ClusterConfig {
    
    @Value("${hazelcast.port:5701}")
    private int hazelcastPort;
    
    @Value("${hazelcast.discovery.mode:tcp-ip}")
    private String discoveryMode;
    
    @Value("${hazelcast.members:localhost:5701}")
    private String membersList;
    
    @Bean
    @Primary
    public HazelcastInstance hazelcastInstance() {
        Config config = new Config();
        
        // Configure network settings
        configureNetworking(config);
        
        // Configure discovery
        configureDiscovery(config);
        
        // Configure data structures
        configureDataStructures(config);
        
        return Hazelcast.newHazelcastInstance(config);
    }
    
    private void configureDiscovery(Config config) {
        switch (discoveryMode.toLowerCase()) {
            case "tcp-ip":
                configureTcpIpDiscovery(config);
                break;
            case "consul":
                configureConsulDiscovery(config);
                break;
            case "kubernetes":
                configureKubernetesDiscovery(config);
                break;
            case "multicast":
                configureMulticastDiscovery(config);
                break;
        }
    }
}
```

### DatabaseConfig
**Location**: `com.example.common.config.DatabaseConfig`

**Purpose**: Configures database connections, connection pooling, and JPA settings

```java
@Configuration
@EnableJpaRepositories(basePackages = "com.example.*.repository")
@EnableTransactionManagement
public class DatabaseConfig {
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSource dataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }
    
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean factory = 
            new LocalContainerEntityManagerFactoryBean();
        
        factory.setDataSource(dataSource());
        factory.setPackagesToScan("com.example.common.model.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        
        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.hbm2ddl.auto", "update");
        jpaProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        jpaProperties.put("hibernate.show_sql", false);
        jpaProperties.put("hibernate.format_sql", true);
        factory.setJpaProperties(jpaProperties);
        
        return factory;
    }
}
```

### SecurityConfig
**Location**: `com.example.common.config.SecurityConfig`

**Purpose**: Configures authentication, authorization, and security policies

```java
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/*/status").permitAll()
                .requestMatchers("/api/coordinator/**").hasRole("COORDINATOR")
                .requestMatchers("/api/producer/**").hasRole("PRODUCER")
                .requestMatchers("/api/consumer/**").hasRole("CONSUMER")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt)
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
            
        return http.build();
    }
    
    @Bean
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation("${security.jwt.issuer-uri}");
    }
}
```

## 🔧 Core Service Classes

### ClusterService
**Location**: `com.example.common.service.ClusterService`

**Purpose**: Provides core cluster operations and state management

**Key Methods**:
```java
@Service
public class ClusterService {
    
    private final HazelcastInstance hazelcastInstance;
    private final MetricsService metricsService;
    
    public String getNodeId() {
        return hazelcastInstance.getCluster().getLocalMember().getUuid().toString();
    }
    
    public boolean isLeader() {
        Set<Member> members = hazelcastInstance.getCluster().getMembers();
        Member firstMember = members.iterator().next();
        return firstMember.localMember();
    }
    
    public void updateNodeStatus(String status, Map<String, Object> metadata) {
        IMap<String, NodeStatus> nodeStatusMap = 
            hazelcastInstance.getMap(ClusterConstants.NODE_STATUS_MAP);
            
        NodeStatus nodeStatus = new NodeStatus();
        nodeStatus.setNodeId(getNodeId());
        nodeStatus.setStatus(status);
        nodeStatus.setMetadata(metadata);
        nodeStatus.setLastUpdated(Instant.now());
        
        nodeStatusMap.put(getNodeId(), nodeStatus);
    }
    
    public ClusterState getClusterState() {
        IMap<String, NodeStatus> statusMap = 
            hazelcastInstance.getMap(ClusterConstants.NODE_STATUS_MAP);
            
        ClusterState state = new ClusterState();
        state.setTotalNodes(getClusterSize());
        state.setHealthyNodes(statusMap.values().stream()
            .mapToInt(status -> "RUNNING".equals(status.getStatus()) ? 1 : 0)
            .sum());
        state.setLeaderNode(findLeaderNode());
        
        return state;
    }
    
    public void storeMetric(String metricName, Object value) {
        metricsService.storeMetric(getNodeId(), metricName, value);
    }
    
    public void publishEvent(ClusterEvent event) {
        ITopic<ClusterEvent> eventTopic = 
            hazelcastInstance.getTopic(ClusterConstants.CLUSTER_EVENTS_TOPIC);
        eventTopic.publish(event);
    }
}
```

### MetricsService
**Location**: `com.example.common.service.MetricsService`

**Purpose**: Handles metrics collection, storage, and retrieval

```java
@Service
public class MetricsService {
    
    private final HazelcastInstance hazelcastInstance;
    
    public void storeMetric(String nodeId, String metricName, Object value) {
        IMap<String, Map<String, Object>> metricsMap = 
            hazelcastInstance.getMap(ClusterConstants.METRICS_MAP);
            
        Map<String, Object> nodeMetrics = metricsMap.computeIfAbsent(nodeId, 
            k -> new ConcurrentHashMap<>());
            
        nodeMetrics.put(metricName, value);
        nodeMetrics.put(metricName + "_timestamp", System.currentTimeMillis());
        
        metricsMap.put(nodeId, nodeMetrics);
    }
    
    public Optional<Object> getMetric(String nodeId, String metricName) {
        IMap<String, Map<String, Object>> metricsMap = 
            hazelcastInstance.getMap(ClusterConstants.METRICS_MAP);
            
        Map<String, Object> nodeMetrics = metricsMap.get(nodeId);
        return Optional.ofNullable(nodeMetrics != null ? 
            nodeMetrics.get(metricName) : null);
    }
    
    public Map<String, Object> getAllMetrics(String nodeId) {
        IMap<String, Map<String, Object>> metricsMap = 
            hazelcastInstance.getMap(ClusterConstants.METRICS_MAP);
            
        return metricsMap.getOrDefault(nodeId, Collections.emptyMap());
    }
    
    public ClusterMetrics getClusterMetrics() {
        IMap<String, Map<String, Object>> metricsMap = 
            hazelcastInstance.getMap(ClusterConstants.METRICS_MAP);
            
        ClusterMetrics clusterMetrics = new ClusterMetrics();
        
        // Aggregate metrics across all nodes
        long totalOrdersProcessed = 0;
        long totalFilesProcessed = 0;
        
        for (Map<String, Object> nodeMetrics : metricsMap.values()) {
            totalOrdersProcessed += getLongMetric(nodeMetrics, "orders_processed");
            totalFilesProcessed += getLongMetric(nodeMetrics, "files_processed");
        }
        
        clusterMetrics.setTotalOrdersProcessed(totalOrdersProcessed);
        clusterMetrics.setTotalFilesProcessed(totalFilesProcessed);
        
        return clusterMetrics;
    }
}
```

### ServiceDiscovery
**Location**: `com.example.common.discovery.ServiceDiscovery`

**Purpose**: Provides dynamic service discovery capabilities

```java
@Service
@ConditionalOnProperty(name = "service.discovery.enabled", havingValue = "true")
public class ServiceDiscovery {
    
    private final ServiceRegistry serviceRegistry;
    private final ScheduledExecutorService scheduler;
    
    @PostConstruct
    public void initialize() {
        // Register this service instance
        registerService();
        
        // Start periodic discovery
        startPeriodicDiscovery();
    }
    
    public void registerService() {
        ServiceInstance instance = ServiceInstance.builder()
            .id(getServiceInstanceId())
            .name(getServiceName())
            .address(getLocalAddress())
            .port(getServicePort())
            .metadata(getServiceMetadata())
            .build();
            
        serviceRegistry.register(instance);
    }
    
    public List<ServiceInstance> discoverServices(String serviceName) {
        return serviceRegistry.getInstances(serviceName);
    }
    
    public List<String> discoverClusterMembers() {
        List<ServiceInstance> instances = discoverServices("camel-cluster");
        
        return instances.stream()
            .map(instance -> instance.getAddress() + ":" + instance.getPort())
            .collect(Collectors.toList());
    }
    
    private void startPeriodicDiscovery() {
        scheduler.scheduleAtFixedRate(this::updateClusterMembership, 
            30, 30, TimeUnit.SECONDS);
    }
    
    private void updateClusterMembership() {
        try {
            List<String> discoveredMembers = discoverClusterMembers();
            updateHazelcastMembership(discoveredMembers);
        } catch (Exception e) {
            logger.warn("Failed to update cluster membership", e);
        }
    }
}
```

## 📦 Data Models

### DTOs (Data Transfer Objects)

#### OrderDTO
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    private String orderId;
    private String customerId;
    private String productId;
    private Integer quantity;
    private BigDecimal price;
    private LocalDateTime orderDate;
    private String status;
    private String priority;
    
    // Validation methods
    public boolean isValid() {
        return ValidationUtils.isNotBlank(orderId) &&
               ValidationUtils.isNotBlank(customerId) &&
               ValidationUtils.isNotBlank(productId) &&
               quantity != null && quantity > 0 &&
               price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }
}
```

#### ClusterStatusDTO
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterStatusDTO {
    private String nodeId;
    private String nodeType;
    private String status;
    private Instant lastSeen;
    private Map<String, Object> metrics;
    private Map<String, Object> metadata;
    
    public boolean isHealthy() {
        return "RUNNING".equals(status) && 
               Duration.between(lastSeen, Instant.now()).toSeconds() < 120;
    }
}
```

### Entities (JPA)

#### ClusterNode
```java
@Entity
@Table(name = "cluster_nodes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClusterNode {
    
    @Id
    private String nodeId;
    
    @Column(nullable = false)
    private String nodeType;
    
    @Column(nullable = false)
    private String status;
    
    @Column
    private String address;
    
    @Column
    private Integer port;
    
    @Column(name = "last_seen")
    private Instant lastSeen;
    
    @Column(name = "joined_at")
    private Instant joinedAt;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> metadata;
}
```

#### ProcessingMetrics
```java
@Entity
@Table(name = "processing_metrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "node_id", nullable = false)
    private String nodeId;
    
    @Column(name = "metric_name", nullable = false)
    private String metricName;
    
    @Column(name = "metric_value", nullable = false)
    private String metricValue;
    
    @Column(name = "metric_type")
    private String metricType;
    
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;
    
    @Column(name = "tags", columnDefinition = "jsonb")
    @Convert(converter = JsonConverter.class)
    private Map<String, String> tags;
}
```

### Events

#### ClusterEvent
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterEvent {
    
    public enum EventType {
        NODE_JOINED,
        NODE_LEFT,
        LEADERSHIP_CHANGE,
        LOAD_WARNING,
        SCALING_EVENT,
        HEALTH_WARNING,
        CONFIGURATION_CHANGE
    }
    
    private String eventId;
    private EventType type;
    private String sourceNodeId;
    private String targetNodeId;
    private Instant timestamp;
    private Map<String, Object> data;
    private String description;
    
    public static ClusterEvent nodeJoined(String nodeId, String nodeType) {
        return ClusterEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .type(EventType.NODE_JOINED)
            .sourceNodeId(nodeId)
            .timestamp(Instant.now())
            .description("Node " + nodeId + " of type " + nodeType + " joined the cluster")
            .data(Map.of("nodeType", nodeType))
            .build();
    }
    
    public static ClusterEvent leadershipChange(String oldLeader, String newLeader) {
        return ClusterEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .type(EventType.LEADERSHIP_CHANGE)
            .sourceNodeId(oldLeader)
            .targetNodeId(newLeader)
            .timestamp(Instant.now())
            .description("Leadership changed from " + oldLeader + " to " + newLeader)
            .build();
    }
    
    public String toJson() {
        return JsonUtils.toJson(this);
    }
    
    public static ClusterEvent fromJson(String json) {
        return JsonUtils.fromJson(json, ClusterEvent.class);
    }
}
```

## 🛠️ Utility Classes

### ClusterConstants
```java
public final class ClusterConstants {
    
    // Cluster Configuration
    public static final String CLUSTER_NAME = "camel-cluster";
    public static final String CLUSTER_GROUP_NAME = "camel-cluster-group";
    public static final String CLUSTER_PASSWORD = "camel-cluster-password";
    
    // Node Types
    public static final String NODE_TYPE_PRODUCER = "producer";
    public static final String NODE_TYPE_CONSUMER = "consumer";
    public static final String NODE_TYPE_COORDINATOR = "coordinator";
    
    // Route IDs
    public static final String PRODUCER_ORDER_ROUTE_ID = "producer-order-generator";
    public static final String PRODUCER_FILE_ROUTE_ID = "producer-file-generator";
    public static final String CONSUMER_ORDER_ROUTE_ID = "consumer-order-processor";
    public static final String CONSUMER_FILE_ROUTE_ID = "consumer-file-processor";
    public static final String CONSUMER_HEALTH_ROUTE_ID = "consumer-health-check";
    public static final String CONSUMER_CLUSTER_EVENTS_ROUTE_ID = "consumer-cluster-events";
    
    // Queue Names
    public static final String ORDERS_QUEUE = "orders";
    public static final String FILES_QUEUE = "files";
    public static final String EVENTS_QUEUE = "events";
    
    // Hazelcast Maps and Topics
    public static final String NODE_STATUS_MAP = "nodeStatus";
    public static final String METRICS_MAP = "clusterMetrics";
    public static final String CONFIGURATION_MAP = "clusterConfiguration";
    public static final String CLUSTER_EVENTS_TOPIC = "clusterEvents";
    public static final String LEADERSHIP_TOPIC = "leadershipEvents";
    
    // File Paths
    public static final String SHARED_FILE_PATH = "/shared";
    public static final String INPUT_FILE_PATH = "/shared/input";
    public static final String OUTPUT_FILE_PATH = "/shared/output";
    public static final String PROCESSED_FILE_PATH = "/shared/processed";
    
    // Timeouts and Intervals
    public static final long DEFAULT_TIMEOUT = 30000; // 30 seconds
    public static final long HEALTH_CHECK_INTERVAL = 30000; // 30 seconds
    public static final long METRICS_COLLECTION_INTERVAL = 60000; // 1 minute
    public static final long NODE_TIMEOUT = 120000; // 2 minutes
    
    // Status Values
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_STOPPED = "STOPPED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_STARTING = "STARTING";
    public static final String STATUS_STOPPING = "STOPPING";
    
    private ClusterConstants() {
        throw new UnsupportedOperationException("Utility class");
    }
}
```

### JsonUtils
```java
@Component
public class JsonUtils {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    
    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new ClusterException("Failed to serialize object to JSON", e);
        }
    }
    
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new ClusterException("Failed to deserialize JSON to object", e);
        }
    }
    
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new ClusterException("Failed to deserialize JSON to object", e);
        }
    }
    
    public static JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ClusterException("Failed to parse JSON", e);
        }
    }
}
```

### ValidationUtils
```java
public class ValidationUtils {
    
    public static boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }
    
    public static boolean isValidEmail(String email) {
        return isNotBlank(email) && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
    
    public static boolean isValidOrderId(String orderId) {
        return isNotBlank(orderId) && orderId.matches("^ORD-\\d+$");
    }
    
    public static boolean isValidNodeId(String nodeId) {
        return isNotBlank(nodeId) && nodeId.matches("^[a-zA-Z0-9-]+$");
    }
    
    public static void validateOrder(OrderDTO order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        
        if (!isValidOrderId(order.getOrderId())) {
            throw new IllegalArgumentException("Invalid order ID format");
        }
        
        if (!isNotBlank(order.getCustomerId())) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        
        if (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }
    
    public static void validateClusterNode(ClusterNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Cluster node cannot be null");
        }
        
        if (!isValidNodeId(node.getNodeId())) {
            throw new IllegalArgumentException("Invalid node ID format");
        }
        
        if (!isNotBlank(node.getNodeType())) {
            throw new IllegalArgumentException("Node type is required");
        }
        
        Set<String> validNodeTypes = Set.of(
            ClusterConstants.NODE_TYPE_PRODUCER,
            ClusterConstants.NODE_TYPE_CONSUMER,
            ClusterConstants.NODE_TYPE_COORDINATOR
        );
        
        if (!validNodeTypes.contains(node.getNodeType())) {
            throw new IllegalArgumentException("Invalid node type: " + node.getNodeType());
        }
    }
}
```

## 🎯 Exception Handling

### Custom Exceptions
```java
// Base cluster exception
public class ClusterException extends RuntimeException {
    public ClusterException(String message) {
        super(message);
    }
    
    public ClusterException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Service discovery specific exception
public class ServiceDiscoveryException extends ClusterException {
    public ServiceDiscoveryException(String message) {
        super(message);
    }
    
    public ServiceDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Leadership election exception
public class LeadershipException extends ClusterException {
    public LeadershipException(String message) {
        super(message);
    }
    
    public LeadershipException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

## 📋 Configuration Properties

### application.yml Template
```yaml
# Common configuration for all services
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  
  # Database configuration
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/camel_cluster}
    username: ${DATABASE_USERNAME:camel_user}
    password: ${DATABASE_PASSWORD:camel_pass}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: ${DATABASE_MAX_POOL_SIZE:20}
      minimum-idle: ${DATABASE_MIN_IDLE:5}
      connection-timeout: ${DATABASE_CONNECTION_TIMEOUT:30000}
      idle-timeout: ${DATABASE_IDLE_TIMEOUT:600000}
      max-lifetime: ${DATABASE_MAX_LIFETIME:1800000}
  
  # JPA configuration
  jpa:
    hibernate:
      ddl-auto: ${JPA_DDL_AUTO:update}
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        jdbc:
          batch_size: ${JPA_BATCH_SIZE:25}
        order_inserts: true
        order_updates: true
  
  # ActiveMQ configuration
  activemq:
    broker-url: ${ACTIVEMQ_BROKER_URL:tcp://localhost:61616}
    user: ${ACTIVEMQ_USER:admin}
    password: ${ACTIVEMQ_PASSWORD:admin}
    pool:
      enabled: true
      max-connections: ${ACTIVEMQ_MAX_CONNECTIONS:10}

# Cluster configuration
cluster:
  node:
    id: ${CLUSTER_NODE_ID:node-1}
    type: ${CLUSTER_NODE_TYPE:unknown}
  
# Hazelcast configuration
hazelcast:
  port: ${HAZELCAST_PORT:5701}
  discovery:
    mode: ${HAZELCAST_DISCOVERY_MODE:tcp-ip}
  members: ${HAZELCAST_MEMBERS:localhost:5701}
  
  # Kubernetes discovery
  kubernetes:
    enabled: ${HAZELCAST_KUBERNETES_ENABLED:false}
    service-name: ${HAZELCAST_K8S_SERVICE_NAME:camel-cluster-service}
    namespace: ${HAZELCAST_K8S_NAMESPACE:default}
  
  # Multicast discovery
  multicast:
    enabled: ${HAZELCAST_MULTICAST_ENABLED:false}
    group: ${HAZELCAST_MULTICAST_GROUP:224.2.2.3}
    port: ${HAZELCAST_MULTICAST_PORT:54327}

# Service discovery configuration
service:
  discovery:
    enabled: ${SERVICE_DISCOVERY_ENABLED:false}
    type: ${SERVICE_DISCOVERY_TYPE:consul}
    consul:
      url: ${SERVICE_DISCOVERY_CONSUL_URL:http://localhost:8500}
      health-check-interval: ${SERVICE_DISCOVERY_HEALTH_CHECK_INTERVAL:30s}
    eureka:
      url: ${SERVICE_DISCOVERY_EUREKA_URL:http://localhost:8761/eureka}

# Security configuration
security:
  jwt:
    issuer-uri: ${SECURITY_JWT_ISSUER_URI:http://localhost:8080/auth/realms/camel-cluster}
  oauth2:
    resource-id: ${SECURITY_OAUTH2_RESOURCE_ID:camel-cluster}

# Monitoring and metrics
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,env,camel
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: ${METRICS_PROMETHEUS_ENABLED:true}
```

## 🚀 Usage Examples

### Basic Cluster Service Usage
```java
@Service
public class MyService {
    
    @Autowired
    private ClusterService clusterService;
    
    public void doSomething() {
        // Check if this node is the leader
        if (clusterService.isLeader()) {
            // Execute leader-only logic
            executeLeaderTask();
        }
        
        // Update node status
        Map<String, Object> metadata = Map.of(
            "lastActivity", System.currentTimeMillis(),
            "processingCount", getProcessingCount()
        );
        clusterService.updateNodeStatus("RUNNING", metadata);
        
        // Store metrics
        clusterService.storeMetric("orders_processed", getOrdersProcessed());
        
        // Publish event
        ClusterEvent event = ClusterEvent.nodeStatusUpdate(
            clusterService.getNodeId(), "RUNNING");
        clusterService.publishEvent(event);
    }
}
```

### Service Discovery Integration
```java
@Service
public class MyServiceWithDiscovery {
    
    @Autowired
    private ServiceDiscovery serviceDiscovery;
    
    @PostConstruct
    public void initialize() {
        // Register this service
        serviceDiscovery.registerService();
        
        // Discover other services
        List<ServiceInstance> producers = 
            serviceDiscovery.discoverServices("producer");
        
        // Use discovered services
        for (ServiceInstance producer : producers) {
            String producerUrl = "http://" + producer.getAddress() + 
                                ":" + producer.getPort();
            // Make calls to producer
        }
    }
}
```

## 📊 Maven Configuration

### pom.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>com.example</groupId>
    <artifactId>camel-cluster-common</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    
    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <spring.boot.version>3.2.0</spring.boot.version>
        <camel.version>4.2.0</camel.version>
        <hazelcast.version>5.3.0</hazelcast.version>
    </properties>
    
    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
            <version>${spring.boot.version}</version>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
            <version>${spring.boot.version}</version>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
            <version>${spring.boot.version}</version>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
            <version>${spring.boot.version}</version>
        </dependency>
        
        <!-- Apache Camel -->
        <dependency>
            <groupId>org.apache.camel.springboot</groupId>
            <artifactId>camel-spring-boot-starter</artifactId>
            <version>${camel.version}</version>
        </dependency>
        
        <dependency>
            <groupId>org.apache.camel</groupId>
            <artifactId>camel-hazelcast</artifactId>
            <version>${camel.version}</version>
        </dependency>
        
        <!-- Hazelcast -->
        <dependency>
            <groupId>com.hazelcast</groupId>
            <artifactId>hazelcast-all</artifactId>
            <version>${hazelcast.version}</version>
        </dependency>
        
        <!-- Database -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        
        <!-- JSON Processing -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>
        
        <!-- Utilities -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

## 🔧 Build and Deployment

### Build Commands
```bash
# Build the common library
mvn clean compile

# Run tests
mvn test

# Package as JAR
mvn package

# Install to local repository
mvn install

# Deploy to remote repository
mvn deploy
```

### Usage in Other Modules
```xml
<!-- In producer/consumer/coordinator pom.xml -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>camel-cluster-common</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

**Related Documentation**:
- [Producer Service](./producer.md) - Uses common library for clustering
- [Consumer Service](./consumer.md) - Uses common library for processing
- [Coordinator Service](./coordinator.md) - Uses common library for management
- [Service Discovery](./service-discovery.md) - Dynamic discovery implementation 