package org.opensips.cli.modules;

import java.util.List;

/**
 * Interface for OpenSIPS CLI modules
 */
public interface Module {
    
    /**
     * Get the name of the module
     */
    String getName();
    
    /**
     * Get a description of the module
     */
    String getDescription();
    
    /**
     * Execute a command in this module
     * 
     * @param command The command to execute
     * @return Result of the command execution
     */
    String execute(String command);
    
    /**
     * Get available commands in this module
     * 
     * @return List of available commands
     */
    List<String> getAvailableCommands();
    
    /**
     * Get completions for a partial command
     * 
     * @param partialCommand The partial command
     * @return List of possible completions
     */
    List<String> getCompletions(String partialCommand);
    
    /**
     * Check if the module should be excluded
     * 
     * @return true if module should be excluded, false otherwise
     */
    default boolean isExcluded() {
        return false;
    }
    
    /**
     * Get exclusion reason if module is excluded
     * 
     * @return Reason for exclusion or null if not excluded
     */
    default String getExclusionReason() {
        return null;
    }
}