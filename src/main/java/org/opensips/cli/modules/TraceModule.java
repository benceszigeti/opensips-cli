package org.opensips.cli.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Call tracing module
 */
public class TraceModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(TraceModule.class);
    
    private static final ConcurrentHashMap<String, TraceSession> activeTraces = new ConcurrentHashMap<>();
    private static final AtomicBoolean globalTracing = new AtomicBoolean(false);
    
    @Override
    public String getName() {
        return "trace";
    }
    
    @Override
    public String getDescription() {
        return "Trace call information";
    }
    
    @Override
    public String execute(String command) {
        CommandParseResult parsed = parseCommand(command);
        
        if (parsed.getCommand().isEmpty()) {
            return "Usage: trace <command> [parameters...]\n" +
                   "Available commands: " + String.join(", ", getAvailableCommands());
        }
        
        switch (parsed.getCommand()) {
            case "start":
                return startTrace(parsed.getParams());
            case "stop":
                return stopTrace(parsed.getParams());
            case "status":
                return getTraceStatus(parsed.getParams());
            case "list":
                return listTraces(parsed.getParams());
            case "show":
                return showTrace(parsed.getParams());
            case "export":
                return exportTrace(parsed.getParams());
            case "filter":
                return setTraceFilter(parsed.getParams());
            case "clear":
                return clearTraces(parsed.getParams());
            default:
                return "Unknown command: " + parsed.getCommand();
        }
    }
    
    @Override
    public List<String> getAvailableCommands() {
        return Arrays.asList("start", "stop", "status", "list", "show", "export", "filter", "clear");
    }
    
    private String startTrace(List<String> params) {
        if (params.isEmpty()) {
            return "Error: trace start requires <call_id|user|all> [duration_seconds] [output_file]";
        }
        
        String target = params.get(0);
        int duration = params.size() > 1 ? Integer.parseInt(params.get(1)) : 300; // 5 minutes default
        String outputFile = params.size() > 2 ? params.get(2) : null;
        
        try {
            String traceId = generateTraceId();
            TraceSession session = new TraceSession(traceId, target, duration, outputFile);
            
            if ("all".equalsIgnoreCase(target)) {
                globalTracing.set(true);
                session.setGlobal(true);
            }
            
            activeTraces.put(traceId, session);
            
            // Start tracing via MI command
            String result = executeMICommand("trace_start", List.of(target, String.valueOf(duration)));
            
            if (result != null && !result.contains("Error")) {
                return String.format("Trace started successfully:\n" +
                                   "Trace ID: %s\n" +
                                   "Target: %s\n" +
                                   "Duration: %d seconds\n" +
                                   "Output: %s", traceId, target, duration, 
                                   outputFile != null ? outputFile : "console");
            } else {
                activeTraces.remove(traceId);
                return "Failed to start trace: " + result;
            }
            
        } catch (Exception e) {
            logger.error("Failed to start trace", e);
            return "Error starting trace: " + e.getMessage();
        }
    }
    
    private String stopTrace(List<String> params) {
        if (params.isEmpty()) {
            return "Error: trace stop requires <trace_id|all>";
        }
        
        String target = params.get(0);
        
        try {
            if ("all".equalsIgnoreCase(target)) {
                // Stop all traces
                for (String traceId : activeTraces.keySet()) {
                    stopSingleTrace(traceId);
                }
                globalTracing.set(false);
                return "All traces stopped";
            } else {
                // Stop specific trace
                return stopSingleTrace(target);
            }
            
        } catch (Exception e) {
            logger.error("Failed to stop trace", e);
            return "Error stopping trace: " + e.getMessage();
        }
    }
    
    private String stopSingleTrace(String traceId) {
        TraceSession session = activeTraces.get(traceId);
        if (session == null) {
            return "Trace not found: " + traceId;
        }
        
        try {
            // Stop tracing via MI command
            String result = executeMICommand("trace_stop", List.of(session.getTarget()));
            
            if (result != null && !result.contains("Error")) {
                // Export trace data if output file was specified
                if (session.getOutputFile() != null) {
                    exportTraceData(session);
                }
                
                activeTraces.remove(traceId);
                return "Trace stopped: " + traceId;
            } else {
                return "Failed to stop trace: " + result;
            }
            
        } catch (Exception e) {
            logger.error("Failed to stop trace", e);
            return "Error stopping trace: " + e.getMessage();
        }
    }
    
    private String getTraceStatus(List<String> params) {
        if (activeTraces.isEmpty() && !globalTracing.get()) {
            return "No active traces";
        }
        
        StringBuilder status = new StringBuilder();
        status.append("Trace Status:\n");
        status.append("Global tracing: ").append(globalTracing.get() ? "enabled" : "disabled").append("\n");
        status.append("Active traces: ").append(activeTraces.size()).append("\n\n");
        
        for (TraceSession session : activeTraces.values()) {
            status.append("Trace ID: ").append(session.getTraceId()).append("\n");
            status.append("Target: ").append(session.getTarget()).append("\n");
            status.append("Started: ").append(session.getStartTime()).append("\n");
            status.append("Duration: ").append(session.getDuration()).append(" seconds\n");
            status.append("Output: ").append(session.getOutputFile() != null ? session.getOutputFile() : "console").append("\n");
            status.append("Status: ").append(session.isActive() ? "active" : "stopped").append("\n\n");
        }
        
        return status.toString();
    }
    
    private String listTraces(List<String> params) {
        if (activeTraces.isEmpty()) {
            return "No active traces";
        }
        
        StringBuilder list = new StringBuilder();
        list.append("Active Traces:\n");
        list.append(String.format("%-20s %-15s %-20s %-10s\n", "Trace ID", "Target", "Start Time", "Status"));
        list.append("-".repeat(70)).append("\n");
        
        for (TraceSession session : activeTraces.values()) {
            list.append(String.format("%-20s %-15s %-20s %-10s\n",
                session.getTraceId(),
                session.getTarget(),
                session.getStartTime(),
                session.isActive() ? "active" : "stopped"
            ));
        }
        
        return list.toString();
    }
    
    private String showTrace(List<String> params) {
        if (params.isEmpty()) {
            return "Error: trace show requires <trace_id>";
        }
        
        String traceId = params.get(0);
        TraceSession session = activeTraces.get(traceId);
        
        if (session == null) {
            return "Trace not found: " + traceId;
        }
        
        try {
            // Get trace data via MI command
            String result = executeMICommand("trace_show", List.of(session.getTarget()));
            
            if (result != null && !result.contains("Error")) {
                return "Trace data for " + traceId + ":\n" + result;
            } else {
                return "Failed to get trace data: " + result;
            }
            
        } catch (Exception e) {
            logger.error("Failed to show trace", e);
            return "Error showing trace: " + e.getMessage();
        }
    }
    
    private String exportTrace(List<String> params) {
        if (params.size() < 2) {
            return "Error: trace export requires <trace_id> <output_file>";
        }
        
        String traceId = params.get(0);
        String outputFile = params.get(1);
        
        TraceSession session = activeTraces.get(traceId);
        if (session == null) {
            return "Trace not found: " + traceId;
        }
        
        try {
            exportTraceData(session, outputFile);
            return "Trace exported to: " + outputFile;
            
        } catch (Exception e) {
            logger.error("Failed to export trace", e);
            return "Error exporting trace: " + e.getMessage();
        }
    }
    
    private String setTraceFilter(List<String> params) {
        if (params.size() < 2) {
            return "Error: trace filter requires <trace_id> <filter_expression>";
        }
        
        String traceId = params.get(0);
        String filter = String.join(" ", params.subList(1, params.size()));
        
        TraceSession session = activeTraces.get(traceId);
        if (session == null) {
            return "Trace not found: " + traceId;
        }
        
        try {
            // Set trace filter via MI command
            String result = executeMICommand("trace_filter", List.of(session.getTarget(), filter));
            
            if (result != null && !result.contains("Error")) {
                session.setFilter(filter);
                return "Filter set for trace " + traceId + ": " + filter;
            } else {
                return "Failed to set filter: " + result;
            }
            
        } catch (Exception e) {
            logger.error("Failed to set trace filter", e);
            return "Error setting filter: " + e.getMessage();
        }
    }
    
    private String clearTraces(List<String> params) {
        try {
            // Clear all traces via MI command
            String result = executeMICommand("trace_clear", List.of());
            
            if (result != null && !result.contains("Error")) {
                activeTraces.clear();
                globalTracing.set(false);
                return "All traces cleared";
            } else {
                return "Failed to clear traces: " + result;
            }
            
        } catch (Exception e) {
            logger.error("Failed to clear traces", e);
            return "Error clearing traces: " + e.getMessage();
        }
    }
    
    // Helper methods
    private String generateTraceId() {
        return "trace_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }
    
    private void exportTraceData(TraceSession session) throws IOException {
        exportTraceData(session, session.getOutputFile());
    }
    
    private void exportTraceData(TraceSession session, String outputFile) throws IOException {
        // Get trace data
        String traceData = executeMICommand("trace_show", List.of(session.getTarget()));
        
        if (traceData != null && !traceData.contains("Error")) {
            Path outputPath = Paths.get(outputFile);
            Files.write(outputPath, traceData.getBytes());
        }
    }
    
    // Inner class for trace sessions
    private static class TraceSession {
        private final String traceId;
        private final String target;
        private final int duration;
        private final String outputFile;
        private final String startTime;
        private final boolean global;
        private String filter;
        private boolean active;
        
        public TraceSession(String traceId, String target, int duration, String outputFile) {
            this.traceId = traceId;
            this.target = target;
            this.duration = duration;
            this.outputFile = outputFile;
            this.startTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            this.global = false;
            this.active = true;
        }
        
        // Getters and setters
        public String getTraceId() { return traceId; }
        public String getTarget() { return target; }
        public int getDuration() { return duration; }
        public String getOutputFile() { return outputFile; }
        public String getStartTime() { return startTime; }
        public boolean isGlobal() { return global; }
        public String getFilter() { return filter; }
        public boolean isActive() { return active; }
        
        public void setGlobal(boolean global) { /* this.global = global; */ }
        public void setFilter(String filter) { this.filter = filter; }
        public void setActive(boolean active) { this.active = active; }
    }
}