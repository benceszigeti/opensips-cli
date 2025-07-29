package org.opensips.cli.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * FIFO communication handler using named pipes
 */
public class FifoCommunicationHandler implements CommunicationHandler {
    private static final Logger logger = LoggerFactory.getLogger(FifoCommunicationHandler.class);
    
    private String fifoFile;
    private String fifoFileFallback;
    private String fifoReplyDir;
    private boolean valid = false;
    private String validationError = null;
    private long timeoutMs = 10000; // Default timeout in milliseconds
    private ObjectMapper objectMapper;
    private ExecutorService executor;
    
    public FifoCommunicationHandler() {
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    @Override
    public void initialize(Map<String, String> config) throws CommunicationException {
        this.fifoFile = config.getOrDefault("fifo_file", "/var/run/opensips/opensips_fifo");
        this.fifoFileFallback = config.getOrDefault("fifo_file_fallback", "/tmp/opensips_fifo");
        this.fifoReplyDir = config.getOrDefault("fifo_reply_dir", "/tmp");
        
        // Get timeout from config (convert seconds to milliseconds)
        int timeoutSeconds = Integer.parseInt(config.getOrDefault("timeout", "10"));
        this.timeoutMs = timeoutSeconds * 1000;
        
        logger.debug("Initialized FIFO communication handler with fifo_file: {}, fallback: {}, reply_dir: {}, timeout: {}ms", 
                    fifoFile, fifoFileFallback, fifoReplyDir, timeoutMs);
    }
    
    @Override
    public String execute(String command, List<String> params) throws CommunicationException {
        String actualFifoFile = findFifoFile();
        if (actualFifoFile == null) {
            throw new CommunicationException("No FIFO file found. Tried: " + fifoFile + " and " + fifoFileFallback);
        }
        
        // Create unique reply FIFO
        String replyFifoName = "opensips_cli_reply_" + UUID.randomUUID().toString().replace("-", "");
        Path replyFifoPath = Paths.get(fifoReplyDir, replyFifoName);
        
        try {
            // Create reply FIFO
            createFifo(replyFifoPath);
            
            // Create JSON-RPC request
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("method", command);
            request.put("id", 1);
            
            // Add parameters
            if (params != null && !params.isEmpty()) {
                request.set("params", objectMapper.valueToTree(params));
            } else {
                request.set("params", objectMapper.createArrayNode());
            }
            
            // Add reply FIFO to request
            request.put("reply_fifo", replyFifoPath.toString());
            
            String jsonRequest = objectMapper.writeValueAsString(request);
            logger.debug("Sending FIFO JSON-RPC request: {}", jsonRequest);
            
            // Write command to FIFO
            writeToFifo(actualFifoFile, jsonRequest);
            
            // Read response from reply FIFO with timeout
            String response = readFromFifoWithTimeout(replyFifoPath.toString());
            
            logger.debug("Received FIFO response: {}", response);
            
            // Parse JSON-RPC response
            try {
                JsonNode responseNode = objectMapper.readTree(response);
                if (responseNode.has("error")) {
                    throw new CommunicationException("JSON-RPC error: " + responseNode.get("error").toString());
                }
                if (responseNode.has("result")) {
                    return objectMapper.writeValueAsString(responseNode.get("result"));
                } else {
                    return response;
                }
            } catch (IOException e) {
                // If response is not JSON, return as-is
                return response;
            }
            
        } catch (IOException e) {
            throw new CommunicationException("FIFO communication failed", e);
        } finally {
            // Clean up reply FIFO
            cleanupFifo(replyFifoPath);
        }
    }
    
    private String findFifoFile() {
        if (Files.exists(Paths.get(fifoFile))) {
            return fifoFile;
        }
        if (Files.exists(Paths.get(fifoFileFallback))) {
            return fifoFileFallback;
        }
        return null;
    }
    
    private void createFifo(Path fifoPath) throws IOException {
        try {
            // Try to create FIFO using mkfifo command
            ProcessBuilder pb = new ProcessBuilder("mkfifo", fifoPath.toString());
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                // Fallback: try to create using Java NIO (may not work on all systems)
                try {
                    Files.createFile(fifoPath);
                } catch (IOException e) {
                    throw new IOException("Failed to create FIFO: " + fifoPath, e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while creating FIFO", e);
        }
    }
    
    private void writeToFifo(String fifoFile, String command) throws IOException {
        try (FileWriter writer = new FileWriter(fifoFile)) {
            writer.write(command + "\n");
            writer.flush();
        }
    }
    
    private String readFromFifoWithTimeout(String fifoFile) throws IOException {
        Future<String> future = executor.submit(() -> {
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(fifoFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
            }
            return response.toString().trim();
        });
        
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IOException("FIFO read timeout after " + timeoutMs + " ms");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("FIFO read interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException("FIFO read failed", e.getCause());
        }
    }
    
    private void cleanupFifo(Path fifoPath) {
        try {
            Files.deleteIfExists(fifoPath);
        } catch (IOException e) {
            logger.warn("Failed to cleanup FIFO: {}", fifoPath, e);
        }
    }
    
    private String escapeParam(String param) {
        // Basic escaping for FIFO parameters
        return param.replace(" ", "\\ ").replace("\"", "\\\"");
    }
    
    @Override
    public boolean isValid() {
        if (valid) {
            return true;
        }
        
        String actualFifoFile = findFifoFile();
        if (actualFifoFile == null) {
            valid = false;
            validationError = "No FIFO file found";
            return false;
        }
        
        try {
            // Test with a simple command
            execute("ps", List.of());
            valid = true;
            validationError = null;
            return true;
        } catch (CommunicationException e) {
            valid = false;
            validationError = e.getMessage();
            return false;
        }
    }
    
    @Override
    public String getValidationError() {
        return validationError;
    }
    
    @Override
    public void close() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}