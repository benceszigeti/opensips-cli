package org.opensips.cli;

import org.apache.commons.cli.*;
import org.opensips.cli.config.Configuration;
import org.opensips.cli.config.ConfigurationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Main entry point for OpenSIPS CLI Java port
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) {
        try {
            Options options = createOptions();
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);
            
            if (cmd.hasOption("version")) {
                System.out.println(Version.getFullVersion());
                System.exit(0);
            }
            
            if (cmd.hasOption("help")) {
                printHelp(options);
                System.exit(0);
            }
            
            // Parse configuration
            Configuration config = parseConfiguration(cmd);
            
            // Create and start CLI
            OpenSIPSCLI cli = new OpenSIPSCLI(config);
            
            if (cmd.hasOption("execute")) {
                // Non-interactive mode
                String[] commandArgs = cmd.getArgs();
                if (commandArgs.length > 0) {
                    String command = String.join(" ", commandArgs);
                    cli.executeCommand(command);
                }
            } else {
                // Interactive mode
                cli.startInteractive();
            }
            
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments: {}", e.getMessage());
            printHelp(createOptions());
            System.exit(1);
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
    
    private static Options createOptions() {
        Options options = new Options();
        
        options.addOption("h", "help", false, "Display help information");
        options.addOption("v", "version", false, "Display version information");
        options.addOption("d", "debug", false, "Enable debug mode");
        options.addOption("f", "config", true, "Specify configuration file");
        options.addOption("i", "instance", true, "Choose OpenSIPS instance");
        options.addOption("o", "option", true, "Set configuration option (KEY=VALUE)");
        options.addOption("p", "print", false, "Print configuration");
        options.addOption("x", "execute", false, "Execute command and exit");
        
        return options;
    }
    
    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("opensips-cli [OPTIONS] [COMMAND]", 
                           "OpenSIPS CLI - Java Port\n\n", 
                           options, 
                           "\nExamples:\n" +
                           "  opensips-cli                    # Start interactive mode\n" +
                           "  opensips-cli -x 'mi ps'         # Execute command and exit\n" +
                           "  opensips-cli -f config.cfg      # Use custom config file\n" +
                           "  opensips-cli -i production      # Use specific instance\n" +
                           "  opensips-cli -o url=http://localhost:8080/mi  # Override config option",
                           true);
    }
    
    private static Configuration parseConfiguration(CommandLine cmd) {
        Configuration config = new Configuration();
        
        // Set debug mode
        if (cmd.hasOption("debug")) {
            config.setDebug(true);
        }
        
        // Set config file
        if (cmd.hasOption("config")) {
            config.setConfigFile(cmd.getOptionValue("config"));
        }
        
        // Set instance
        if (cmd.hasOption("instance")) {
            config.setInstance(cmd.getOptionValue("instance"));
        }
        
        // Parse options
        if (cmd.hasOption("option")) {
            String[] optionValues = cmd.getOptionValues("option");
            if (optionValues != null) {
                for (String option : optionValues) {
                    String[] parts = option.split("=", 2);
                    if (parts.length == 2) {
                        config.setOption(parts[0], parts[1]);
                    }
                }
            }
        }
        
        // Set print config flag
        if (cmd.hasOption("print")) {
            config.setPrintConfig(true);
        }
        
        // Set execute mode
        if (cmd.hasOption("execute")) {
            config.setExecuteMode(true);
        }
        
        return config;
    }
}