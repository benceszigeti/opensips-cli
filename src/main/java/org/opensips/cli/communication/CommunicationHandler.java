package org.opensips.cli.communication;

import java.util.List;
import java.util.Map;

/**
 * Interface for communication handlers with OpenSIPS
 */
public interface CommunicationHandler {
    
    /**
     * Execute a command on OpenSIPS
     * 
     * @param command The command to execute
     * @param params List of parameters for the command
     * @return Response from OpenSIPS
     * @throws CommunicationException if communication fails
     */
    String execute(String command, List<String> params) throws CommunicationException;
    
    /**
     * Check if the communication handler is valid/connected
     * 
     * @return true if valid, false otherwise
     */
    boolean isValid();
    
    /**
     * Get validation error message if any
     * 
     * @return Error message or null if valid
     */
    String getValidationError();
    
    /**
     * Initialize the communication handler
     * 
     * @param config Configuration parameters
     * @throws CommunicationException if initialization fails
     */
    void initialize(Map<String, String> config) throws CommunicationException;
    
    /**
     * Close/cleanup the communication handler
     */
    void close();
}