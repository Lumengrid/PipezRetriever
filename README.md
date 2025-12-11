# Pipez Retriever

An addon mod for [Pipez](https://www.curseforge.com/minecraft/mc-mods/pipez) that adds **Retrieve Mode** to pipes, allowing you to pull items, fluids, and energy FROM connected inventories instead of pushing TO them.

## Features

### Retrieve Mode Toggle
- Adds an **Extract/Retrieve** button to the pipe configuration UI (top-left)
- **Extract Mode** (default): Push items/fluids/energy from the source to connected destinations
- **Retrieve Mode**: Pull items/fluids/energy from connected destinations into the source

### Full Configuration Support
Retrieve mode works with all existing Pipez configuration options:

| Feature | Description |
|---------|-------------|
| **Redstone Control** | Ignored, Off When Powered, On When Powered, Always Off |
| **Distribution** | Nearest First, Furthest First, Round Robin, Random |
| **Filter Mode** | Whitelist / Blacklist |
| **Filters** | Full support for item/fluid filters with tags |

### Supported Pipe Types
- ✅ Item Pipes
- ✅ Fluid Pipes
- ✅ Energy Pipes
- ✅ Gas Pipes (requires [Mekanism](https://www.curseforge.com/minecraft/mc-mods/mekanism))
- ✅ Universal Pipes

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.77+
- [Pipez](https://www.curseforge.com/minecraft/mc-mods/pipez) 1.21.1-1.2.19+

### Optional Dependencies
- [Mekanism](https://www.curseforge.com/minecraft/mc-mods/mekanism) - Required for Gas Pipe retrieve mode support

## Installation

1. Download and install [NeoForge](https://neoforged.net/) for Minecraft 1.21.1
2. Install [Pipez](https://www.curseforge.com/minecraft/mc-mods/pipez)
3. Place the Pipez Retriever JAR in your `mods` folder
4. Launch the game

## Usage

1. Place a pipe and connect it to an inventory
2. Use a **Wrench** (shift + right-click) to set an extraction point
3. Insert an **Upgrade** (Basic or higher) into the extraction point
4. Open the pipe configuration UI by right-clicking the extraction point
5. Click the **Extract/Retrieve** button at the top to toggle modes:
   - **Extract**: Items flow OUT of the source inventory
   - **Retrieve**: Items flow INTO the source inventory from connected destinations

### Example Use Cases

**Retrieve Mode is perfect for:**
- Collecting items from multiple chests into a central storage
- Draining fluids from tanks scattered around your base
- Gathering energy from multiple generators into a battery bank
- Collecting gases from Mekanism machines into central tanks
- Creating item/fluid/gas return systems

## Upgrade Compatibility

The Retrieve Mode button is **always enabled**, regardless of upgrade level. The transfer rates follow the standard Pipez tier system:

| Upgrade | Transfer Rate |
|---------|---------------|
| None | Default rate |
| Basic | Basic rate |
| Improved | Improved rate |
| Advanced | Advanced rate |
| Ultimate | Ultimate rate |
| Infinity | Maximum rate |

## Building from Source

```bash
git clone https://github.com/yourusername/pipezretriever.git
cd pipezretriever
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## License

All Rights Reserved

## Credits

- **Pipez** by [henkelmax](https://www.curseforge.com/members/henkelmax) - The amazing pipe mod this addon extends
- **Lumengrid** - Pipez Retriever development
