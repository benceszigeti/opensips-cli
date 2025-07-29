package org.opensips.cli.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

/**
 * TLS certificate management module
 */
public class TLSModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(TLSModule.class);
    
    @Override
    public String getName() {
        return "tls";
    }
    
    @Override
    public String getDescription() {
        return "Manage TLS certificates";
    }
    
    @Override
    public String execute(String command) {
        CommandParseResult parsed = parseCommand(command);
        
        if (parsed.getCommand().isEmpty()) {
            return "Usage: tls <command> [parameters...]\n" +
                   "Available commands: " + String.join(", ", getAvailableCommands());
        }
        
        switch (parsed.getCommand()) {
            case "generate":
                return generateCertificate(parsed.getParams());
            case "install":
                return installCertificate(parsed.getParams());
            case "list":
                return listCertificates(parsed.getParams());
            case "remove":
                return removeCertificate(parsed.getParams());
            case "verify":
                return verifyCertificate(parsed.getParams());
            case "info":
                return getCertificateInfo(parsed.getParams());
            default:
                return "Unknown command: " + parsed.getCommand();
        }
    }
    
    @Override
    public List<String> getAvailableCommands() {
        return Arrays.asList("generate", "install", "list", "remove", "verify", "info");
    }
    
    private String generateCertificate(List<String> params) {
        if (params.size() < 3) {
            return "Error: tls generate requires <domain> <output_dir> <days_valid> [key_size] [country] [state] [city] [organization]";
        }
        
        String domain = params.get(0);
        String outputDir = params.get(1);
        int daysValid = Integer.parseInt(params.get(2));
        int keySize = params.size() > 3 ? Integer.parseInt(params.get(3)) : 2048;
        String country = params.size() > 4 ? params.get(4) : "US";
        String state = params.size() > 5 ? params.get(5) : "State";
        String city = params.size() > 6 ? params.get(6) : "City";
        String organization = params.size() > 7 ? params.get(7) : "OpenSIPS";
        
        try {
            Path outputPath = Paths.get(outputDir);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }
            
            // Generate key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(keySize, new SecureRandom());
            KeyPair keyPair = keyGen.generateKeyPair();
            
            // Generate certificate using OpenSSL
            String certFile = outputPath.resolve(domain + ".crt").toString();
            String keyFile = outputPath.resolve(domain + ".key").toString();
            String csrFile = outputPath.resolve(domain + ".csr").toString();
            
            // Create OpenSSL configuration
            String configFile = createOpenSSLConfig(domain, country, state, city, organization, outputPath);
            
            // Generate private key
            generatePrivateKey(keyFile, keySize);
            
            // Generate CSR
            generateCSR(keyFile, csrFile, configFile);
            
            // Generate self-signed certificate
            generateSelfSignedCert(keyFile, certFile, configFile, daysValid);
            
            // Clean up config file
            Files.deleteIfExists(Paths.get(configFile));
            
            return String.format("Certificate generated successfully:\n" +
                               "Certificate: %s\n" +
                               "Private Key: %s\n" +
                               "CSR: %s\n" +
                               "Valid for: %d days", certFile, keyFile, csrFile, daysValid);
            
        } catch (Exception e) {
            logger.error("Failed to generate certificate", e);
            return "Error generating certificate: " + e.getMessage();
        }
    }
    
    private String installCertificate(List<String> params) {
        if (params.size() < 3) {
            return "Error: tls install requires <cert_file> <key_file> <opensips_config_dir> [ca_file]";
        }
        
        String certFile = params.get(0);
        String keyFile = params.get(1);
        String configDir = params.get(2);
        String caFile = params.size() > 3 ? params.get(3) : null;
        
        try {
            Path configPath = Paths.get(configDir);
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath);
            }
            
            // Copy certificate files
            Path destCert = configPath.resolve("server.crt");
            Path destKey = configPath.resolve("server.key");
            
            Files.copy(Paths.get(certFile), destCert);
            Files.copy(Paths.get(keyFile), destKey);
            
            if (caFile != null) {
                Path destCA = configPath.resolve("ca.crt");
                Files.copy(Paths.get(caFile), destCA);
            }
            
            // Set proper permissions
            destCert.toFile().setReadable(true, true);
            destKey.toFile().setReadable(true, true);
            destKey.toFile().setWritable(false, false);
            
            return "Certificate installed successfully to " + configDir;
            
        } catch (Exception e) {
            logger.error("Failed to install certificate", e);
            return "Error installing certificate: " + e.getMessage();
        }
    }
    
    private String listCertificates(List<String> params) {
        String certDir = params.size() > 0 ? params.get(0) : "/etc/opensips/tls";
        
        try {
            Path certPath = Paths.get(certDir);
            if (!Files.exists(certPath)) {
                return "Certificate directory not found: " + certDir;
            }
            
            StringBuilder result = new StringBuilder();
            result.append("Certificates in ").append(certDir).append(":\n");
            
            Files.list(certPath)
                .filter(path -> path.toString().endsWith(".crt") || path.toString().endsWith(".pem"))
                .forEach(path -> {
                    try {
                        String info = getCertificateInfo(path.toString());
                        result.append("- ").append(path.getFileName()).append("\n");
                    } catch (Exception e) {
                        result.append("- ").append(path.getFileName()).append(" (error reading)\n");
                    }
                });
            
            return result.toString();
            
        } catch (Exception e) {
            logger.error("Failed to list certificates", e);
            return "Error listing certificates: " + e.getMessage();
        }
    }
    
    private String removeCertificate(List<String> params) {
        if (params.size() < 1) {
            return "Error: tls remove requires <cert_file>";
        }
        
        String certFile = params.get(0);
        
        try {
            Path certPath = Paths.get(certFile);
            if (!Files.exists(certPath)) {
                return "Certificate file not found: " + certFile;
            }
            
            Files.delete(certPath);
            
            // Also try to remove associated key file
            String keyFile = certFile.replace(".crt", ".key").replace(".pem", ".key");
            Path keyPath = Paths.get(keyFile);
            if (Files.exists(keyPath)) {
                Files.delete(keyPath);
            }
            
            return "Certificate removed: " + certFile;
            
        } catch (Exception e) {
            logger.error("Failed to remove certificate", e);
            return "Error removing certificate: " + e.getMessage();
        }
    }
    
    private String verifyCertificate(List<String> params) {
        if (params.size() < 1) {
            return "Error: tls verify requires <cert_file>";
        }
        
        String certFile = params.get(0);
        
        try {
            // Use OpenSSL to verify certificate
            ProcessBuilder pb = new ProcessBuilder("openssl", "x509", "-in", certFile, "-text", "-noout");
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return "Certificate verification successful:\n" + output.toString();
            } else {
                return "Certificate verification failed";
            }
            
        } catch (Exception e) {
            logger.error("Failed to verify certificate", e);
            return "Error verifying certificate: " + e.getMessage();
        }
    }
    
    private String getCertificateInfo(List<String> params) {
        if (params.size() < 1) {
            return "Error: tls info requires <cert_file>";
        }
        
        String certFile = params.get(0);
        return getCertificateInfo(certFile);
    }
    
    private String getCertificateInfo(String certFile) {
        try {
            // Use OpenSSL to get certificate info
            ProcessBuilder pb = new ProcessBuilder("openssl", "x509", "-in", certFile, "-noout", "-subject", "-issuer", "-dates");
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return "Certificate information:\n" + output.toString();
            } else {
                return "Failed to read certificate information";
            }
            
        } catch (Exception e) {
            logger.error("Failed to get certificate info", e);
            return "Error getting certificate info: " + e.getMessage();
        }
    }
    
    // Helper methods for certificate generation
    private String createOpenSSLConfig(String domain, String country, String state, String city, String organization, Path outputPath) throws IOException {
        String configContent = String.format(
            "[req]\n" +
            "distinguished_name = req_distinguished_name\n" +
            "req_extensions = v3_req\n" +
            "prompt = no\n" +
            "\n" +
            "[req_distinguished_name]\n" +
            "C = %s\n" +
            "ST = %s\n" +
            "L = %s\n" +
            "O = %s\n" +
            "OU = OpenSIPS\n" +
            "CN = %s\n" +
            "\n" +
            "[v3_req]\n" +
            "keyUsage = keyEncipherment, dataEncipherment\n" +
            "extendedKeyUsage = serverAuth\n" +
            "subjectAltName = @alt_names\n" +
            "\n" +
            "[alt_names]\n" +
            "DNS.1 = %s\n" +
            "DNS.2 = *.%s\n",
            country, state, city, organization, domain, domain, domain
        );
        
        Path configFile = outputPath.resolve("openssl.conf");
        Files.write(configFile, configContent.getBytes());
        return configFile.toString();
    }
    
    private void generatePrivateKey(String keyFile, int keySize) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("openssl", "genrsa", "-out", keyFile, String.valueOf(keySize));
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Failed to generate private key");
        }
    }
    
    private void generateCSR(String keyFile, String csrFile, String configFile) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("openssl", "req", "-new", "-key", keyFile, "-out", csrFile, "-config", configFile);
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Failed to generate CSR");
        }
    }
    
    private void generateSelfSignedCert(String keyFile, String certFile, String configFile, int daysValid) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("openssl", "x509", "-req", "-in", certFile.replace(".crt", ".csr"), 
                                             "-signkey", keyFile, "-out", certFile, "-days", String.valueOf(daysValid), 
                                             "-extensions", "v3_req", "-extfile", configFile);
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Failed to generate self-signed certificate");
        }
    }
}