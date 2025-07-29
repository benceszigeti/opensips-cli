package org.opensips.cli;

/**
 * Version information for OpenSIPS CLI Java port
 */
public class Version {
    public static final String VERSION = "1.0.0";
    public static final String BUILD_DATE = "2024-01-01";
    public static final String JAVA_VERSION = System.getProperty("java.version");
    
    private Version() {
        // Utility class, prevent instantiation
    }
    
    public static String getVersion() {
        return VERSION;
    }
    
    public static String getFullVersion() {
        return "OpenSIPS CLI Java " + VERSION + " (Java " + JAVA_VERSION + ")";
    }
}