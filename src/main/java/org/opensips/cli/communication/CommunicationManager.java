package org.opensips.cli.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages communication with OpenSIPS using different transport methods
 */
public class CommunicationManager {
    private static final Logger logger = LoggerFactory.getLogger(CommunicationManager.class);
    
    private CommunicationHandler handler;
    private Map<String, String> config;
    
    public CommunicationManager() {
        this.config = new HashMap<>();
    }
    
    /**
     * Initialize communication manager with configuration
     */
    public void initialize(Map<String, String> config) throws CommunicationException {
        this.config = new HashMap<>(config);
        
        String commType = config.getOrDefault("communication_type", "fifo");
        
        switch (commType.toLowerCase()) {
            case "http":
                handler = new HttpCommunicationHandler();
                break;
            case "fifo":
                handler = new FifoCommunicationHandler();
                break;
            case "datagram":
                handler = new DatagramCommunicationHandler();
                break;
            default:
                throw new CommunicationException("Unsupported communication type: " + commType);
        }
        
        handler.initialize(config);
        logger.debug("Initialized {} communication handler", commType);
    }
    
    /**
     * Execute a command
     */
    public String execute(String command, List<String> params) throws CommunicationException {
        if (handler == null) {
            throw new CommunicationException("Communication handler not initialized");
        }
        
        return handler.execute(command, params);
    }
    
    /**
     * Check if communication is valid
     */
    public boolean isValid() {
        return handler != null && handler.isValid();
    }
    
    /**
     * Get validation error
     */
    public String getValidationError() {
        return handler != null ? handler.getValidationError() : "Handler not initialized";
    }
    
    /**
     * Close communication handler
     */
    public void close() {
        if (handler != null) {
            handler.close();
        }
    }
    
    /**
     * Get the current configuration
     */
    public Map<String, String> getConfig() {
        return new HashMap<>(config);
    }
}