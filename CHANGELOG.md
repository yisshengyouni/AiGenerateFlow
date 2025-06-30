# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- Enhanced UML Flow Tool Window with comprehensive welcome page
- Tool window icon integration for better visual identification
- Interactive configuration and help buttons in tool window
- Detailed feature introduction and usage guide sections
- Multi-section layout with feature highlights, usage instructions, and configuration guidance

### Changed
- Completely redesigned AI configuration interface with improved user experience
- Reorganized configuration panels into logical sections (General, AI Models, Prompts, Patterns)
- Unified UI design with consistent styling across all configuration panels
- Replaced form-based UI with programmatic component creation for better maintainability
- Improved layout structure using BorderLayout and GridBagLayout for better responsiveness
- Enhanced tool window welcome page with modern UI design and emoji icons
- Updated configuration panel titles and descriptions for better clarity

### Removed
- Deprecated single API key configuration in favor of multi-AI model support
- Removed legacy AiConfigurationComponent.form file
- Cleaned up obsolete API key methods and references
- Eliminated redundant container panels in configuration UI

### Improved
- Better code organization and maintainability in configuration components
- Enhanced user guidance with step-by-step usage instructions
- Improved visual hierarchy and information presentation
- Better integration between tool window and configuration settings
- More intuitive navigation with direct links to configuration pages

### Technical
- Refactored UmlFlowToolWindow.java for better code structure
- Optimized import statements and removed duplicates
- Enhanced error handling and user feedback mechanisms
- Improved component lifecycle management
- Better separation of concerns in UI component creation

---

## Previous Versions

### [1.0.0] - Initial Release
- Basic UML flow diagram generation from Java methods
- Support for multiple AI providers (DeepSeek, OpenAI, Anthropic)
- PlantUML integration for diagram visualization
- IntelliJ IDEA plugin integration
- Basic configuration interface