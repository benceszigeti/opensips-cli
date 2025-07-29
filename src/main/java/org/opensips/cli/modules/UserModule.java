package org.opensips.cli.modules;

import java.util.Arrays;
import java.util.List;

/**
 * User management module
 */
public class UserModule extends AbstractModule {
    
    @Override
    public String getName() {
        return "user";
    }
    
    @Override
    public String getDescription() {
        return "Manage OpenSIPS users";
    }
    
    @Override
    public String execute(String command) {
        CommandParseResult parsed = parseCommand(command);
        
        if (parsed.getCommand().isEmpty()) {
            return "Usage: user <command> [parameters...]\n" +
                   "Available commands: " + String.join(", ", getAvailableCommands());
        }
        
        switch (parsed.getCommand()) {
            case "add":
                if (parsed.getParams().size() < 2) {
                    return "Error: username and password required";
                }
                return "Added user: " + parsed.getParams().get(0);
            case "remove":
                if (parsed.getParams().isEmpty()) {
                    return "Error: username required";
                }
                return "Removed user: " + parsed.getParams().get(0);
            case "list":
                return "User list functionality not yet implemented";
            default:
                return "Unknown command: " + parsed.getCommand();
        }
    }
    
    @Override
    public List<String> getAvailableCommands() {
        return Arrays.asList("add", "remove", "list");
    }
}