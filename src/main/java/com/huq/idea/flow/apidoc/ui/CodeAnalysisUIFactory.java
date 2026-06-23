package com.huq.idea.flow.apidoc.ui;

import com.huq.idea.flow.util.AiUtils;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

/**
 * UI Factory for Code Analysis features like Explain, Review, Unit Test generation.
 */
public class CodeAnalysisUIFactory {

    private static final Logger LOG = Logger.getInstance(CodeAnalysisUIFactory.class);

    public interface PromptProvider {
        String getPrompt(String collectedCode);
        JComboBox<IdeaSettings.PromptConfig> getPromptComboBox();
    }

    public static void showInitialDialog(Project project, String collectedCode, String title, PromptProvider promptProvider, String generateButtonText, String systemMessage) {
        DialogWrapper dialog = new DialogWrapper(project) {
            private JPanel mainPanel;
            private JTextArea resultArea;
            private JTextArea codeArea;
            private ComboBox<IdeaSettings.CustomAiProviderConfig> providerComboBox;
            private ComboBox<String> specificModelComboBox;
            private JComboBox<IdeaSettings.PromptConfig> promptComboBox;
            private JButton generateButton;
            private JButton copyButton;

            {
                init();
                setTitle(title);
            }

            @Override
            protected @Nullable JComponent createCenterPanel() {
                mainPanel = new JPanel(new BorderLayout());
                mainPanel.setPreferredSize(new Dimension(1000, 700));

                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
                splitPane.setDividerLocation(500);
                splitPane.setResizeWeight(0.5);

                // Left side: Source Code
                JPanel codePanel = new JPanel(new BorderLayout());
                codeArea = new JTextArea();
                codeArea.setEditable(true);
                codeArea.setText(collectedCode);
                codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
                codePanel.add(new JScrollPane(codeArea), BorderLayout.CENTER);

                // Right side: AI Result
                JPanel resultPanel = new JPanel(new BorderLayout());
                resultArea = new JTextArea();
                resultArea.setEditable(false);
                resultArea.setLineWrap(true);
                resultArea.setWrapStyleWord(true);
                resultArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                resultArea.setText("点击\"" + generateButtonText + "\"按钮开始分析...");
                resultArea.setMargin(new Insets(10, 10, 10, 10));
                resultPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);

                splitPane.setLeftComponent(codePanel);
                splitPane.setRightComponent(resultPanel);

                // Top panel: Configs
                JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

                // Configs
                promptComboBox = promptProvider.getPromptComboBox();
                if (promptComboBox != null) {
                    topPanel.add(new JLabel("提示词模板:"));
                    topPanel.add(promptComboBox);
                }

                mainPanel.add(topPanel, BorderLayout.NORTH);

                // Bottom panel: Action buttons
                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

                JLabel providerLabel = new JLabel("AI 提供商:");
                java.util.List<IdeaSettings.CustomAiProviderConfig> availableProviders = AiUtils.getCustomProviders();
                providerComboBox = new ComboBox<>(availableProviders.toArray(new IdeaSettings.CustomAiProviderConfig[0]));

                JLabel specificModelLabel = new JLabel("具体模型:");
                specificModelComboBox = new ComboBox<>();

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

                generateButton = new JButton(generateButtonText);
                generateButton.addActionListener(e -> generateAnalysis());
                buttonPanel.add(generateButton);

                copyButton = new JButton("复制结果");
                copyButton.addActionListener(e -> {
                    copyToClipboard(resultArea.getText());
                    Notifications.Bus.notify(new Notification(
                            "com.yt.huq.idea",
                            title,
                            "结果已复制到剪贴板",
                            NotificationType.INFORMATION),
                            project);
                });
                buttonPanel.add(copyButton);

                mainPanel.add(splitPane, BorderLayout.CENTER);
                mainPanel.add(buttonPanel, BorderLayout.SOUTH);

                return mainPanel;
            }

            @Override
            protected Action[] createActions() {
                return new Action[]{getCancelAction()};
            }

            private void generateAnalysis() {
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

                new Task.Backgroundable(project, title, true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        indicator.setText("正在" + generateButtonText + "...");
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
                        String result = response.isSuccess() ? response.getContent() : null;

                        if (result != null && !result.isEmpty()) {
                            // Clean up Markdown code blocks if generating test or javadoc
                            if (generateButtonText.contains("测试") || generateButtonText.contains("JavaDoc")) {
                                if (result.startsWith("```java")) {
                                    result = result.substring(7);
                                } else if (result.startsWith("```")) {
                                    result = result.substring(3);
                                }
                                if (result.endsWith("```")) {
                                    result = result.substring(0, result.length() - 3);
                                }
                                result = result.trim();
                            }

                            String finalResult = result;
                            SwingUtilities.invokeLater(() -> {
                                resultArea.setText(finalResult);
                                resultArea.setCaretPosition(0);

                                generateButton.setEnabled(true);
                                generateButton.setText("重新" + generateButtonText.replace("重新", ""));
                            });
                        } else {
                            SwingUtilities.invokeLater(() -> {
                                String errorMsg = "分析失败，请检查API设置和网络连接";
                                if (response != null && !response.isSuccess() && response.getErrorMessage() != null) {
                                    errorMsg = "分析失败: " + response.getErrorMessage();
                                }

                                Notifications.Bus.notify(new Notification(
                                        "com.yt.huq.idea",
                                        title,
                                        errorMsg,
                                        NotificationType.ERROR),
                                        project);

                                generateButton.setEnabled(true);
                                generateButton.setText(generateButtonText);
                            });
                        }
                    }
                }.queue();
            }
        };

        dialog.show();
    }

    private static void copyToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }
}
