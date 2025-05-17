# Method Chain Analysis and UML Sequence Diagram Generator

This IntelliJ IDEA plugin analyzes method call chains at the current cursor position and generates UML sequence diagrams. It can also enhance these diagrams using the DeepSeek API.

## Features

- Analyze method call chains starting from the method at the current cursor position
- Include interfaces and their implementations in the analysis
- Generate PlantUML sequence diagrams
- Enhance diagrams using DeepSeek API
- Copy diagrams to clipboard or save to file

## Actions

The plugin adds the following action to the "Generate" menu:

1. **Generate UML Flow Diagram** - Generates UML flow diagram from method code using AI

## Configuration

Configure the plugin in Settings > UmlFlowAiConfigurable:

- **API Key** - Your DeepSeek API key
- **UML Sequence Prompt** - The prompt template used to generate UML sequence diagrams

## How It Works

1. The plugin analyzes the method at the current cursor position
2. It builds a call stack by traversing the method call hierarchy
3. It generates a PlantUML sequence diagram from the call stack
4. It sends the diagram to DeepSeek API for enhancement (if configured)
5. It displays the diagram(s) in a dialog


### Flow Diagram Generation

The plugin generates flow diagrams in PlantUML format, which can be rendered by PlantUML tools or online services.


## Requirements

- IntelliJ IDEA 2023.2 or later
- Java 8 or later
- DeepSeek API key (for enhanced diagrams)
