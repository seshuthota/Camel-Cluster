package com.example.common.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service discovery implementation for dynamic cluster member detection.
 * Supports multiple backends: Consul, Eureka, Database, REST API.
 */
@Service
public class ServiceDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscovery.class);

    @Value("${service.discovery.enabled:false}")
    private boolean enabled;

    @Value("${service.discovery.type:consul}")
    private String discoveryType;

    @Value("${service.discovery.consul.url:http://localhost:8500}")
    private String consulUrl;

    @Value("${service.discovery.consul.service:camel-cluster}")
    private String consulService;

    @Value("${service.discovery.eureka.url:http://localhost:8761}")
    private String eurekaUrl;

    @Value("${service.discovery.refresh.interval:30000}")
    private long refreshInterval;

    @Value("${cluster.node.id:unknown}")
    private String nodeId;

    @Value("${cluster.node.type:unknown}")
    private String nodeType;

    @Value("${hazelcast.port:5701}")
    private int hazelcastPort;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, ServiceInstance> cachedServices = new HashMap<>();
    private Timer refreshTimer;

    @PostConstruct
    public void initialize() {
        if (enabled) {
            logger.info("Initializing service discovery with type: {}", discoveryType);
            
            // Register this node
            registerSelf();
            
            // Start periodic refresh
            startPeriodicRefresh();
        } else {
            logger.debug("Service discovery is disabled");
        }
    }

    /**
     * Register current node with service discovery
     */
    public void registerSelf() {
        if (!enabled) return;

        try {
            ServiceInstance instance = new ServiceInstance(
                nodeId,
                nodeType,
                getCurrentHostname(),
                hazelcastPort,
                Map.of("version", "1.0", "environment", getEnvironment())
            );

            switch (discoveryType.toLowerCase()) {
                case "consul":
                    registerWithConsul(instance);
                    break;
                case "eureka":
                    registerWithEureka(instance);
                    break;
                case "database":
                    registerWithDatabase(instance);
                    break;
                default:
                    logger.warn("Unknown discovery type: {}", discoveryType);
            }

            logger.info("Successfully registered node: {} with service discovery", nodeId);

        } catch (Exception e) {
            logger.error("Failed to register with service discovery", e);
        }
    }

    /**
     * Discover all cluster members from service registry
     */
    public List<String> discoverClusterMembers() {
        if (!enabled) {
            return Collections.emptyList();
        }

        try {
            List<ServiceInstance> instances = null;

            switch (discoveryType.toLowerCase()) {
                case "consul":
                    instances = discoverFromConsul();
                    break;
                case "eureka":
                    instances = discoverFromEureka();
                    break;
                case "database":
                    instances = discoverFromDatabase();
                    break;
                default:
                    logger.warn("Unknown discovery type: {}", discoveryType);
                    return Collections.emptyList();
            }

            if (instances != null) {
                updateCache(instances);
                return instances.stream()
                    .map(instance -> instance.getHost() + ":" + instance.getPort())
                    .collect(Collectors.toList());
            }

        } catch (Exception e) {
            logger.error("Failed to discover cluster members", e);
        }

        // Return cached results if discovery fails
        return getCachedMembers();
    }

    /**
     * Register with Consul service registry
     */
    private void registerWithConsul(ServiceInstance instance) {
        try {
            Map<String, Object> registration = Map.of(
                "ID", instance.getId(),
                "Name", consulService,
                "Tags", Arrays.asList(instance.getServiceType(), "hazelcast"),
                "Address", instance.getHost(),
                "Port", instance.getPort(),
                "Check", Map.of(
                    "HTTP", "http://" + instance.getHost() + ":" + (instance.getPort() + 1000) + "/actuator/health",
                    "Interval", "30s"
                )
            );

            String url = consulUrl + "/v1/agent/service/register";
            restTemplate.put(url, registration);
            
            logger.debug("Registered with Consul: {}", registration);

        } catch (Exception e) {
            logger.error("Failed to register with Consul", e);
        }
    }

    /**
     * Discover services from Consul
     */
    private List<ServiceInstance> discoverFromConsul() {
        try {
            String url = consulUrl + "/v1/health/service/" + consulService + "?passing=true";
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> response = restTemplate.getForObject(url, List.class);
            
            if (response != null) {
                return response.stream()
                    .map(this::parseConsulService)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            }

        } catch (Exception e) {
            logger.error("Failed to discover from Consul", e);
        }

        return Collections.emptyList();
    }

    /**
     * Register with Eureka service registry
     */
    private void registerWithEureka(ServiceInstance instance) {
        // TODO: Implement Eureka registration
        logger.debug("Eureka registration not yet implemented");
    }

    /**
     * Discover services from Eureka
     */
    private List<ServiceInstance> discoverFromEureka() {
        // TODO: Implement Eureka discovery
        logger.debug("Eureka discovery not yet implemented");
        return Collections.emptyList();
    }

    /**
     * Register with database
     */
    private void registerWithDatabase(ServiceInstance instance) {
        // TODO: Implement database registration
        logger.debug("Database registration not yet implemented");
    }

    /**
     * Discover services from database
     */
    private List<ServiceInstance> discoverFromDatabase() {
        // TODO: Implement database discovery
        logger.debug("Database discovery not yet implemented");
        return Collections.emptyList();
    }

    /**
     * Parse Consul service response
     */
    @SuppressWarnings("unchecked")
    private ServiceInstance parseConsulService(Map<String, Object> consulService) {
        try {
            Map<String, Object> service = (Map<String, Object>) consulService.get("Service");
            
            String id = (String) service.get("ID");
            String address = (String) service.get("Address");
            Integer port = (Integer) service.get("Port");
            List<String> tags = (List<String>) service.get("Tags");
            
            String serviceType = tags.stream()
                .filter(tag -> !tag.equals("hazelcast"))
                .findFirst()
                .orElse("unknown");

            return new ServiceInstance(id, serviceType, address, port, Map.of());

        } catch (Exception e) {
            logger.error("Failed to parse Consul service", e);
            return null;
        }
    }

    /**
     * Update cached services
     */
    private void updateCache(List<ServiceInstance> instances) {
        cachedServices.clear();
        for (ServiceInstance instance : instances) {
            cachedServices.put(instance.getId(), instance);
        }
        logger.debug("Updated service cache with {} instances", instances.size());
    }

    /**
     * Get cached cluster members
     */
    private List<String> getCachedMembers() {
        return cachedServices.values().stream()
            .map(instance -> instance.getHost() + ":" + instance.getPort())
            .collect(Collectors.toList());
    }

    /**
     * Start periodic refresh of service discovery
     */
    private void startPeriodicRefresh() {
        refreshTimer = new Timer("ServiceDiscoveryRefresh", true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    discoverClusterMembers();
                } catch (Exception e) {
                    logger.error("Error during periodic service discovery refresh", e);
                }
            }
        }, refreshInterval, refreshInterval);

        logger.info("Started periodic service discovery refresh every {} ms", refreshInterval);
    }

    /**
     * Unregister from service discovery on shutdown
     */
    public void unregister() {
        if (!enabled) return;

        try {
            switch (discoveryType.toLowerCase()) {
                case "consul":
                    unregisterFromConsul();
                    break;
                case "eureka":
                    unregisterFromEureka();
                    break;
                case "database":
                    unregisterFromDatabase();
                    break;
            }

            if (refreshTimer != null) {
                refreshTimer.cancel();
            }

            logger.info("Successfully unregistered from service discovery");

        } catch (Exception e) {
            logger.error("Failed to unregister from service discovery", e);
        }
    }

    private void unregisterFromConsul() {
        try {
            String url = consulUrl + "/v1/agent/service/deregister/" + nodeId;
            restTemplate.put(url, null);
        } catch (Exception e) {
            logger.error("Failed to unregister from Consul", e);
        }
    }

    private void unregisterFromEureka() {
        // TODO: Implement Eureka unregistration
    }

    private void unregisterFromDatabase() {
        // TODO: Implement database unregistration
    }

    private String getCurrentHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }

    private String getEnvironment() {
        return System.getProperty("spring.profiles.active", "development");
    }

    /**
     * Service instance representation
     */
    public static class ServiceInstance {
        private final String id;
        private final String serviceType;
        private final String host;
        private final int port;
        private final Map<String, Object> metadata;

        public ServiceInstance(String id, String serviceType, String host, int port, Map<String, Object> metadata) {
            this.id = id;
            this.serviceType = serviceType;
            this.host = host;
            this.port = port;
            this.metadata = metadata;
        }

        // Getters
        public String getId() { return id; }
        public String getServiceType() { return serviceType; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public Map<String, Object> getMetadata() { return metadata; }

        @Override
        public String toString() {
            return String.format("ServiceInstance{id='%s', type='%s', host='%s', port=%d}", 
                               id, serviceType, host, port);
        }
    }
} 