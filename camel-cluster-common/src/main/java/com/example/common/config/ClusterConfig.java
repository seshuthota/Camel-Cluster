package com.example.common.config;

import com.example.common.util.ClusterConstants;
import com.example.common.discovery.ServiceDiscovery;
import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dynamic cluster configuration for Hazelcast.
 * Supports multiple discovery mechanisms for different deployment scenarios.
 */
@Configuration
public class ClusterConfig {

    private static final Logger logger = LoggerFactory.getLogger(ClusterConfig.class);

    @Value("${cluster.node.id:unknown}")
    private String nodeId;

    @Value("${cluster.node.type:unknown}")
    private String nodeType;

    @Value("${hazelcast.port:5701}")
    private int hazelcastPort;

    @Value("${hazelcast.discovery.mode:tcp-ip}")
    private String discoveryMode;

    // Dynamic member discovery
    @Value("${hazelcast.members:}")
    private String membersList;

    @Value("${hazelcast.kubernetes.enabled:false}")
    private boolean kubernetesEnabled;

    @Value("${hazelcast.kubernetes.service-name:}")
    private String kubernetesServiceName;

    @Value("${hazelcast.kubernetes.namespace:default}")
    private String kubernetesNamespace;

    @Value("${hazelcast.multicast.enabled:false}")
    private boolean multicastEnabled;

    @Value("${hazelcast.multicast.group:224.2.2.3}")
    private String multicastGroup;

    @Value("${hazelcast.multicast.port:54327}")
    private int multicastPort;

    @Autowired
    private ServiceDiscovery serviceDiscovery;

    /**
     * Configure Hazelcast instance with dynamic discovery
     */
    @Bean
    public HazelcastInstance hazelcastInstance() {
        Config config = new Config();
        
        // Cluster configuration
        config.setClusterName(ClusterConstants.CLUSTER_NAME);
        config.setInstanceName(nodeId + "-" + nodeType);

        // Network configuration
        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(hazelcastPort);
        networkConfig.setPortAutoIncrement(true);
        networkConfig.setPortCount(100); // Allow port range for scaling

        // Configure discovery based on deployment mode
        configureDiscovery(networkConfig);

        // Map configurations for better performance
        configureHazelcastMaps(config);

        // Security and performance settings
        configureAdvancedSettings(config);

        logger.info("Initializing Hazelcast cluster: {} with discovery mode: {}", 
                   ClusterConstants.CLUSTER_NAME, discoveryMode);

        // Create and return Hazelcast instance
        return Hazelcast.newHazelcastInstance(config);
    }

    /**
     * Configure discovery mechanism based on deployment environment
     */
    private void configureDiscovery(NetworkConfig networkConfig) {
        JoinConfig joinConfig = networkConfig.getJoin();
        
        switch (discoveryMode.toLowerCase()) {
            case "kubernetes":
                configureKubernetesDiscovery(joinConfig);
                break;
            case "multicast":
                configureMulticastDiscovery(joinConfig);
                break;
            case "tcp-ip":
            default:
                configureTcpIpDiscovery(joinConfig);
                break;
        }
    }

    /**
     * Configure Kubernetes service discovery
     */
    private void configureKubernetesDiscovery(JoinConfig joinConfig) {
        logger.info("Configuring Kubernetes discovery for service: {}", kubernetesServiceName);
        
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getTcpIpConfig().setEnabled(false);
        
        // Enable Kubernetes discovery
        joinConfig.getKubernetesConfig().setEnabled(kubernetesEnabled)
                .setProperty("namespace", kubernetesNamespace)
                .setProperty("service-name", kubernetesServiceName);
    }

    /**
     * Configure multicast discovery (for development)
     */
    private void configureMulticastDiscovery(JoinConfig joinConfig) {
        logger.info("Configuring multicast discovery on {}:{}", multicastGroup, multicastPort);
        
        joinConfig.getTcpIpConfig().setEnabled(false);
        joinConfig.getKubernetesConfig().setEnabled(false);
        
        MulticastConfig multicastConfig = joinConfig.getMulticastConfig();
        multicastConfig.setEnabled(multicastEnabled)
                .setMulticastGroup(multicastGroup)
                .setMulticastPort(multicastPort);
    }

    /**
     * Configure TCP/IP discovery with dynamic member list
     */
    private void configureTcpIpDiscovery(JoinConfig joinConfig) {
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getKubernetesConfig().setEnabled(false);
        
        TcpIpConfig tcpIpConfig = joinConfig.getTcpIpConfig();
        tcpIpConfig.setEnabled(true);

        // Dynamic member discovery
        List<String> members = getDiscoveredMembers();
        
        if (members.isEmpty()) {
            logger.warn("No cluster members configured! Using fallback discovery.");
            members = getFallbackMembers();
        }

        for (String member : members) {
            tcpIpConfig.addMember(member.trim());
            logger.info("Added cluster member: {}", member);
        }
        
        logger.info("Configured TCP/IP discovery with {} members", members.size());
    }

    /**
     * Dynamically discover cluster members from various sources
     */
    private List<String> getDiscoveredMembers() {
        // Priority 1: Environment variable
        if (membersList != null && !membersList.trim().isEmpty()) {
            return Arrays.asList(membersList.split(","));
        }

        // Priority 2: System properties
        String systemMembers = System.getProperty("hazelcast.members");
        if (systemMembers != null && !systemMembers.trim().isEmpty()) {
            return Arrays.asList(systemMembers.split(","));
        }

        // Priority 3: Service discovery (could integrate with Consul, Eureka, etc.)
        return discoverMembersFromServiceRegistry();
    }

    /**
     * Integrate with external service discovery
     * TODO: Implement Consul/Eureka/Zookeeper integration
     */
    private List<String> discoverMembersFromServiceRegistry() {
        // Integration with ServiceDiscovery component would go here
        // For now, return empty list to use fallback discovery
        logger.debug("Service registry discovery not configured, using fallback");
        return List.of();
    }

    /**
     * Fallback members for development/local testing
     */
    private List<String> getFallbackMembers() {
        return Arrays.asList(
            "localhost:" + hazelcastPort,
            "127.0.0.1:" + (hazelcastPort + 1),
            "127.0.0.1:" + (hazelcastPort + 2)
        );
    }

    /**
     * Configure Hazelcast maps with appropriate settings
     */
    private void configureHazelcastMaps(Config config) {
        // Processed lines map
        MapConfig processedLinesMap = new MapConfig(ClusterConstants.PROCESSED_LINES_MAP);
        processedLinesMap.setTimeToLiveSeconds(3600); // 1 hour TTL
        processedLinesMap.setMaxIdleSeconds(1800);     // 30 minutes idle
        processedLinesMap.setBackupCount(1);           // 1 backup for HA
        config.addMapConfig(processedLinesMap);

        // Cluster metrics map
        MapConfig metricsMap = new MapConfig(ClusterConstants.CLUSTER_METRICS_MAP);
        metricsMap.setTimeToLiveSeconds(1800); // 30 minutes TTL
        metricsMap.setBackupCount(1);
        config.addMapConfig(metricsMap);

        // Node status map
        MapConfig nodeStatusMap = new MapConfig(ClusterConstants.NODE_STATUS_MAP);
        nodeStatusMap.setTimeToLiveSeconds(300); // 5 minutes TTL
        nodeStatusMap.setBackupCount(2); // More backups for critical data
        config.addMapConfig(nodeStatusMap);
    }

    /**
     * Configure advanced Hazelcast settings
     */
    private void configureAdvancedSettings(Config config) {
        // Management center (if available)
        ManagementCenterConfig mcConfig = config.getManagementCenterConfig();
        mcConfig.setConsoleEnabled(true);
        
        // Split brain protection
        config.getCPSubsystemConfig().setCPMemberCount(3);
        
        // Performance tuning
        config.setProperty("hazelcast.operation.call.timeout.millis", "60000");
        config.setProperty("hazelcast.operation.backup.timeout.millis", "5000");
        config.setProperty("hazelcast.partition.count", "271");
    }

    // Getters for injected values
    public String getNodeId() {
        return nodeId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public int getHazelcastPort() {
        return hazelcastPort;
    }

    public String getDiscoveryMode() {
        return discoveryMode;
    }
} 