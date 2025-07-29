package org.opensips.cli.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Datagram communication handler using UDP
 */
public class DatagramCommunicationHandler implements CommunicationHandler {
    private static final Logger logger = LoggerFactory.getLogger(DatagramCommunicationHandler.class);
    
    private String datagramIp;
    private int datagramPort;
    private String datagramUnixSocket;
    private DatagramSocket socket;
    private boolean valid = false;
    private String validationError = null;
    private ObjectMapper objectMapper;
    
    public DatagramCommunicationHandler() {
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public void initialize(Map<String, String> config) throws CommunicationException {
        this.datagramIp = config.getOrDefault("datagram_ip", "127.0.0.1");
        this.datagramPort = Integer.parseInt(config.getOrDefault("datagram_port", "8080"));
        this.datagramUnixSocket = config.getOrDefault("datagram_unix_socket", "/tmp/opensips.sock");
        
        // Get timeout from config (convert seconds to milliseconds)
        int timeoutSeconds = Integer.parseInt(config.getOrDefault("timeout", "5"));
        int timeoutMs = timeoutSeconds * 1000;
        
        try {
            this.socket = new DatagramSocket();
            this.socket.setSoTimeout(timeoutMs);
        } catch (SocketException e) {
            throw new CommunicationException("Failed to create datagram socket", e);
        }
        
        logger.debug("Initialized datagram communication handler with IP: {}, port: {}, timeout: {}ms", 
                    datagramIp, datagramPort, timeoutMs);
    }
    
    @Override
    public String execute(String command, List<String> params) throws CommunicationException {
        try {
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
            
            String jsonRequest = objectMapper.writeValueAsString(request);
            logger.debug("Sending datagram JSON-RPC request: {}", jsonRequest);
            
            // Send command
            byte[] sendData = jsonRequest.getBytes(StandardCharsets.UTF_8);
            InetAddress address = InetAddress.getByName(datagramIp);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, datagramPort);
            socket.send(sendPacket);
            
            // Receive response
            byte[] receiveData = new byte[4096];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(receivePacket);
            
            String response = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
            logger.debug("Received datagram response: {}", response);
            
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
            throw new CommunicationException("Datagram communication failed", e);
        }
    }
    
    @Override
    public boolean isValid() {
        if (valid) {
            return true;
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
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}