package com.huq.idea.flow.apidoc.ui;

import com.huq.idea.flow.config.config.IdeaSettings;
import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
import com.huq.idea.flow.util.AiUtils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.HashSet;
import java.util.Set;

/**
 * Factory for creating UI components for text-based code analysis features
 * (Explain, Review, Test Generate, Optimize).
 */
public class CodeAnalysisUIFactory {
    private static final Logger LOG = Logger.getInstance(CodeAnalysisUIFactory.class);

    public interface PromptProvider {
        String getPrompt(String code);
    }

    public static JPanel createAnalysisTab(Project project,
                                           String collectedCode,
                                           String initialMessage,
                                           String actionName,
                                           String progressMessage,
                                           String systemMessage,
                                           PromptProvider promptProvider,
                                           boolean stripMarkdown) {
        JPanel panel = new JPanel(new BorderLayout());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(500);
        splitPane.setResizeWeight(0.5);

        JPanel codePanel = new JPanel(new BorderLayout());
        JTextArea codeArea = new JTextArea();
        codeArea.setEditable(true);
        codeArea.setText(collectedCode);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        codePanel.add(new JScrollPane(codeArea), BorderLayout.CENTER);

        JPanel resultPanel = new JPanel(new BorderLayout());
        JTextArea resultArea = new JTextArea();
        resultArea.setEditable(true); // Allow editing of generated content
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        resultArea.setText(initialMessage);
        resultArea.setMargin(new Insets(10, 10, 10, 10));

        resultPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);

        splitPane.setLeftComponent(codePanel);
        splitPane.setRightComponent(resultPanel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JLabel providerLabel = new JLabel("AI 提供商:");
        java.util.List<IdeaSettings.CustomAiProviderConfig> availableProviders = AiUtils.getCustomProviders();
        ComboBox<IdeaSettings.CustomAiProviderConfig> providerComboBox = new ComboBox<>(availableProviders.toArray(new IdeaSettings.CustomAiProviderConfig[0]));

        JLabel specificModelLabel = new JLabel("具体模型:");
        ComboBox<String> specificModelComboBox = new ComboBox<>();

        providerComboBox.addActionListener(e -> {
            IdeaSettings.CustomAiProviderConfig selected = (IdeaSettings.CustomAiProviderConfig) providerComboBox.getSelectedItem();
            specificModelComboBox.removeAllItems();
            if (selected != null && selected.getModels() != null) {
                String[] models = selected.getModels().split(",");
                for (String model : models) {
                    specificModelComboBox.addItem(model.trim());
                }
                if (models.length > 0) {
                    specificModelComboBox.setSelectedIndex(0);
                }
            }
        });

        if (!availableProviders.isEmpty()) {
            providerComboBox.setSelectedIndex(-1);
            providerComboBox.setSelectedIndex(0);
        }

        providerComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof IdeaSettings.CustomAiProviderConfig) {
                    IdeaSettings.CustomAiProviderConfig provider = (IdeaSettings.CustomAiProviderConfig) value;
                    setText(provider.getName());
                }
                return this;
            }
        });

        buttonPanel.add(providerLabel);
        buttonPanel.add(providerComboBox);
        buttonPanel.add(specificModelLabel);
        buttonPanel.add(specificModelComboBox);

        JButton generateButton = new JButton(actionName);
        generateButton.addActionListener(e -> {
            generateButton.setEnabled(false);
            generateButton.setText("执行中...");

            IdeaSettings.CustomAiProviderConfig selectedProvider = (IdeaSettings.CustomAiProviderConfig) providerComboBox.getSelectedItem();
            String selectedModel = (String) specificModelComboBox.getSelectedItem();

            if (selectedProvider == null) {
                LOG.info("No AI provider selected or available");
                Notifications.Bus.notify(new Notification(
                        "com.yt.huq.idea",
                        "无可用AI模型",
                        "未选择AI提供商或没有可用的AI提供商。请在设置 > UmlFlowAiConfigurable 中配置至少一个提供商",
                        NotificationType.ERROR),
                        project);
                generateButton.setEnabled(true);
                generateButton.setText(actionName);
                return;
            }

            new Task.Backgroundable(project, actionName, true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText(progressMessage);
                    indicator.setIndeterminate(true);

                    String prompt = promptProvider.getPrompt(codeArea.getText());

                    AiUtils.AiConfig config = new AiUtils.AiConfig(selectedProvider, selectedModel);
                    if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
                        SwingUtilities.invokeLater(() -> {
                            generateButton.setEnabled(true);
                            Notifications.Bus.notify(new Notification(
                                "com.yt.huq.idea",
                                "API密钥未配置",
                                "请在设置中为 " + selectedProvider.getName() + " 配置API密钥",
                                NotificationType.WARNING),
                                project);
                        });
                        return;
                    }

                    config.setSystemMessage(systemMessage)
                          .setTemperature(0.7)
                          .setMaxTokens(8000);

                    AiUtils.AiResponse response = AiUtils.callAi(prompt, config);
                    String resultText = response.isSuccess() ? response.getContent() : null;

                    if (resultText != null && !resultText.isEmpty()) {
                        if (stripMarkdown) {
                            if (resultText.startsWith("```java")) {
                                resultText = resultText.substring(7);
                            } else if (resultText.startsWith("```")) {
                                resultText = resultText.substring(3);
                            }
                            if (resultText.endsWith("```")) {
                                resultText = resultText.substring(0, resultText.length() - 3);
                            }
                            resultText = resultText.trim();
                        }
                        String finalResultText = resultText;
                        SwingUtilities.invokeLater(() -> {
                            resultArea.setText(finalResultText);
                            resultArea.setCaretPosition(0);

                            generateButton.setEnabled(true);
                            generateButton.setText("重新" + actionName);
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            String errorMsg = actionName + "失败，请检查API设置和网络连接";
                            if (response != null && !response.isSuccess() && response.getErrorMessage() != null) {
                                errorMsg = actionName + "失败: " + response.getErrorMessage();
                            }

                            Notifications.Bus.notify(new Notification(
                                    "com.yt.huq.idea",
                                    actionName,
                                    errorMsg,
                                    NotificationType.ERROR),
                                    project);

                            generateButton.setEnabled(true);
                            generateButton.setText(actionName);
                        });
                    }
                }
            }.queue();
        });
        buttonPanel.add(generateButton);

        JButton copyButton = new JButton("复制结果");
        copyButton.addActionListener(e -> {
            copyToClipboard(resultArea.getText());
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    actionName,
                    "结果已复制到剪贴板",
                    NotificationType.INFORMATION),
                    project);
        });
        buttonPanel.add(copyButton);

        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private static void copyToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    public static String collectCodeFromCallStack(CallStack callStack) {
        StringBuilder codeBuilder = new StringBuilder();
        Set<String> processedMethods = new HashSet<>();
        appendMethodCode(codeBuilder, callStack, processedMethods);
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, 1, processedMethods);
        }
        return codeBuilder.toString();
    }

    private static void collectCodeFromChildCallStack(StringBuilder codeBuilder, CallStack callStack, int depth, Set<String> processedMethods) {
        if (depth > 10) {
            return;
        }
        if (!callStack.isRecursive()) {
            appendMethodCode(codeBuilder, callStack, processedMethods);
        }
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, depth + 1, processedMethods);
        }
    }

    private static final String SEPARATOR = "\n\n// " + "================================================================================" + "\n";
    private static void appendMethodCode(StringBuilder codeBuilder, CallStack callStack, Set<String> processedMethods) {
        MethodDescription methodDesc = callStack.getMethodDescription();
        if (methodDesc == null) {
            return;
        }

        String methodCode = methodDesc.getText();
        if (methodCode == null || methodCode.isEmpty()) {
            return;
        }

        String methodId = methodDesc.buildMethodId();
        if (processedMethods.contains(methodId)) {
            return;
        }
        processedMethods.add(methodId);

        String className = methodDesc.getClassName();
        String methodName = methodDesc.getName();

        codeBuilder.append(SEPARATOR);
        codeBuilder.append("// Class: ").append(className).append("\n");
        codeBuilder.append("// Method: ").append(methodName).append("\n");
        codeBuilder.append("// token: ").append(methodId).append("\n");
        codeBuilder.append(methodCode);
        codeBuilder.append(SEPARATOR);
    }
}
