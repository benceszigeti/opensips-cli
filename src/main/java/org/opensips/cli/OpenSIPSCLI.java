package org.opensips.cli;

import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.opensips.cli.config.Configuration;
import org.opensips.cli.config.ConfigurationManager;
import org.opensips.cli.communication.CommunicationException;
import org.opensips.cli.communication.CommunicationHandler;
import org.opensips.cli.communication.CommunicationManager;
import org.opensips.cli.modules.Module;
import org.opensips.cli.modules.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Main OpenSIPS CLI class providing interactive command-line interface
 */
public class OpenSIPSCLI {
    private static final Logger logger = LoggerFactory.getLogger(OpenSIPSCLI.class);
    
    private Configuration config;
    private ConfigurationManager configManager;
    private CommunicationManager commManager;
    private ModuleManager moduleManager;
    private Terminal terminal;
    private LineReader reader;
    private String lastCommand = "";
    
    public OpenSIPSCLI(Configuration config) {
        this.config = config;
        this.configManager = new ConfigurationManager();
        this.commManager = new CommunicationManager();
        this.moduleManager = new ModuleManager();
        
        initialize();
    }
    
    private void initialize() {
        try {
            // Load configuration
            configManager.loadConfiguration(config.getConfigFile());
            configManager.setInstance(config.getInstance());
            
            // Set custom options
            configManager.setCustomOptions(config.getOptions());
            
            // Initialize communication
            commManager.initialize(configManager.toMap());
            
            // Initialize modules
            moduleManager.initialize(commManager);
            
            // Initialize terminal for interactive mode
            if (!config.isExecuteMode()) {
                initializeTerminal();
            }
            
            // Print configuration if requested
            if (config.isPrintConfig()) {
                printConfiguration();
            }
            
        } catch (Exception e) {
            logger.error("Failed to initialize OpenSIPS CLI: {}", e.getMessage(), e);
            throw new RuntimeException("Initialization failed", e);
        }
    }
    
    private void initializeTerminal() throws IOException {
        terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        
        reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new DefaultParser())
                .completer(new OpenSIPSCompleter(moduleManager))
                .history(new DefaultHistory())
                .build();
    }
    
    /**
     * Start interactive mode
     */
    public void startInteractive() {
        if (config.isExecuteMode()) {
            logger.error("Cannot start interactive mode in execute mode");
            return;
        }
        
        System.out.println(config.getPromptIntro());
        
        while (true) {
            try {
                String line = reader.readLine(config.getPromptName() + "> ");
                
                if (line == null) {
                    break; // EOF
                }
                
                line = line.trim();
                if (line.isEmpty()) {
                    if (config.isPromptEmptyLineRepeatCmd() && !lastCommand.isEmpty()) {
                        line = lastCommand;
                    } else {
                        continue;
                    }
                }
                
                if (!line.isEmpty()) {
                    lastCommand = line;
                    executeCommand(line);
                }
                
            } catch (UserInterruptException e) {
                // Ctrl+C
                System.out.println("^C");
            } catch (EndOfFileException e) {
                // Ctrl+D
                break;
            } catch (Exception e) {
                logger.error("Error reading command: {}", e.getMessage());
            }
        }
        
        System.out.println("Goodbye!");
    }
    
    /**
     * Execute a single command
     */
    public void executeCommand(String command) {
        try {
            String[] parts = command.split("\\s+", 2);
            String moduleName = parts[0];
            String moduleCommand = parts.length > 1 ? parts[1] : "";
            
            Module module = moduleManager.getModule(moduleName);
            if (module == null) {
                System.err.println("Unknown module: " + moduleName);
                System.err.println("Available modules: " + moduleManager.getAvailableModules());
                return;
            }
            
            String result = module.execute(moduleCommand);
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }
            
        } catch (Exception e) {
            logger.error("Error executing command '{}': {}", command, e.getMessage());
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    /**
     * Execute MI command directly
     */
    public String executeMICommand(String command, List<String> params) {
        try {
            return commManager.execute(command, params);
        } catch (CommunicationException e) {
            logger.error("MI command failed: {}", e.getMessage());
            return null;
        }
    }
    
    private void printConfiguration() {
        System.out.println("Configuration:");
        Map<String, String> configMap = configManager.toMap();
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }
    }
    
    /**
     * Close resources
     */
    public void close() {
        try {
            if (terminal != null) {
                terminal.close();
            }
            if (commManager != null) {
                commManager.close();
            }
        } catch (IOException e) {
            logger.warn("Error closing resources", e);
        }
    }
}