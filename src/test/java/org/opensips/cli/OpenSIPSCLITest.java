package org.opensips.cli;

import org.junit.jupiter.api.Test;
import org.opensips.cli.config.Configuration;
import org.opensips.cli.modules.Module;
import org.opensips.cli.modules.ModuleManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for OpenSIPS CLI
 */
public class OpenSIPSCLITest {
    
    @Test
    public void testVersion() {
        assertNotNull(Version.getVersion());
        assertNotNull(Version.getFullVersion());
        assertTrue(Version.getFullVersion().contains("OpenSIPS CLI Java"));
    }
    
    @Test
    public void testConfiguration() {
        Configuration config = new Configuration();
        config.setDebug(true);
        config.setInstance("test");
        
        assertTrue(config.isDebug());
        assertEquals("test", config.getInstance());
    }
    
    @Test
    public void testModuleManager() {
        ModuleManager manager = new ModuleManager();
        
        assertNotNull(manager.getModule("mi"));
        assertNotNull(manager.getModule("instance"));
        assertNotNull(manager.getModule("user"));
        
        assertTrue(manager.getAvailableModules().contains("mi"));
        assertTrue(manager.getAvailableModules().contains("instance"));
        assertTrue(manager.getAvailableModules().contains("user"));
    }
    
    @Test
    public void testMICommand() {
        ModuleManager manager = new ModuleManager();
        Module miModule = manager.getModule("mi");
        
        assertNotNull(miModule);
        assertEquals("mi", miModule.getName());
        assertEquals("Execute Management Interface commands", miModule.getDescription());
        
        // Test command execution (should return usage info for empty command)
        String result = miModule.execute("");
        assertNotNull(result);
        assertTrue(result.contains("Usage: mi"));
    }
}