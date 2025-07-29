package org.opensips.cli.modules;

import java.util.Arrays;
import java.util.List;

/**
 * Instance management module
 */
public class InstanceModule extends AbstractModule {
    
    @Override
    public String getName() {
        return "instance";
    }
    
    @Override
    public String getDescription() {
        return "Manage OpenSIPS instances";
    }
    
    @Override
    public String execute(String command) {
        CommandParseResult parsed = parseCommand(command);
        
        if (parsed.getCommand().isEmpty()) {
            return "Usage: instance <command> [parameters...]\n" +
                   "Available commands: " + String.join(", ", getAvailableCommands());
        }
        
        switch (parsed.getCommand()) {
            case "list":
                return "Available instances: default, production, development";
            case "switch":
                if (parsed.getParams().isEmpty()) {
                    return "Error: instance name required";
                }
                return "Switched to instance: " + parsed.getParams().get(0);
            case "current":
                return "Current instance: default";
            default:
                return "Unknown command: " + parsed.getCommand();
        }
    }
    
    @Override
    public List<String> getAvailableCommands() {
        return Arrays.asList("list", "switch", "current");
    }
}