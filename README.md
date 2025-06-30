# AI UML Flow Diagram Generator

This IntelliJ IDEA plugin analyzes Java method code and generates UML flow diagrams using AI technology. It supports multiple AI providers and provides an intuitive interface for configuration and usage.

## Installation

You can find the pre-built plugin package in the `distributions` directory of this repository.

## ✨ Features

- **🎯 Smart Flow Diagram Generation**: Automatically generate UML activity diagrams from Java method code
- **🤖 Multi-AI Support**: Compatible with DeepSeek, OpenAI, Anthropic, and other major AI providers
- **📊 Visual Integration**: View generated diagrams directly within IntelliJ IDEA
- **🎨 Customizable**: Support for custom prompts and configuration patterns
- **💾 Export Options**: Save diagrams as PlantUML files or export as images
- **🖼️ Enhanced UI**: Modern, user-friendly interface with comprehensive guidance
- **🔧 Tool Window**: Dedicated tool window with welcome page and quick access to features

## Actions

The plugin adds the following action to the "Generate" menu:

1. **Generate UML Flow Diagram** - Generates UML flow diagram from method code using AI

## 🚀 Quick Start

### 1. Configuration
Open **Settings > Tools > UmlFlowAiConfigurable** to configure:

#### 🔑 AI Model Configuration
- **DeepSeek API Key** - For DeepSeek AI models
- **OpenAI API Key** - For GPT models
- **Anthropic API Key** - For Claude models
- **Other AI Providers** - Additional AI service configurations

#### ⚙️ General Settings
- **PlantUML Path** - Local PlantUML installation path (optional, for image export)
- **Flow Diagram Prompt** - Custom prompt template for diagram generation
- **Class Patterns** - Include/exclude patterns for class scanning

### 2. Usage
1. **Position Cursor**: Place your cursor inside a Java method
2. **Generate Diagram**: Right-click → Generate → "Generate UML Flow Diagram" or use Alt+Insert
3. **View Result**: The generated diagram will appear in a popup window
4. **Export**: Save as .puml file or export as image (requires PlantUML)

### 3. Tool Window
Access the **UML Flow Diagram** tool window for:
- Feature overview and usage instructions
- Quick access to configuration settings
- Help and documentation links

## 🔧 How It Works

1. **Code Analysis**: The plugin analyzes the Java method at the cursor position
2. **AI Processing**: Selected AI model processes the code to understand logic flow
3. **Diagram Generation**: AI generates PlantUML activity diagram code
4. **Visualization**: The diagram is rendered and displayed in the IDE
5. **Export Options**: Save or export the diagram in various formats

## 📋 Requirements

- **IntelliJ IDEA**: 2023.2 or later
- **Java**: 8 or later
- **AI API Key**: At least one configured AI provider
- **PlantUML** (optional): For local image generation

## 🆕 What's New

### Latest UI Enhancement Update
- **🎨 Redesigned Configuration Interface**: Modern, organized panels with better user experience
- **🖼️ Enhanced Tool Window**: Comprehensive welcome page with feature guides and quick access buttons
- **⚡ Improved Workflow**: Streamlined configuration process and better visual feedback
- **🔧 Better Organization**: Logical separation of settings into General, AI Models, Prompts, and Patterns
- **📱 Modern Design**: Consistent styling with emoji icons and clear visual hierarchy

See [CHANGELOG.md](CHANGELOG.md) for detailed release notes and [RELEASE_NOTES.md](RELEASE_NOTES.md) for comprehensive update information.

## 📞 Support

For questions, issues, or feature requests:
- **Email**: yifengkuaijian@gmail.com
- **Issues**: Create an issue in this repository
- **Documentation**: Check the tool window welcome page for usage guidance

## 📄 License

This project is licensed under the terms specified in the [LICENSE](LICENSE) file.
