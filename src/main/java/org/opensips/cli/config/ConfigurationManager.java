package org.opensips.cli.config;

import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Manages configuration loading and parsing
 */
public class ConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);
    
    private static final List<String> DEFAULT_CONFIG_PATHS = Arrays.asList(
        System.getProperty("user.home") + "/.opensips-cli.cfg",
        "/etc/opensips-cli.cfg",
        "/etc/opensips/opensips-cli.cfg"
    );
    
    private static final String DEFAULT_SECTION = "default";
    
    private INIConfiguration iniConfig;
    private String currentInstance = DEFAULT_SECTION;
    
    public ConfigurationManager() {
        this.iniConfig = new INIConfiguration();
    }
    
    /**
     * Load configuration from file
     */
    public void loadConfiguration(String configFile) {
        if (configFile == null) {
            // Try to find config file in default locations
            configFile = findDefaultConfigFile();
        }
        
        if (configFile != null) {
            try {
                FileBasedConfigurationBuilder<INIConfiguration> builder =
                    new FileBasedConfigurationBuilder<INIConfiguration>(INIConfiguration.class)
                        .configure(new Parameters().fileBased().setFile(new File(configFile)));
                
                this.iniConfig = builder.getConfiguration();
                logger.debug("Loaded configuration from: {}", configFile);
            } catch (ConfigurationException e) {
                logger.warn("Failed to load configuration from {}: {}", configFile, e.getMessage());
            }
        } else {
            logger.debug("No configuration file found, using defaults");
        }
    }
    
    /**
     * Find default configuration file
     */
    private String findDefaultConfigFile() {
        for (String path : DEFAULT_CONFIG_PATHS) {
            if (Files.exists(Paths.get(path))) {
                return path;
            }
        }
        return null;
    }
    
    /**
     * Check if instance exists
     */
    public boolean hasInstance(String instance) {
        return iniConfig.getSections().contains(instance);
    }
    
    /**
     * Set current instance
     */
    public void setInstance(String instance) {
        if (hasInstance(instance)) {
            this.currentInstance = instance;
            logger.debug("Switched to instance: {}", instance);
        } else {
            logger.warn("Instance '{}' not found, using default", instance);
            this.currentInstance = DEFAULT_SECTION;
        }
    }
    
    /**
     * Get configuration value for current instance
     */
    public String get(String key) {
        return get(currentInstance, key);
    }
    
    /**
     * Get configuration value for specific instance
     */
    public String get(String instance, String key) {
        String fullKey = instance + "." + key;
        return iniConfig.getString(fullKey);
    }
    
    /**
     * Get configuration value with default
     */
    public String getWithDefault(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Get all configuration values for current instance
     */
    public java.util.Map<String, String> getInstanceConfig() {
        java.util.Map<String, String> config = new java.util.HashMap<>();
        
        if (iniConfig.getSections().contains(currentInstance)) {
            // Get all keys for current instance
            java.util.Iterator<String> keys = iniConfig.getKeys(currentInstance);
            while (keys.hasNext()) {
                String key = keys.next();
                String value = iniConfig.getString(key);
                if (value != null) {
                    // Remove instance prefix from key
                    String shortKey = key.substring(currentInstance.length() + 1);
                    config.put(shortKey, value);
                }
            }
        }
        
        return config;
    }
    
    /**
     * Get current instance name
     */
    public String getCurrentInstance() {
        return currentInstance;
    }
    
    /**
     * Get all available instances
     */
    public List<String> getAvailableInstances() {
        List<String> instances = new ArrayList<>();
        for (String section : iniConfig.getSections()) {
            instances.add(section);
        }
        return instances;
    }
    
    /**
     * Set custom options (overrides)
     */
    public void setCustomOptions(java.util.Map<String, String> options) {
        if (options != null) {
            for (java.util.Map.Entry<String, String> entry : options.entrySet()) {
                String fullKey = currentInstance + "." + entry.getKey();
                iniConfig.setProperty(fullKey, entry.getValue());
            }
        }
    }
    
    /**
     * Convert configuration to map
     */
    public java.util.Map<String, String> toMap() {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        
        // Add global configuration
        java.util.Iterator<String> globalKeys = iniConfig.getKeys();
        while (globalKeys.hasNext()) {
            String key = globalKeys.next();
            if (!key.contains(".")) { // Global keys don't have section prefix
                String value = iniConfig.getString(key);
                if (value != null) {
                    result.put(key, value);
                }
            }
        }
        
        // Add instance-specific configuration
        result.putAll(getInstanceConfig());
        
        return result;
    }
}