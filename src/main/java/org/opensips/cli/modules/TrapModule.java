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
import java.util.ArrayList;
import java.io.PrintWriter;

/**
 * Debug trap module
 */
public class TrapModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(TrapModule.class);
    
    private static final ConcurrentHashMap<String, TrapSession> activeTraps = new ConcurrentHashMap<>();
    
    @Override
    public String getName() {
        return "trap";
    }
    
    @Override
    public String getDescription() {
        return "Debug trap functionality";
    }
    
    @Override
    public String execute(String command) {
        CommandParseResult parsed = parseCommand(command);
        
        if (parsed.getCommand().isEmpty()) {
            // No command specified - this is the main trap functionality
            return doTrap(parsed.getParams());
        }
        
        switch (parsed.getCommand()) {
            case "snapshot":
                return takeSnapshot(parsed.getParams());
            case "backtrace":
                return getBacktrace(parsed.getParams());
            case "memory":
                return getMemoryInfo(parsed.getParams());
            case "threads":
                return getThreadInfo(parsed.getParams());
            case "signals":
                return sendSignal(parsed.getParams());
            case "attach":
                return attachDebugger(parsed.getParams());
            case "detach":
                return detachDebugger(parsed.getParams());
            case "list":
                return listTraps(parsed.getParams());
            case "clear":
                return clearTraps(parsed.getParams());
            default:
                return "Usage: trap <command> [parameters...]\n" +
                       "Available commands: " + String.join(", ", getAvailableCommands()) + "\n\n" +
                       "Or use 'trap' without parameters to take a snapshot";
        }
    }
    
    private String doTrap(List<String> params) {
        try {
            // Get trap file from configuration
            String trapFile = getTrapFile();
            String processName = getProcessName();
            
            logger.info("Trapping {} in {}", processName, trapFile);
            
            // Get PIDs - either from params or from MI
            List<String> pids;
            if (params != null && !params.isEmpty()) {
                pids = params;
            } else {
                pids = getOpenSIPSPids();
            }
            
            if (pids.isEmpty()) {
                return "Error: could not find OpenSIPS' pids";
            }
            
            logger.debug("Dumping PIDs: {}", String.join(", ", pids));
            
            // Take backtraces for all PIDs
            StringBuilder output = new StringBuilder();
            String processInfo = getProcessInfo(pids);
            
            if (!processInfo.isEmpty()) {
                output.append(processInfo).append("\n");
            }
            
            for (String pid : pids) {
                try {
                    // Create temporary file for gdb output
                    String tempFile = "/tmp/gdb_output_" + pid + "_" + System.currentTimeMillis();
                    String backtrace = getGdbBacktrace(pid, null, tempFile);
                    
                    if (backtrace != null && !backtrace.contains("Error")) {
                        // Read the gdb output from temp file
                        String gdbOutput = readFileContent(tempFile);
                        if (gdbOutput != null && !gdbOutput.isEmpty()) {
                            String procInfo = getProcessInfo(pid);
                            output.append("\n\n---start ").append(pid).append(" (").append(procInfo).append(")\n");
                            output.append(gdbOutput);
                        } else {
                            logger.warn("No gdb output for pid {}", pid);
                        }
                    } else {
                        logger.warn("Failed to get backtrace for pid {}: {}", pid, backtrace);
                    }
                    
                    // Clean up temp file
                    try {
                        Files.deleteIfExists(Paths.get(tempFile));
                    } catch (Exception e) {
                        logger.debug("Could not delete temp file {}: {}", tempFile, e.getMessage());
                    }
                    
                } catch (Exception e) {
                    logger.warn("Failed to get backtrace for pid {}: {}", pid, e.getMessage());
                }
            }
            
            // Write to trap file
            try (PrintWriter writer = new PrintWriter(new FileWriter(trapFile))) {
                writer.write(output.toString());
            }
            
            return "Trap file: " + trapFile;
            
        } catch (Exception e) {
            logger.error("Failed to execute trap", e);
            return "Error executing trap: " + e.getMessage();
        }
    }
    
    private String readFileContent(String filePath) {
        try {
            return new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (Exception e) {
            logger.debug("Could not read file {}: {}", filePath, e.getMessage());
            return null;
        }
    }
    
    private String getTrapFile() {
        // Try to get from configuration, fallback to default
        try {
            if (commManager != null) {
                String trapFile = commManager.getConfig().get("trap_file");
                if (trapFile != null && !trapFile.isEmpty()) {
                    return trapFile;
                }
            }
        } catch (Exception e) {
            logger.debug("Could not get trap_file from config: {}", e.getMessage());
        }
        
        // Default trap file location
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return "/tmp/gdb_opensips_" + timestamp;
    }
    
    private String getProcessName() {
        try {
            if (commManager != null) {
                String processName = commManager.getConfig().get("process_name");
                if (processName != null && !processName.isEmpty()) {
                    return processName;
                }
            }
        } catch (Exception e) {
            logger.debug("Could not get process_name from config: {}", e.getMessage());
        }
        return "opensips";
    }
    
    private List<String> getOpenSIPSPids() {
        List<String> pids = new ArrayList<>();
        
        try {
            // Try to get PIDs through MI first
            String response = executeMICommand("ps", new ArrayList<>());
            if (response != null && !response.isEmpty()) {
                // Parse JSON response to extract PIDs
                // This is a simplified version - in real implementation you'd parse the JSON properly
                String[] lines = response.split("\n");
                for (String line : lines) {
                    if (line.contains("\"PID\"")) {
                        // Extract PID from JSON line
                        int start = line.indexOf("\"PID\":") + 6;
                        int end = line.indexOf(",", start);
                        if (end == -1) end = line.indexOf("}", start);
                        if (start > 6 && end > start) {
                            String pid = line.substring(start, end).trim();
                            if (pid.matches("\\d+")) {
                                pids.add(pid);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not get PIDs through MI: {}", e.getMessage());
        }
        
        // Fallback to pidof command
        if (pids.isEmpty()) {
            try {
                ProcessBuilder pb = new ProcessBuilder("pidof", getProcessName());
                Process process = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String output = reader.readLine();
                if (output != null && !output.isEmpty()) {
                    String[] pidArray = output.split("\\s+");
                    pids.addAll(Arrays.asList(pidArray));
                }
                process.waitFor();
            } catch (Exception e) {
                logger.debug("Could not get PIDs using pidof: {}", e.getMessage());
            }
        }
        
        return pids;
    }
    
    private String getProcessInfo(List<String> pids) {
        StringBuilder info = new StringBuilder();
        for (String pid : pids) {
            info.append(getProcessInfo(pid)).append("\n");
        }
        return info.toString();
    }
    
    private String getProcessInfo(String pid) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ps", "--no-headers", "-ww", "-fp", pid);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.readLine();
            process.waitFor();
            return output != null ? output : "UNKNOWN";
        } catch (Exception e) {
            logger.debug("Could not get process info for pid {}: {}", pid, e.getMessage());
            return "UNKNOWN";
        }
    }
    
    @Override
    public List<String> getAvailableCommands() {
        return Arrays.asList("snapshot", "backtrace", "memory", "threads", "signals", "attach", "detach", "list", "clear");
    }
    
    private String takeSnapshot(List<String> params) {
        if (params.isEmpty()) {
            return "Error: trap snapshot requires <output_file> [pid]";
        }
        
        String outputFile = params.get(0);
        String pid = params.size() > 1 ? params.get(1) : null;
        
        try {
            String snapshotId = generateSnapshotId();
            TrapSession session = new TrapSession(snapshotId, "snapshot", outputFile);
            activeTraps.put(snapshotId, session);
            
            // Take snapshot using gdb
            String result = takeGdbSnapshot(pid, outputFile);
            
            if (result != null && !result.contains("Error")) {
                return String.format("Snapshot taken successfully:\n" +
                                   "Snapshot ID: %s\n" +
                                   "Output file: %s\n" +
                                   "PID: %s", snapshotId, outputFile, pid != null ? pid : "auto-detected");
            } else {
                activeTraps.remove(snapshotId);
                return "Failed to take snapshot: " + result;
            }
            
        } catch (Exception e) {
            logger.error("Failed to take snapshot", e);
            return "Error taking snapshot: " + e.getMessage();
        }
    }
    
    private String getBacktrace(List<String> params) {
        if (params.isEmpty()) {
            return "Error: trap backtrace requires <output_file> [pid] [thread_id]";
        }
        
        String outputFile = params.get(0);
        String pid = params.size() > 1 ? params.get(1) : null;
        String threadId = params.size() > 2 ? params.get(2) : null;
        
        try {
            String trapId = generateTrapId();
            TrapSession session = new TrapSession(trapId, "backtrace", outputFile);
            activeTraps.put(trapId, session);
            
            // Get backtrace using gdb
            String result = getGdbBacktrace(pid, threadId, outputFile);
            
            if (result != null && !result.contains("Error")) {
                return String.format("Backtrace captured successfully:\n" +
                                   "Trap ID: %s\n" +
                                   "Output file: %s\n" +
                                   "PID: %s\n" +
                                   "Thread: %s", trapId, outputFile, 
                                   pid != null ? pid : "auto-detected",
                                   threadId != null ? threadId : "all");
            } else {
                activeTraps.remove(trapId);
                return "Failed to get backtrace: " + result;
            }
            
        } catch (Exception e) {
            logger.error("Failed to get backtrace", e);
            return "Error getting backtrace: " + e.getMessage();
        }
    }
    
    private String getMemoryInfo(List<String> params) {
        if (params.isEmpty()) {
            return "Error: trap memory requires <output_file> [pid]";
        }
        
        String outputFile = params.get(0);
        String pid = params.size() > 1 ? params.get(1) : null;
        
        try {
            String trapId = generateTrapId();
            TrapSession session = new TrapSession(trapId, "memory", outputFile);
            activeTraps.put(trapId, session);
            
            // Get memory info using gdb
            String result = getGdbMemoryInfo(pid, outputFile);
            
            if (result != null && !result.contains("Error")) {
                return String.format("Memory info captured successfully:\n" +
                                   "Trap ID: %s\n" +
                                   "Output file: %s\n" +
                                   "PID: %s", trapId, outputFile, 
                                   pid != null ? pid : "auto-detected");
            } else {
                activeTraps.remove(trapId);
                return "Failed to get memory info: " + result;
            }
            
        } catch (Exception e) {
            logger.error("Failed to get memory info", e);
            return "Error getting memory info: " + e.getMessage();
        }
    }
    
    private String getThreadInfo(List<String> params) {
        if (params.isEmpty()) {
            return "Error: trap threads requires <output_file> [pid]";
        }
        
        String outputFile = params.get(0);
        String pid = params.size() > 1 ? params.get(1) : null;
        
        try {
            String trapId = generateTrapId();
            TrapSession session = new TrapSession(trapId, "threads", outputFile);
            activeTraps.put(trapId, session);
            
            // Get thread info using gdb
            String result = getGdbThreadInfo(pid, outputFile);
            
            if (result != null && !result.contains("Error")) {
                return String.format("Thread info captured successfully:\n" +
                                   "Trap ID: %s\n" +
                                   "Output file: %s\n" +
                                   "PID: %s", trapId, outputFile, 
                                   pid != null ? pid : "auto-detected");
            } else {
                activeTraps.remove(trapId);
                return "Failed to get thread info: " + result;
            }
            
        } catch (Exception e) {
            logger.error("Failed to get thread info", e);
            return "Error getting thread info: " + e.getMessage();
        }
    }
    
    private String sendSignal(List<String> params) {
        if (params.size() < 2) {
            return "Error: trap signals requires <signal> <pid>";
        }
        
        String signal = params.get(0);
        String pid = params.get(1);
        
        try {
            // Send signal using kill command
            ProcessBuilder pb = new ProcessBuilder("kill", "-" + signal, pid);
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                return "Signal " + signal + " sent to process " + pid;
            } else {
                return "Failed to send signal (exit code: " + exitCode + ")";
            }
            
        } catch (Exception e) {
            logger.error("Failed to send signal", e);
            return "Error sending signal: " + e.getMessage();
        }
    }
    
    private String attachDebugger(List<String> params) {
        if (params.isEmpty()) {
            return "Error: trap attach requires <pid> [output_file]";
        }
        
        String pid = params.get(0);
        String outputFile = params.size() > 1 ? params.get(1) : null;
        
        try {
            String trapId = generateTrapId();
            TrapSession session = new TrapSession(trapId, "attach", outputFile);
            activeTraps.put(trapId, session);
            
            // Attach gdb to process
            String result = attachGdb(pid, outputFile);
            
            if (result != null && !result.contains("Error")) {
                return String.format("Debugger attached successfully:\n" +
                                   "Trap ID: %s\n" +
                                   "PID: %s\n" +
                                   "Output: %s", trapId, pid, 
                                   outputFile != null ? outputFile : "console");
            } else {
                activeTraps.remove(trapId);
                return "Failed to attach debugger: " + result;
            }
            
        } catch (Exception e) {
            logger.error("Failed to attach debugger", e);
            return "Error attaching debugger: " + e.getMessage();
        }
    }
    
    private String detachDebugger(List<String> params) {
        if (params.isEmpty()) {
            return "Error: trap detach requires <trap_id>";
        }
        
        String trapId = params.get(0);
        TrapSession session = activeTraps.get(trapId);
        
        if (session == null) {
            return "Trap not found: " + trapId;
        }
        
        try {
            // Detach gdb
            String result = detachGdb(trapId);
            
            if (result != null && !result.contains("Error")) {
                activeTraps.remove(trapId);
                return "Debugger detached: " + trapId;
            } else {
                return "Failed to detach debugger: " + result;
            }
            
        } catch (Exception e) {
            logger.error("Failed to detach debugger", e);
            return "Error detaching debugger: " + e.getMessage();
        }
    }
    
    private String listTraps(List<String> params) {
        if (activeTraps.isEmpty()) {
            return "No active traps";
        }
        
        StringBuilder list = new StringBuilder();
        list.append("Active Traps:\n");
        list.append(String.format("%-20s %-15s %-20s %-15s\n", "Trap ID", "Type", "Start Time", "Output"));
        list.append("-".repeat(75)).append("\n");
        
        for (TrapSession session : activeTraps.values()) {
            list.append(String.format("%-20s %-15s %-20s %-15s\n",
                session.getTrapId(),
                session.getType(),
                session.getStartTime(),
                session.getOutputFile() != null ? session.getOutputFile() : "console"
            ));
        }
        
        return list.toString();
    }
    
    private String clearTraps(List<String> params) {
        try {
            // Detach all debuggers
            for (String trapId : activeTraps.keySet()) {
                detachGdb(trapId);
            }
            
            activeTraps.clear();
            return "All traps cleared";
            
        } catch (Exception e) {
            logger.error("Failed to clear traps", e);
            return "Error clearing traps: " + e.getMessage();
        }
    }
    
    // Helper methods for gdb operations
    private String takeGdbSnapshot(String pid, String outputFile) throws IOException, InterruptedException {
        String actualPid = pid != null ? pid : getOpenSIPSPid();
        if (actualPid == null) {
            return "Error: Could not find OpenSIPS process";
        }
        
        String gdbScript = String.format(
            "attach %s\n" +
            "info registers\n" +
            "info stack\n" +
            "info threads\n" +
            "info memory\n" +
            "detach\n" +
            "quit\n", actualPid
        );
        
        return executeGdbScript(gdbScript, outputFile);
    }
    
    private String getGdbBacktrace(String pid, String threadId, String outputFile) throws IOException, InterruptedException {
        String actualPid = pid != null ? pid : getOpenSIPSPid();
        if (actualPid == null) {
            return "Error: Could not find OpenSIPS process";
        }
        
        // First, try to get the executable path from /proc/{pid}/exe
        String executablePath = getProcessExecutable(actualPid);
        if (executablePath == null) {
            return "Error: Could not find executable for process " + actualPid;
        }
        
        // Check if the executable exists
        if (!Files.exists(Paths.get(executablePath))) {
            return "Error: Executable not found: " + executablePath;
        }
        
        // Verify it's an OpenSIPS process
        String processName = getProcessName();
        String fileName = Paths.get(executablePath).getFileName().toString();
        if (!fileName.equals(processName)) {
            return "Error: Process " + actualPid + "/" + fileName + " is not OpenSIPS process";
        }
        
        logger.debug("Dumping backtrace for {} pid {}", executablePath, actualPid);
        
        // Use the same gdb command as Python: gdb executable pid -batch --eval-command "bt full"
        ProcessBuilder pb = new ProcessBuilder("gdb", executablePath, actualPid, "-batch", "--eval-command", "bt full");
        
        if (outputFile != null) {
            pb.redirectOutput(new File(outputFile));
        }
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode == 0) {
            return "Success";
        } else {
            return "GDB exited with code: " + exitCode;
        }
    }
    
    private String getProcessExecutable(String pid) {
        try {
            Path exePath = Paths.get("/proc", pid, "exe");
            if (Files.isSymbolicLink(exePath)) {
                return Files.readSymbolicLink(exePath).toString();
            }
        } catch (Exception e) {
            logger.debug("Could not read executable path for pid {}: {}", pid, e.getMessage());
        }
        return null;
    }
    
    private String getGdbMemoryInfo(String pid, String outputFile) throws IOException, InterruptedException {
        String actualPid = pid != null ? pid : getOpenSIPSPid();
        if (actualPid == null) {
            return "Error: Could not find OpenSIPS process";
        }
        
        String gdbScript = String.format(
            "attach %s\n" +
            "info memory\n" +
            "info proc mappings\n" +
            "detach\n" +
            "quit\n", actualPid
        );
        
        return executeGdbScript(gdbScript, outputFile);
    }
    
    private String getGdbThreadInfo(String pid, String outputFile) throws IOException, InterruptedException {
        String actualPid = pid != null ? pid : getOpenSIPSPid();
        if (actualPid == null) {
            return "Error: Could not find OpenSIPS process";
        }
        
        String gdbScript = String.format(
            "attach %s\n" +
            "info threads\n" +
            "thread apply all info registers\n" +
            "detach\n" +
            "quit\n", actualPid
        );
        
        return executeGdbScript(gdbScript, outputFile);
    }
    
    private String attachGdb(String pid, String outputFile) throws IOException, InterruptedException {
        String actualPid = pid != null ? pid : getOpenSIPSPid();
        if (actualPid == null) {
            return "Error: Could not find OpenSIPS process";
        }
        
        String gdbScript = String.format(
            "attach %s\n" +
            "set pagination off\n" +
            "continue\n", actualPid
        );
        
        return executeGdbScript(gdbScript, outputFile);
    }
    
    private String detachGdb(String trapId) throws IOException, InterruptedException {
        // This would need to be implemented with proper gdb session management
        return "Debugger detached";
    }
    
    private String executeGdbScript(String script, String outputFile) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("gdb", "-batch", "-x", "-");
        
        if (outputFile != null) {
            pb.redirectOutput(new File(outputFile));
        }
        
        Process process = pb.start();
        
        // Write script to gdb stdin
        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream())) {
            writer.write(script);
            writer.flush();
        }
        
        int exitCode = process.waitFor();
        if (exitCode == 0) {
            return "Success";
        } else {
            return "GDB exited with code: " + exitCode;
        }
    }
    
    private String getOpenSIPSPid() {
        try {
            ProcessBuilder pb = new ProcessBuilder("pgrep", "opensips");
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String pid = reader.readLine();
                if (pid != null) {
                    return pid.trim();
                }
            }
        } catch (Exception e) {
            logger.warn("Could not find OpenSIPS process", e);
        }
        return null;
    }
    
    // Helper methods
    private String generateSnapshotId() {
        return "snapshot_" + System.currentTimeMillis();
    }
    
    private String generateTrapId() {
        return "trap_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }
    
    // Inner class for trap sessions
    private static class TrapSession {
        private final String trapId;
        private final String type;
        private final String outputFile;
        private final String startTime;
        
        public TrapSession(String trapId, String type, String outputFile) {
            this.trapId = trapId;
            this.type = type;
            this.outputFile = outputFile;
            this.startTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        
        // Getters
        public String getTrapId() { return trapId; }
        public String getType() { return type; }
        public String getOutputFile() { return outputFile; }
        public String getStartTime() { return startTime; }
    }
}