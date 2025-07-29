package org.opensips.cli.modules;

import org.opensips.cli.communication.CommunicationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages all OpenSIPS CLI modules
 */
public class ModuleManager {
    private static final Logger logger = LoggerFactory.getLogger(ModuleManager.class);
    
    private Map<String, Module> modules = new HashMap<>();
    private CommunicationManager commManager;
    
    public ModuleManager() {
        // Initialize with default modules
        registerModule(new ManagementInterfaceModule());
        registerModule(new InstanceModule());
        registerModule(new UserModule());
        registerModule(new DatabaseModule());
        registerModule(new DiagnoseModule());
        registerModule(new TraceModule());
        registerModule(new TrapModule());
        registerModule(new TLSModule());
    }
    
    /**
     * Initialize module manager with communication manager
     */
    public void initialize(CommunicationManager commManager) {
        this.commManager = commManager;
        
        // Initialize all modules
        for (Module module : modules.values()) {
            if (module instanceof AbstractModule) {
                ((AbstractModule) module).setCommunicationManager(commManager);
            }
        }
        
        logger.debug("Initialized {} modules", modules.size());
    }
    
    /**
     * Register a module
     */
    public void registerModule(Module module) {
        if (module.isExcluded()) {
            logger.debug("Excluding module {}: {}", module.getName(), module.getExclusionReason());
            return;
        }
        
        modules.put(module.getName(), module);
        logger.debug("Registered module: {}", module.getName());
    }
    
    /**
     * Get a module by name
     */
    public Module getModule(String name) {
        return modules.get(name);
    }
    
    /**
     * Get all available modules
     */
    public List<String> getAvailableModules() {
        return modules.keySet().stream().sorted().collect(Collectors.toList());
    }
    
    /**
     * Get all modules
     */
    public Map<String, Module> getAllModules() {
        return new HashMap<>(modules);
    }
    
    /**
     * Get completions for a module
     */
    public List<String> getModuleCompletions(String moduleName, String partialCommand) {
        Module module = modules.get(moduleName);
        if (module != null) {
            return module.getCompletions(partialCommand);
        }
        return List.of();
    }
}