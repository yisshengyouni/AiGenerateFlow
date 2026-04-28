package com.huq.idea.flow.apidoc.ui;

import com.huq.idea.flow.apidoc.service.UmlFlowService;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.huq.idea.flow.util.AiUtils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
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

public class CodeAnalysisUIFactory {
    private static final Logger LOG = Logger.getInstance(CodeAnalysisUIFactory.class);

    public interface CodeAnalysisAction {
        String getPromptTemplate();
        String getSystemMessage();
        double getTemperature();
        String getButtonText();
        String getWaitText();
        String getActionName();
        String getEmptyResultText();
        boolean isCleanupMarkdown();
    }

    public static void showAnalysisDialog(Project project, String collectedCode, String title, CodeAnalysisAction actionConfig) {
        UmlFlowService flowService = project.getService(UmlFlowService.class);
        if (flowService == null) {
            LOG.error("Failed to get UmlFlowService");
            return;
        }

        JPanel panel = createAnalysisPanel(project, collectedCode, title, actionConfig);
        panel.setName(title);

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                flowService.addFlow(panel);
            } catch (Exception ex) {
                LOG.error("Failed to add flow to window", ex);
                Notifications.Bus.notify(new Notification(
                        "com.yt.huq.idea",
                        "Window Error",
                        "Failed to open window: " + ex.getMessage(),
                        NotificationType.ERROR),
                        project);
            }
        });
    }

    private static JPanel createAnalysisPanel(Project project, String collectedCode, String title, CodeAnalysisAction actionConfig) {
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

        JPanel analysisPanel = new JPanel(new BorderLayout());
        JTextArea analysisArea = new JTextArea();
        analysisArea.setEditable(false);
        analysisArea.setLineWrap(true);
        analysisArea.setWrapStyleWord(true);
        analysisArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        analysisArea.setText(actionConfig.getEmptyResultText());
        analysisArea.setMargin(new Insets(10, 10, 10, 10));

        analysisPanel.add(new JScrollPane(analysisArea), BorderLayout.CENTER);

        splitPane.setLeftComponent(codePanel);
        splitPane.setRightComponent(analysisPanel);

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

        JButton generateButton = new JButton(actionConfig.getButtonText());
        generateButton.addActionListener(e -> {
            generateButton.setEnabled(false);
            generateButton.setText(actionConfig.getWaitText());

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
                generateButton.setText(actionConfig.getButtonText());
                return;
            }

            new Task.Backgroundable(project, actionConfig.getActionName(), true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("正在执行 " + actionConfig.getActionName() + "...");
                    indicator.setIndeterminate(true);

                    String prompt = String.format(actionConfig.getPromptTemplate(), codeArea.getText());

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

                    config.setSystemMessage(actionConfig.getSystemMessage())
                          .setTemperature(actionConfig.getTemperature())
                          .setMaxTokens(8000);

                    AiUtils.AiResponse response = AiUtils.callAi(prompt, config);
                    String result = response.isSuccess() ? response.getContent() : null;

                    if (result != null && !result.isEmpty()) {
                        if (actionConfig.isCleanupMarkdown()) {
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
                            analysisArea.setText(finalResult);
                            analysisArea.setCaretPosition(0);

                            generateButton.setEnabled(true);
                            generateButton.setText("重新" + actionConfig.getButtonText().replace("代码", ""));
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            String errorMsg = actionConfig.getActionName() + "失败，请检查API设置和网络连接";
                            if (response != null && !response.isSuccess() && response.getErrorMessage() != null) {
                                errorMsg = actionConfig.getActionName() + "失败: " + response.getErrorMessage();
                            }

                            Notifications.Bus.notify(new Notification(
                                    "com.yt.huq.idea",
                                    actionConfig.getActionName(),
                                    errorMsg,
                                    NotificationType.ERROR),
                                    project);

                            generateButton.setEnabled(true);
                            generateButton.setText(actionConfig.getButtonText());
                        });
                    }
                }
            }.queue();
        });
        buttonPanel.add(generateButton);

        JButton copyButton = new JButton("复制结果");
        copyButton.addActionListener(e -> {
            copyToClipboard(analysisArea.getText());
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    actionConfig.getActionName(),
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
