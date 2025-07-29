package org.opensips.cli.communication;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * HTTP communication handler using JSON-RPC
 */
public class HttpCommunicationHandler implements CommunicationHandler {
    private static final Logger logger = LoggerFactory.getLogger(HttpCommunicationHandler.class);
    
    private CloseableHttpClient httpClient;
    private String url;
    private ObjectMapper objectMapper;
    private boolean valid = false;
    private String validationError = null;
    
    public HttpCommunicationHandler() {
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public void initialize(Map<String, String> config) throws CommunicationException {
        this.url = config.getOrDefault("url", "http://127.0.0.1:8888/mi");
        logger.debug("Initialized HTTP communication handler with URL: {}", url);
    }
    
    @Override
    public String execute(String command, List<String> params) throws CommunicationException {
        try {
            // Create JSON-RPC request
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("method", command);
            request.put("id", 1);
            
            // Add parameters if any
            if (params != null && !params.isEmpty()) {
                ArrayNode paramsArray = objectMapper.createArrayNode();
                for (String param : params) {
                    paramsArray.add(param);
                }
                request.set("params", paramsArray);
            }
            
            String requestJson = objectMapper.writeValueAsString(request);
            logger.debug("Sending JSON-RPC request: {}", requestJson);
            
            // Create HTTP POST request
            HttpPost httpPost = new HttpPost(url);
            httpPost.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));
            
            // Execute request
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                logger.debug("Received response: {}", responseBody);
                
                // Parse JSON-RPC response
                JsonNode responseNode;
                try {
                    responseNode = objectMapper.readTree(responseBody);
                } catch (IOException e) {
                    throw new CommunicationException("Failed to parse JSON response", e);
                }
                
                if (responseNode.has("error")) {
                    JsonNode error = responseNode.get("error");
                    String errorMessage = error.has("message") ? 
                        error.get("message").asText() : "Unknown error";
                    throw new CommunicationException("JSON-RPC error: " + errorMessage);
                }
                
                if (responseNode.has("result")) {
                    JsonNode result = responseNode.get("result");
                    try {
                        return objectMapper.writeValueAsString(result);
                    } catch (IOException e) {
                        throw new CommunicationException("Failed to serialize result", e);
                    }
                } else {
                    return responseBody;
                }
            }
            
        } catch (IOException | org.apache.hc.core5.http.ParseException e) {
            throw new CommunicationException("HTTP communication failed", e);
        }
    }
    
    @Override
    public boolean isValid() {
        if (valid) {
            return true;
        }
        
        try {
            // Try to execute a simple command to test connection
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
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (IOException e) {
            logger.warn("Error closing HTTP client", e);
        }
    }
}