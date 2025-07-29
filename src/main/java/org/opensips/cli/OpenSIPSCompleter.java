package org.opensips.cli;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.opensips.cli.modules.Module;
import org.opensips.cli.modules.ModuleManager;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Command completion for OpenSIPS CLI
 */
public class OpenSIPSCompleter implements Completer {
    
    private final ModuleManager moduleManager;
    
    public OpenSIPSCompleter(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }
    
    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line();
        int cursor = line.cursor();
        
        // Split the buffer into words
        String[] words = buffer.substring(0, cursor).split("\\s+");
        
        if (words.length == 0 || (words.length == 1 && buffer.endsWith(" "))) {
            // Complete module names
            List<String> modules = moduleManager.getAvailableModules();
            for (String module : modules) {
                candidates.add(new Candidate(module, module, null, null, null, null, true));
            }
        } else if (words.length >= 1) {
            String moduleName = words[0];
            Module module = moduleManager.getModule(moduleName);
            
            if (module != null && words.length >= 2) {
                // Complete module commands
                String partialCommand = words[1];
                List<String> completions = module.getCompletions(partialCommand);
                
                for (String completion : completions) {
                    candidates.add(new Candidate(completion, completion, null, null, null, null, true));
                }
            }
        }
    }
}