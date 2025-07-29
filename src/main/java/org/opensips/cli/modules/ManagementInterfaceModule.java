package org.opensips.cli.modules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Management Interface module for executing MI commands
 */
public class ManagementInterfaceModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(ManagementInterfaceModule.class);
    private ObjectMapper objectMapper = new ObjectMapper();
    private List<String> cachedCommands = null;
    
    @Override
    public String getName() {
        return "mi";
    }
    
    @Override
    public String getDescription() {
        return "Execute Management Interface commands";
    }
    
    @Override
    public String execute(String command) {
        CommandParseResult parsed = parseCommand(command);
        
        if (parsed.getCommand().isEmpty()) {
            return "Usage: mi <command> [parameters...]\n" +
                   "Available commands: " + String.join(", ", getAvailableCommands());
        }
        
        return executeMICommand(parsed.getCommand(), parsed.getParams());
    }
    
    @Override
    public List<String> getAvailableCommands() {
        if (cachedCommands != null) {
            return cachedCommands;
        }
        
        // Check if dynamic command fetching is disabled (useful for FIFO)
        try {
            String disableDynamic = commManager != null ? 
                commManager.getConfig().getOrDefault("disable_dynamic_commands", "false") : "false";
            if ("true".equalsIgnoreCase(disableDynamic)) {
                logger.debug("Dynamic command fetching disabled, using fallback list");
                return getFallbackCommands();
            }
            
            // For FIFO communication, disable dynamic fetching to prevent hanging
            String commType = commManager.getConfig().getOrDefault("communication_type", "fifo");
            if ("fifo".equalsIgnoreCase(commType)) {
                logger.debug("FIFO communication detected, using fallback command list to prevent hanging");
                return getFallbackCommands();
            }
        } catch (Exception e) {
            logger.debug("Could not check communication settings: {}", e.getMessage());
        }
        
        try {
            // Fetch available commands from OpenSIPS using 'which' command
            // Use a shorter timeout for this operation to prevent hanging
            String response = executeMICommand("which", new ArrayList<>());
            if (response != null && !response.isEmpty()) {
                try {
                    // Parse JSON response
                    JsonNode jsonNode = objectMapper.readTree(response);
                    List<String> commands = new ArrayList<>();
                    
                    if (jsonNode.isArray()) {
                        for (JsonNode node : jsonNode) {
                            if (node.isTextual()) {
                                commands.add(node.asText());
                            }
                        }
                    } else if (jsonNode.isObject()) {
                        // Handle object response format
                        for (String fieldName : (Iterable<String>) jsonNode::fieldNames) {
                            commands.add(fieldName);
                        }
                    }
                    
                    if (!commands.isEmpty()) {
                        cachedCommands = commands;
                        return commands;
                    }
                } catch (Exception e) {
                    logger.debug("Failed to parse 'which' response as JSON: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to fetch available commands dynamically: {}", e.getMessage());
        }
        
        // Fallback to hardcoded list if dynamic fetching fails
        logger.debug("Using fallback command list");
        return getFallbackCommands();
    }
    
    private List<String> getFallbackCommands() {
        return Arrays.asList(
            "ps", "uptime", "version", "shm_status", "get_statistics",
            "get_statistics_all", "get_statistics_group", "clear_statistics",
            "list_statistics", "get_proc_stats", "get_worker_pid",
            "set_debug", "set_log_level", "set_glog_level", "set_xlog_level",
            "which", "help", "kill", "pwd", "arg", "shm_check", "cache_store",
            "cache_fetch", "cache_remove", "event_subscribe", "events_list",
            "subscribers_list", "raise_event", "list_tcp_conns", "mem_pkg_dump",
            "mem_shm_dump", "mem_rpm_dump", "reload_routes", "sr_get_status",
            "sr_list_status", "sr_list_reports", "sr_list_identifiers",
            "list_blacklists", "check_blacklists", "check_blacklist",
            "add_blacklist_rule", "del_blacklist_rule", "t_uac_dlg", "t_uac_cancel",
            "t_hash", "t_reply", "db_get", "db_set", "httpd_list_root_path",
            "rand_set_prob", "rand_reset_prob", "rand_get_prob", "get_config_hash",
            "check_config_hash", "shv_get", "shv_set", "ds_set_state", "ds_list",
            "ds_reload", "ds_push_script_attrs", "cache_remove_chunk", "trace",
            "trace_start", "trace_stop", "rl_list", "rl_reset_pipe", "rl_set_pid",
            "rl_get_pid", "rl_dump_pipe", "tcp_trace", "tls_reload", "tls_list",
            "tls_trace", "address_reload", "address_dump", "subnet_dump", "allow_uri"
        );
    }
    
    /**
     * Clear cached commands (useful for testing or when commands change)
     */
    public void clearCache() {
        cachedCommands = null;
    }
}