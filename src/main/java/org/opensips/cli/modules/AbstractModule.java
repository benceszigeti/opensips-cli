package org.opensips.cli.modules;

import org.opensips.cli.communication.CommunicationManager;
import org.opensips.cli.communication.CommunicationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for OpenSIPS CLI modules
 */
public abstract class AbstractModule implements Module {
    
    protected CommunicationManager commManager;
    
    /**
     * Set the communication manager for this module
     */
    public void setCommunicationManager(CommunicationManager commManager) {
        this.commManager = commManager;
    }
    
    /**
     * Execute an MI command
     */
    protected String executeMICommand(String command, List<String> params) {
        try {
            return commManager.execute(command, params);
        } catch (CommunicationException e) {
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * Execute an MI command with no parameters
     */
    protected String executeMICommand(String command) {
        return executeMICommand(command, new ArrayList<>());
    }
    
    /**
     * Parse command line into command and parameters
     */
    protected CommandParseResult parseCommand(String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return new CommandParseResult("", new ArrayList<>());
        }
        
        String[] parts = commandLine.trim().split("\\s+");
        String command = parts[0];
        List<String> params = new ArrayList<>();
        
        for (int i = 1; i < parts.length; i++) {
            params.add(parts[i]);
        }
        
        return new CommandParseResult(command, params);
    }
    
    /**
     * Get completions for a partial command
     */
    @Override
    public List<String> getCompletions(String partialCommand) {
        List<String> completions = new ArrayList<>();
        List<String> availableCommands = getAvailableCommands();
        
        if (partialCommand == null || partialCommand.isEmpty()) {
            return availableCommands;
        }
        
        for (String command : availableCommands) {
            if (command.startsWith(partialCommand)) {
                completions.add(command);
            }
        }
        
        return completions;
    }
    
    /**
     * Helper class for parsed command results
     */
    protected static class CommandParseResult {
        private final String command;
        private final List<String> params;
        
        public CommandParseResult(String command, List<String> params) {
            this.command = command;
            this.params = params;
        }
        
        public String getCommand() {
            return command;
        }
        
        public List<String> getParams() {
            return params;
        }
    }
}