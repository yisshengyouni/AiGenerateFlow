with open('src/main/java/com/huq/idea/flow/apidoc/ui/CodeAnalysisUIFactory.java', 'r') as f:
    content = f.read()

# Add postProcessResponse to the interface
interface_addition = """
    public interface AnalysisConfigProvider {
        String getPrompt(String collectedCode);
        String getSystemMessage();
        String getActionName();
        String getInitialMessage();
        default boolean isEditableResult() { return false; }
        default boolean isLineWrapResult() { return true; }
        default String postProcessResponse(String response) { return response; }
    }
"""

content = content.replace("    public interface AnalysisConfigProvider {\n        String getPrompt(String collectedCode);\n        String getSystemMessage();\n        String getActionName();\n        String getInitialMessage();\n    }", interface_addition)

# Update UI elements creation based on configProvider
ui_updates = """        JPanel resultPanel = new JPanel(new BorderLayout());
        JTextArea resultArea = new JTextArea();
        resultArea.setEditable(configProvider.isEditableResult());
        resultArea.setLineWrap(configProvider.isLineWrapResult());
        if (configProvider.isLineWrapResult()) {
            resultArea.setWrapStyleWord(true);
        } else {
            resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        }
        if (!configProvider.isEditableResult()) {
            resultArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        }"""
content = content.replace("        JPanel resultPanel = new JPanel(new BorderLayout());\n        JTextArea resultArea = new JTextArea();\n        resultArea.setEditable(false);\n        resultArea.setLineWrap(true);\n        resultArea.setWrapStyleWord(true);\n        resultArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));", ui_updates)

# Apply postProcessResponse
process_response = """                    if (result != null && !result.isEmpty()) {
                        String finalResult = configProvider.postProcessResponse(result);
                        SwingUtilities.invokeLater(() -> {
                            resultArea.setText(finalResult);"""
content = content.replace("                    if (result != null && !result.isEmpty()) {\n                        SwingUtilities.invokeLater(() -> {\n                            resultArea.setText(result);", process_response)

with open('src/main/java/com/huq/idea/flow/apidoc/ui/CodeAnalysisUIFactory.java', 'w') as f:
    f.write(content)
