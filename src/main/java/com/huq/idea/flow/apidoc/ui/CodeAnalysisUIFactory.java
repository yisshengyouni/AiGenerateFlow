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
import java.util.List;

/**
 * Unified UI factory for text-based Code Analysis actions
 */
public class CodeAnalysisUIFactory {
    private static final Logger LOG = Logger.getInstance(CodeAnalysisUIFactory.class);

    public interface AnalysisConfig {
        String getTabName();
        String getActionName();
        String getInitialMessage();
        String getSystemMessage();
        String getPrompt(String collectedCode);
        boolean isCodeOutput();
        default JComboBox<IdeaSettings.PromptConfig> getPromptComboBox() {
            return null;
        }
    }

    public static void showInitialDialog(Project project, String collectedCode, String title, AnalysisConfig config) {
        JTabbedPane tabbedPane = new JTabbedPane();

        JPanel analysisTab = createAnalysisTab(project, title, collectedCode, config);
        tabbedPane.addTab(config.getTabName(), analysisTab);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel enhancedLabel = new JLabel();
        enhancedLabel.setForeground(Color.BLUE);
        bottomPanel.add(enhancedLabel);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        UmlFlowService plugin = project.getService(UmlFlowService.class);
        mainPanel.setName(title + " (" + config.getActionName() + ")");
        plugin.addFlow(mainPanel);
    }

    private static JPanel createAnalysisTab(Project project, String title, String collectedCode, AnalysisConfig config) {
        JPanel panel = new JPanel(new BorderLayout());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(500);
        splitPane.setResizeWeight(0.5);

        // ==== LEFT PANE: Source Code ====
        JPanel codePanel = new JPanel(new BorderLayout());
        JTextArea codeArea = new JTextArea();
        codeArea.setEditable(true);
        codeArea.setText(collectedCode);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        codePanel.add(new JScrollPane(codeArea), BorderLayout.CENTER);

        // ==== RIGHT PANE: Output Area ====
        JPanel outputPanel = new JPanel(new BorderLayout());
        JTextArea outputArea = new JTextArea();
        if (config.isCodeOutput()) {
            outputArea.setEditable(true);
            outputArea.setLineWrap(false);
            outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        } else {
            outputArea.setEditable(false);
            outputArea.setLineWrap(true);
            outputArea.setWrapStyleWord(true);
            outputArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        }
        outputArea.setText(config.getInitialMessage());
        outputArea.setMargin(new Insets(10, 10, 10, 10));

        outputPanel.add(new JScrollPane(outputArea), BorderLayout.CENTER);

        splitPane.setLeftComponent(codePanel);
        splitPane.setRightComponent(outputPanel);

        // ==== BOTTOM PANE: Buttons and Config ====
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JLabel providerLabel = new JLabel("AI 提供商 (&P):");
        providerLabel.setDisplayedMnemonic('P');
        List<IdeaSettings.CustomAiProviderConfig> availableProviders = AiUtils.getCustomProviders();
        JComboBox<IdeaSettings.CustomAiProviderConfig> providerComboBox = new JComboBox<>(availableProviders.toArray(new IdeaSettings.CustomAiProviderConfig[0]));
        providerLabel.setLabelFor(providerComboBox);
        providerComboBox.setToolTipText("选择AI模型提供商");

        JLabel specificModelLabel = new JLabel("具体模型 (&M):");
        specificModelLabel.setDisplayedMnemonic('M');
        JComboBox<String> specificModelComboBox = new JComboBox<>();
        specificModelLabel.setLabelFor(specificModelComboBox);
        specificModelComboBox.setToolTipText("选择具体的AI模型");

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

        JComboBox<IdeaSettings.PromptConfig> promptComboBox = config.getPromptComboBox();
        if (promptComboBox != null) {
            JLabel promptLabel = new JLabel("提示词 (&T):");
            promptLabel.setDisplayedMnemonic('T');
            promptLabel.setLabelFor(promptComboBox);
            promptComboBox.setToolTipText("选择提示词模板");
            buttonPanel.add(promptLabel);
            buttonPanel.add(promptComboBox);
        }

        JButton generateButton = new JButton(config.getActionName() + " (&G)");
        generateButton.setMnemonic('G');
        generateButton.setToolTipText("开始使用AI" + config.getActionName());
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
                generateButton.setText(config.getActionName() + " (G)");
                return;
            }

            new Task.Backgroundable(project, config.getActionName(), true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("正在执行 " + config.getActionName() + "...");
                    indicator.setIndeterminate(true);

                    String prompt = config.getPrompt(codeArea.getText());

                    AiUtils.AiConfig aiConfig = new AiUtils.AiConfig(selectedProvider, selectedModel);
                    if (aiConfig.getApiKey() == null || aiConfig.getApiKey().trim().isEmpty()) {
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

                    aiConfig.setSystemMessage(config.getSystemMessage())
                          .setTemperature(config.isCodeOutput() ? 0.2 : 0.7)
                          .setMaxTokens(8000);

                    AiUtils.AiResponse response = AiUtils.callAi(prompt, aiConfig);
                    String resultText = response.isSuccess() ? response.getContent() : null;

                    if (resultText != null && !resultText.isEmpty()) {
                        if (config.isCodeOutput()) {
                            // Clean up Markdown blocks if output is code
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
                            outputArea.setText(finalResultText);
                            outputArea.setCaretPosition(0);

                            generateButton.setEnabled(true);
                            generateButton.setText("重新" + config.getActionName().replace(" (&G)", " (G)"));
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            String errorMsg = config.getActionName() + "失败，请检查API设置和网络连接";
                            if (response != null && !response.isSuccess() && response.getErrorMessage() != null) {
                                errorMsg = config.getActionName() + "失败: " + response.getErrorMessage();
                            }

                            Notifications.Bus.notify(new Notification(
                                    "com.yt.huq.idea",
                                    config.getActionName(),
                                    errorMsg,
                                    NotificationType.ERROR),
                                    project);

                            generateButton.setEnabled(true);
                            generateButton.setText(config.getActionName() + " (G)");
                        });
                    }
                }
            }.queue();
        });
        buttonPanel.add(generateButton);

        JButton copyButton = new JButton("复制结果 (&C)");
        copyButton.setMnemonic('C');
        copyButton.setToolTipText("复制生成的结果到剪贴板");
        copyButton.addActionListener(e -> {
            copyToClipboard(outputArea.getText());
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    config.getActionName(),
                    "内容已复制到剪贴板",
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
