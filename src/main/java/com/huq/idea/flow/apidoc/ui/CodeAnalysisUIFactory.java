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

    public static void showInitialDialog(Project project, String collectedCode, String title, PromptProvider promptProvider, String generateButtonText, String resultAreaInitialText, String systemMessage, String taskName) {
        JTabbedPane tabbedPane = new JTabbedPane();

        JPanel analysisTab = createAnalysisTab(project, title, collectedCode, promptProvider, generateButtonText, resultAreaInitialText, systemMessage, taskName);
        tabbedPane.addTab(taskName, analysisTab);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel enhancedLabel = new JLabel();
        enhancedLabel.setForeground(Color.BLUE);
        bottomPanel.add(enhancedLabel);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        UmlFlowService plugin = project.getService(UmlFlowService.class);
        mainPanel.setName(title + " (" + taskName + ")");
        plugin.addFlow(mainPanel);
    }

    private static JPanel createAnalysisTab(Project project, String title, String collectedCode, PromptProvider promptProvider, String generateButtonText, String resultAreaInitialText, String systemMessage, String taskName) {
        JPanel panel = new JPanel(new BorderLayout());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(500);
        splitPane.setResizeWeight(0.5);

        // 1. Source Code Tab (Read-only view of collectedCode)
        JTabbedPane leftCodeTabbedPane = new JTabbedPane();
        JPanel sourceCodePanel = new JPanel(new BorderLayout());
        JTextArea sourceTextArea = new JTextArea();
        sourceTextArea.setEditable(false);
        sourceTextArea.setText(collectedCode);
        sourceTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        sourceCodePanel.add(new JScrollPane(sourceTextArea), BorderLayout.CENTER);
        leftCodeTabbedPane.addTab("源码视图", sourceCodePanel);

        // 2. Result Output Tab
        JPanel resultPanel = new JPanel(new BorderLayout());
        JTextArea resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        resultArea.setText(resultAreaInitialText);
        resultArea.setMargin(new Insets(10, 10, 10, 10));
        resultPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);


        splitPane.setLeftComponent(leftCodeTabbedPane);
        splitPane.setRightComponent(resultPanel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JLabel providerLabel = new JLabel("AI提供商:");
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
                generateButton.setText(generateButtonText);
                return;
            }

            new Task.Backgroundable(project, taskName, true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("正在执行" + taskName + "...");
                    indicator.setIndeterminate(true);

                    String prompt = promptProvider.getPrompt(collectedCode);

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

                        // 清理可能包含的 Markdown 代码块标签, 主要针对代码生成场景
                        if (resultText.startsWith("```java")) {
                            resultText = resultText.substring(7);
                        } else if (resultText.startsWith("```")) {
                            resultText = resultText.substring(3);
                        }
                        if (resultText.endsWith("```")) {
                            resultText = resultText.substring(0, resultText.length() - 3);
                        }
                        String finalResultText = resultText.trim();

                        SwingUtilities.invokeLater(() -> {
                            resultArea.setText(finalResultText);
                            resultArea.setCaretPosition(0);

                            generateButton.setEnabled(true);
                            generateButton.setText("重新执行");
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            String errorMsg = taskName + "失败，请检查API设置和网络连接";
                            if (response != null && !response.isSuccess() && response.getErrorMessage() != null) {
                                errorMsg = taskName + "失败: " + response.getErrorMessage();
                            }

                            Notifications.Bus.notify(new Notification(
                                    "com.yt.huq.idea",
                                    taskName,
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
            copyToClipboard(resultArea.getText());
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    taskName,
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
