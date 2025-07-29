package org.opensips.cli.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for OpenSIPS CLI
 */
public class Configuration {
    private boolean debug = false;
    private String configFile = null;
    private String instance = "default";
    private boolean printConfig = false;
    private boolean executeMode = false;
    private Map<String, String> options = new HashMap<>();
    
    // Default configuration values
    private String promptName = "opensips-cli";
    private String promptIntro = "OpenSIPS CLI - Java Port\nType 'help' for available commands.";
    private boolean promptEmptyLineRepeatCmd = false;
    private String historyFile = System.getProperty("user.home") + "/.opensips-cli.history";
    private int historyFileSize = 1000;
    private String logLevel = "WARNING";
    private String communicationType = "fifo";
    private String fifoFile = "/var/run/opensips/opensips_fifo";
    private String fifoFileFallback = "/tmp/opensips_fifo";
    private String fifoReplyDir = "/tmp";
    private String url = "http://127.0.0.1:8888/mi";
    private String datagramIp = "127.0.0.1";
    private int datagramPort = 8080;
    private String datagramUnixSocket = "/tmp/opensips.sock";
    
    public Configuration() {
        // Initialize with defaults
    }
    
    // Getters and setters
    public boolean isDebug() {
        return debug;
    }
    
    public void setDebug(boolean debug) {
        this.debug = debug;
    }
    
    public String getConfigFile() {
        return configFile;
    }
    
    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }
    
    public String getInstance() {
        return instance;
    }
    
    public void setInstance(String instance) {
        this.instance = instance;
    }
    
    public boolean isPrintConfig() {
        return printConfig;
    }
    
    public void setPrintConfig(boolean printConfig) {
        this.printConfig = printConfig;
    }
    
    public boolean isExecuteMode() {
        return executeMode;
    }
    
    public void setExecuteMode(boolean executeMode) {
        this.executeMode = executeMode;
    }
    
    public void setOption(String key, String value) {
        options.put(key, value);
    }
    
    public String getOption(String key) {
        return options.get(key);
    }
    
    public Map<String, String> getOptions() {
        return new HashMap<>(options);
    }
    
    // Core configuration getters
    public String getPromptName() {
        return options.getOrDefault("prompt_name", promptName);
    }
    
    public String getPromptIntro() {
        return options.getOrDefault("prompt_intro", promptIntro);
    }
    
    public boolean isPromptEmptyLineRepeatCmd() {
        return Boolean.parseBoolean(options.getOrDefault("prompt_emptyline_repeat_cmd", 
                                                       String.valueOf(promptEmptyLineRepeatCmd)));
    }
    
    public String getHistoryFile() {
        return options.getOrDefault("history_file", historyFile);
    }
    
    public int getHistoryFileSize() {
        return Integer.parseInt(options.getOrDefault("history_file_size", 
                                                   String.valueOf(historyFileSize)));
    }
    
    public String getLogLevel() {
        return options.getOrDefault("log_level", logLevel);
    }
    
    public String getCommunicationType() {
        return options.getOrDefault("communication_type", communicationType);
    }
    
    public String getFifoFile() {
        return options.getOrDefault("fifo_file", fifoFile);
    }
    
    public String getFifoFileFallback() {
        return options.getOrDefault("fifo_file_fallback", fifoFileFallback);
    }
    
    public String getFifoReplyDir() {
        return options.getOrDefault("fifo_reply_dir", fifoReplyDir);
    }
    
    public String getUrl() {
        return options.getOrDefault("url", url);
    }
    
    public String getDatagramIp() {
        return options.getOrDefault("datagram_ip", datagramIp);
    }
    
    public int getDatagramPort() {
        return Integer.parseInt(options.getOrDefault("datagram_port", 
                                                   String.valueOf(datagramPort)));
    }
    
    public String getDatagramUnixSocket() {
        return options.getOrDefault("datagram_unix_socket", datagramUnixSocket);
    }
    
    @Override
    public String toString() {
        return "Configuration{" +
                "debug=" + debug +
                ", configFile='" + configFile + '\'' +
                ", instance='" + instance + '\'' +
                ", printConfig=" + printConfig +
                ", executeMode=" + executeMode +
                ", options=" + options +
                '}';
    }
}