# OpenSIPS CLI - Java Port

This is a Java port of the OpenSIPS CLI (Command Line Interface) tool. It provides an interactive command-line interface for controlling and monitoring OpenSIPS SIP servers using the Management Interface (MI) over JSON-RPC.

## Features

- **Interactive CLI**: Full-featured command-line interface with auto-completion and command history
- **Multiple Transport Methods**: Support for HTTP, FIFO, and datagram communication (HTTP fully implemented)
- **Modular Design**: Extensible module system for different functionalities
- **Configuration Management**: Support for multiple instances and configuration files
- **JSON-RPC Support**: Native support for OpenSIPS 3.0+ JSON-RPC interface

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher
- OpenSIPS 3.0 or higher (for JSON-RPC support)

## Building

```bash
mvn clean package
```

This will create a shaded JAR file in the `target/` directory that includes all dependencies.

## Running

### Interactive Mode
```bash
java -jar target/opensips-cli-1.0.0.jar
```

### Execute Command and Exit
```bash
java -jar target/opensips-cli-1.0.0.jar -x "mi ps"
```

### With Configuration File
```bash
java -jar target/opensips-cli-1.0.0.jar -f /path/to/config.cfg
```

### With Specific Instance
```bash
java -jar target/opensips-cli-1.0.0.jar -i production
```

## Command Line Options

- `-h, --help`: Display help information
- `-v, --version`: Display version information
- `-d, --debug`: Enable debug mode
- `-f, --config`: Specify configuration file
- `-i, --instance`: Choose OpenSIPS instance
- `-o, --option`: Set configuration option (KEY=VALUE)
- `-p, --print`: Print configuration
- `-x, --execute`: Execute command and exit

## Configuration

The CLI supports configuration files in INI format. Configuration files are searched in the following order:

1. `~/.opensips-cli.cfg`
2. `/etc/opensips-cli.cfg`
3. `/etc/opensips/opensips-cli.cfg`

### Configuration Options

#### Core Settings
- `prompt_name`: CLI prompt name (default: opensips-cli)
- `prompt_intro`: Introduction message
- `prompt_emptyline_repeat_cmd`: Repeat last command on empty line (default: false)
- `history_file`: History file path
- `history_file_size`: History file size limit
- `log_level`: Logging level

#### Communication Settings
- `communication_type`: Transport method (http, fifo, datagram)
- `url`: HTTP URL for JSON-RPC (default: http://127.0.0.1:8888/mi)
- `fifo_file`: FIFO file path
- `datagram_ip`: Datagram IP address
- `datagram_port`: Datagram port

## Modules

The CLI includes the following modules:

### Management Interface (mi)
Execute OpenSIPS MI commands directly:
```
mi ps
mi uptime
mi get_statistics
```

### Instance Management (instance)
Manage different OpenSIPS instances:
```
instance list
instance switch production
instance current
```

### User Management (user)
Manage OpenSIPS users:
```
user add username password
user remove username
user list
```

### Database Management (database)
Manage OpenSIPS database:
```
database create
database migrate
database backup
```

### Diagnostics (diagnose)
Diagnose OpenSIPS instances:
```
diagnose status
diagnose health
diagnose performance
```

### Call Tracing (trace)
Trace call information:
```
trace start
trace stop
trace status
```

### Debug Traps (trap)
Debug functionality:
```
trap snapshot
trap backtrace
trap memory
```

### TLS Management (tls)
Manage TLS certificates:
```
tls generate
tls install
tls list
```

## Development

### Project Structure
```
src/main/java/org/opensips/cli/
├── Main.java                    # Main entry point
├── OpenSIPSCLI.java            # Main CLI class
├── Version.java                # Version information
├── config/                     # Configuration management
├── communication/              # Communication handlers
├── modules/                    # CLI modules
└── utils/                      # Utility classes
```

### Adding New Modules

1. Create a new class extending `AbstractModule`
2. Implement the required methods
3. Register the module in `ModuleManager`

Example:
```java
public class MyModule extends AbstractModule {
    @Override
    public String getName() {
        return "mymodule";
    }
    
    @Override
    public String getDescription() {
        return "My custom module";
    }
    
    @Override
    public String execute(String command) {
        // Implementation
        return "Result";
    }
    
    @Override
    public List<String> getAvailableCommands() {
        return Arrays.asList("command1", "command2");
    }
}
```

### Adding New Communication Handlers

1. Implement the `CommunicationHandler` interface
2. Add the handler to `CommunicationManager`

## Differences from Python Version

- **Language**: Java instead of Python
- **Dependencies**: Maven instead of pip
- **Packaging**: JAR file instead of Python package
- **Terminal**: JLine instead of readline
- **Configuration**: Apache Commons Configuration instead of configparser
- **HTTP Client**: Apache HttpClient instead of requests
- **JSON**: Jackson instead of json

## License

This project is licensed under the GNU General Public License v3.0, same as the original Python version.

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues for bugs and feature requests.

## Status

This is a work-in-progress port. Not all features from the original Python version are fully implemented yet:

- ✅ Basic CLI framework
- ✅ HTTP communication
- ✅ Configuration management
- ✅ Module system
- ✅ Command completion
- ❌ FIFO communication
- ❌ Datagram communication
- ❌ Full database module implementation
- ❌ Advanced diagnostic features
- ❌ TLS certificate management
- ❌ Call tracing functionality