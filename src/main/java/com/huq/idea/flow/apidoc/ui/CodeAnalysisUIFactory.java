package com.huq.idea.flow.apidoc.ui;

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
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Unified UI factory for text-based code analysis actions (Explain, Review, Test generation)
 */
public class CodeAnalysisUIFactory {
    private static final Logger LOG = Logger.getInstance(CodeAnalysisUIFactory.class);

    public static JPanel createAnalysisPanel(
            Project project,
            String collectedCode,
            String actionName,
            String initialMessage,
            String aiSystemMessage,
            String buttonText,
            double temperature,
            Supplier<String> promptSupplier) {

        JPanel panel = new JPanel(new BorderLayout());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(500);
        splitPane.setResizeWeight(0.5);

        // Left Panel: Source Code
        JPanel codePanel = new JPanel(new BorderLayout());
        JTextArea codeArea = new JTextArea();
        codeArea.setEditable(true);
        codeArea.setText(collectedCode);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        codePanel.add(new JScrollPane(codeArea), BorderLayout.CENTER);

        // Right Panel: Analysis Result
        JPanel resultPanel = new JPanel(new BorderLayout());
        JTextArea resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        resultArea.setText(initialMessage);
        resultArea.setMargin(new Insets(10, 10, 10, 10));

        resultPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);

        splitPane.setLeftComponent(codePanel);
        splitPane.setRightComponent(resultPanel);

        // Bottom Panel: Controls
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JLabel providerLabel = new JLabel("AI 提供商:");
        List<IdeaSettings.CustomAiProviderConfig> availableProviders = AiUtils.getCustomProviders();
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

        JButton generateButton = new JButton(buttonText);
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
                generateButton.setText(buttonText);
                return;
            }

            new Task.Backgroundable(project, actionName, true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("正在执行" + actionName + "...");
                    indicator.setIndeterminate(true);

                    String promptTemplate = promptSupplier.get();
                    String prompt = String.format(promptTemplate, codeArea.getText());

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
                            generateButton.setText(buttonText);
                        });
                        return;
                    }

                    config.setSystemMessage(aiSystemMessage)
                          .setTemperature(temperature)
                          .setMaxTokens(8000);

                    AiUtils.AiResponse response = AiUtils.callAi(prompt, config);
                    String analysisResult = response.isSuccess() ? response.getContent() : null;

                    if (analysisResult != null && !analysisResult.isEmpty()) {
                        // Cleanup markdown code blocks if present (especially for test generation)
                        if (actionName.equals("生成单元测试")) {
                            if (analysisResult.startsWith("```java")) {
                                analysisResult = analysisResult.substring(7);
                            } else if (analysisResult.startsWith("```")) {
                                analysisResult = analysisResult.substring(3);
                            }
                            if (analysisResult.endsWith("```")) {
                                analysisResult = analysisResult.substring(0, analysisResult.length() - 3);
                            }
                            analysisResult = analysisResult.trim();
                        }

                        String finalResult = analysisResult;
                        SwingUtilities.invokeLater(() -> {
                            resultArea.setText(finalResult);
                            resultArea.setCaretPosition(0);
                            generateButton.setEnabled(true);
                            generateButton.setText("重新" + actionName.replace("代码", ""));
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
                            generateButton.setText(buttonText);
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
}
