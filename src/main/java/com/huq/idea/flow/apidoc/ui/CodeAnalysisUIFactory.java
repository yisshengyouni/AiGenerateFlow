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
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.function.Function;

/**
 * Unified UI factory for text-based Code Analysis actions (Explain, Review, Tests, etc.)
 */
public class CodeAnalysisUIFactory {
    private static final Logger LOG = Logger.getInstance(CodeAnalysisUIFactory.class);

    public static void createAndShowAnalysisPanel(Project project, String title, String tabName, String collectedCode,
                                                  String initialText, String actionButtonText, String systemMessage,
                                                  Function<String, String> promptProvider) {
        JTabbedPane tabbedPane = new JTabbedPane();

        JPanel analysisTab = createAnalysisTab(project, title, collectedCode, initialText, actionButtonText, systemMessage, promptProvider);
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

    private static JPanel createAnalysisTab(Project project, String title, String collectedCode,
                                            String initialText, String actionButtonText, String systemMessage,
                                            Function<String, String> promptProvider) {
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
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        resultArea.setText(initialText);
        resultArea.setMargin(new Insets(10, 10, 10, 10));

        resultPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);

        splitPane.setLeftComponent(codePanel);
        splitPane.setRightComponent(resultPanel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JLabel providerLabel = new JLabel("AI 提供商(P):");
        providerLabel.setDisplayedMnemonic('P');
        providerLabel.setToolTipText("选择AI提供商 (Alt+P)");

        List<IdeaSettings.CustomAiProviderConfig> availableProviders = AiUtils.getCustomProviders();
        ComboBox<IdeaSettings.CustomAiProviderConfig> providerComboBox = new ComboBox<>(availableProviders.toArray(new IdeaSettings.CustomAiProviderConfig[0]));
        providerLabel.setLabelFor(providerComboBox);

        JLabel specificModelLabel = new JLabel("具体模型(M):");
        specificModelLabel.setDisplayedMnemonic('M');
        specificModelLabel.setToolTipText("选择具体模型 (Alt+M)");

        ComboBox<String> specificModelComboBox = new ComboBox<>();
        specificModelLabel.setLabelFor(specificModelComboBox);

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

        JButton generateButton = new JButton(actionButtonText + "(G)");
        generateButton.setMnemonic('G');
        generateButton.setToolTipText(actionButtonText + " (Alt+G)");

        generateButton.addActionListener(e -> {
            generateButton.setEnabled(false);
            generateButton.setText("处理中...");

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
                generateButton.setText(actionButtonText);
                return;
            }

            new Task.Backgroundable(project, actionButtonText, true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("正在分析代码...");
                    indicator.setIndeterminate(true);

                    String prompt = promptProvider.apply(codeArea.getText());

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
                        // Strip markdown formatting common in raw code outputs
                        if (resultText.contains("```")) {
                            resultText = resultText.replaceAll("```[a-zA-Z]*\\n", "");
                            resultText = resultText.replaceAll("```", "");
                        }

                        String finalResult = resultText;
                        SwingUtilities.invokeLater(() -> {
                            resultArea.setText(finalResult);
                            resultArea.setCaretPosition(0);

                            generateButton.setEnabled(true);
                            generateButton.setText("重新" + actionButtonText.replace("(G)", ""));
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            String errorMsg = "分析失败，请检查API设置和网络连接";
                            if (response != null && !response.isSuccess() && response.getErrorMessage() != null) {
                                errorMsg = "分析失败: " + response.getErrorMessage();
                            }

                            Notifications.Bus.notify(new Notification(
                                    "com.yt.huq.idea",
                                    actionButtonText.replace("(G)", ""),
                                    errorMsg,
                                    NotificationType.ERROR),
                                    project);

                            generateButton.setEnabled(true);
                            generateButton.setText(actionButtonText);
                        });
                    }
                }
            }.queue();
        });
        buttonPanel.add(generateButton);

        JButton copyButton = new JButton("复制结果(C)");
        copyButton.setMnemonic('C');
        copyButton.setToolTipText("复制结果到剪贴板 (Alt+C)");

        copyButton.addActionListener(e -> {
            copyToClipboard(resultArea.getText());
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    actionButtonText.replace("(G)", ""),
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
