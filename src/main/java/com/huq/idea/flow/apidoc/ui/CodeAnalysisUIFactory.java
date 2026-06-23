package com.huq.idea.flow.apidoc.ui;

import com.huq.idea.flow.apidoc.service.UmlFlowService;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.huq.idea.flow.util.AiUtils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

public class CodeAnalysisUIFactory {
    private static final Logger LOG = Logger.getInstance(CodeAnalysisUIFactory.class);

    public interface PromptProvider {
        String getPrompt(String collectedCode);
        default JComboBox<IdeaSettings.PromptConfig> getPromptComboBox() {
            return null;
        }
    }

    public static void showInitialDialog(Project project, String collectedCode, String title, String tabName, String generateButtonText, String systemMessage, String initialExplanationText, PromptProvider promptProvider) {
        JTabbedPane tabbedPane = new JTabbedPane();

        JPanel analysisTab = createAnalysisTab(project, title, collectedCode, tabName, generateButtonText, systemMessage, initialExplanationText, promptProvider);
        tabbedPane.addTab(tabName, analysisTab);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel enhancedLabel = new JLabel();
        enhancedLabel.setForeground(Color.BLUE);
        bottomPanel.add(enhancedLabel);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        UmlFlowService plugin = project.getService(UmlFlowService.class);
        mainPanel.setName(title + " (" + tabName + ")");
        plugin.addFlow(mainPanel);
    }

    private static JPanel createAnalysisTab(Project project, String title, String collectedCode, String tabName, String generateButtonText, String systemMessage, String initialExplanationText, PromptProvider promptProvider) {
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

        JPanel explanationPanel = new JPanel(new BorderLayout());
        JTextArea explanationArea = new JTextArea();
        explanationArea.setEditable(true); // Allow copy-pasting, modifying
        explanationArea.setLineWrap(true);
        explanationArea.setWrapStyleWord(true);
        explanationArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        explanationArea.setText(initialExplanationText);
        explanationArea.setMargin(new Insets(10, 10, 10, 10));

        explanationPanel.add(new JScrollPane(explanationArea), BorderLayout.CENTER);

        splitPane.setLeftComponent(codePanel);
        splitPane.setRightComponent(explanationPanel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JLabel providerLabel = new JLabel("AI 提供商:");
        java.util.List<IdeaSettings.CustomAiProviderConfig> availableProviders = AiUtils.getCustomProviders();
        JComboBox<IdeaSettings.CustomAiProviderConfig> providerComboBox = new JComboBox<>(availableProviders.toArray(new IdeaSettings.CustomAiProviderConfig[0]));

        JLabel specificModelLabel = new JLabel("具体模型:");
        JComboBox<String> specificModelComboBox = new JComboBox<>();

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

        JComboBox<IdeaSettings.PromptConfig> promptComboBox = promptProvider.getPromptComboBox();
        if (promptComboBox != null) {
            JLabel promptLabel = new JLabel("提示词:");
            buttonPanel.add(promptLabel);
            buttonPanel.add(promptComboBox);
        }

        JButton generateButton = new JButton(generateButtonText);
        generateButton.addActionListener(e -> {
            generateButton.setEnabled(false);
            generateButton.setText("分析中...");

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
                generateButton.setText(generateButtonText);
                return;
            }

            new Task.Backgroundable(project, generateButtonText, true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("正在分析代码...");
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
                    String explanation = response.isSuccess() ? response.getContent() : null;

                    if (explanation != null && !explanation.isEmpty()) {
                        // Cleanup Markdown blocks if it's test code or javadoc
                        if (explanation.startsWith("```java")) {
                            explanation = explanation.substring(7);
                        } else if (explanation.startsWith("```")) {
                            explanation = explanation.substring(3);
                        }
                        if (explanation.endsWith("```")) {
                            explanation = explanation.substring(0, explanation.length() - 3);
                        }
                        String finalExplanation = explanation.trim();

                        SwingUtilities.invokeLater(() -> {
                            explanationArea.setText(finalExplanation);
                            // Move caret to top
                            explanationArea.setCaretPosition(0);

                            generateButton.setEnabled(true);
                            generateButton.setText("重新生成");
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            String errorMsg = "分析失败，请检查API设置和网络连接";
                            if (response != null && !response.isSuccess() && response.getErrorMessage() != null) {
                                errorMsg = "分析失败: " + response.getErrorMessage();
                            }

                            Notifications.Bus.notify(new Notification(
                                    "com.yt.huq.idea",
                                    tabName,
                                    errorMsg,
                                    NotificationType.ERROR),
                                    project);

                            generateButton.setEnabled(true);
                            generateButton.setText(generateButtonText);
                        });
                    }
                }
            }.queue();
        });
        buttonPanel.add(generateButton);

        JButton copyButton = new JButton("复制结果");
        copyButton.addActionListener(e -> {
            copyToClipboard(explanationArea.getText());
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    tabName,
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
}
