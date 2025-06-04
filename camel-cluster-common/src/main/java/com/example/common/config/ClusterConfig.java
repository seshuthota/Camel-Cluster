package com.example.common.config;

import com.example.common.util.ClusterConstants;
import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cluster configuration for Hazelcast.
 * Provides shared clustering functionality across all applications.
 */
@Configuration
public class ClusterConfig {

    @Value("${cluster.node.id:unknown}")
    private String nodeId;

    @Value("${cluster.node.type:unknown}")
    private String nodeType;

    @Value("${hazelcast.port:5701}")
    private int hazelcastPort;

    /**
     * Configure Hazelcast instance for clustering
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
        networkConfig.setPortAutoIncrement(false);

        // Join configuration - TCP/IP for Docker
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);
        
        TcpIpConfig tcpIpConfig = joinConfig.getTcpIpConfig();
        tcpIpConfig.setEnabled(true);
        tcpIpConfig.addMember("producer1:5701");
        tcpIpConfig.addMember("consumer1:5702");
        tcpIpConfig.addMember("consumer2:5703");
        tcpIpConfig.addMember("coordinator1:5704");
        tcpIpConfig.addMember("coordinator2:5705");

        // Map configurations for better performance
        configureHazelcastMaps(config);

        // Create and return Hazelcast instance
        return Hazelcast.newHazelcastInstance(config);
    }

    /**
     * Configure Hazelcast maps with appropriate settings
     */
    private void configureHazelcastMaps(Config config) {
        // Processed lines map
        MapConfig processedLinesMap = new MapConfig(ClusterConstants.PROCESSED_LINES_MAP);
        processedLinesMap.setTimeToLiveSeconds(3600); // 1 hour TTL
        processedLinesMap.setMaxIdleSeconds(1800);     // 30 minutes idle
        config.addMapConfig(processedLinesMap);

        // Cluster metrics map
        MapConfig metricsMap = new MapConfig(ClusterConstants.CLUSTER_METRICS_MAP);
        metricsMap.setTimeToLiveSeconds(1800); // 30 minutes TTL
        config.addMapConfig(metricsMap);

        // Node status map
        MapConfig nodeStatusMap = new MapConfig(ClusterConstants.NODE_STATUS_MAP);
        nodeStatusMap.setTimeToLiveSeconds(300); // 5 minutes TTL
        config.addMapConfig(nodeStatusMap);
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
} 