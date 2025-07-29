package org.opensips.cli.communication;

/**
 * Exception thrown when communication with OpenSIPS fails
 */
public class CommunicationException extends Exception {
    
    public CommunicationException(String message) {
        super(message);
    }
    
    public CommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public CommunicationException(Throwable cause) {
        super(cause);
    }
}