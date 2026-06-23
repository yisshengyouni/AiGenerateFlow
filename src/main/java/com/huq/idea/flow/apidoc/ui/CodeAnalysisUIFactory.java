package com.huq.idea.flow.apidoc.ui;

import com.huq.idea.flow.apidoc.service.UmlFlowService;
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

public class CodeAnalysisUIFactory {

    private static final Logger LOG = Logger.getInstance(CodeAnalysisUIFactory.class);

    public enum AnalysisType {
        EXPLAIN("解释代码", "点击\"解释代码\"按钮开始分析..."),
        REVIEW("审查代码", "点击\"审查代码\"按钮开始分析并获取优化建议..."),
        TEST("生成测试代码", "点击\"生成测试代码\"按钮开始生成..."),
        OPTIMIZE("优化代码", "点击\"优化代码\"按钮开始生成优化建议和重构后的代码...");

        private final String buttonText;
        private final String defaultText;

        AnalysisType(String buttonText, String defaultText) {
            this.buttonText = buttonText;
            this.defaultText = defaultText;
        }

        public String getButtonText() {
            return buttonText;
        }

        public String getDefaultText() {
            return defaultText;
        }
    }

    public static void showAnalysisDialog(Project project, String collectedCode, String title, AnalysisType type) {
        UmlFlowService flowService = project.getService(UmlFlowService.class);
        if (flowService == null) {
            return;
        }

        JTabbedPane tabbedPane = new JTabbedPane();

        JPanel analysisTab = createAnalysisTab(project, title, collectedCode, type);
        tabbedPane.addTab(type.buttonText, analysisTab);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        mainPanel.setName(title + " - " + type.buttonText);
        flowService.addFlow(mainPanel);
    }

    private static JPanel createAnalysisTab(Project project, String title, String collectedCode, AnalysisType type) {
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

        JPanel outputPanel = new JPanel(new BorderLayout());
        JTextArea outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setLineWrap(type != AnalysisType.TEST); // Disable wrap for code test
        outputArea.setWrapStyleWord(type != AnalysisType.TEST);
        outputArea.setFont(new Font(type == AnalysisType.TEST ? Font.MONOSPACED : Font.SANS_SERIF, Font.PLAIN, 14));
        outputArea.setText(type.getDefaultText());
        outputArea.setMargin(new Insets(10, 10, 10, 10));

        outputPanel.add(new JScrollPane(outputArea), BorderLayout.CENTER);

        splitPane.setLeftComponent(codePanel);
        splitPane.setRightComponent(outputPanel);

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

        JButton generateButton = new JButton(type.getButtonText());
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
                generateButton.setText(type.getButtonText());
                return;
            }

            new Task.Backgroundable(project, type.getButtonText(), true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("正在执行" + type.getButtonText() + "...");
                    indicator.setIndeterminate(true);

                    String promptTemplate = getPromptTemplate(type);
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
                        });
                        return;
                    }

                    config.setSystemMessage(getSystemMessage(type))
                          .setTemperature(type == AnalysisType.TEST ? 0.2 : 0.7)
                          .setMaxTokens(8000);

                    AiUtils.AiResponse response = AiUtils.callAi(prompt, config);
                    String result = response.isSuccess() ? response.getContent() : null;

                    if (result != null && !result.isEmpty()) {
                        if (type == AnalysisType.TEST) {
                            // 清理可能包含的 Markdown 代码块标签
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
                            outputArea.setText(finalResult);
                            // Move caret to top
                            outputArea.setCaretPosition(0);

                            generateButton.setEnabled(true);
                            generateButton.setText("重新执行");
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            String errorMsg = "执行失败，请检查API设置和网络连接";
                            if (response != null && !response.isSuccess() && response.getErrorMessage() != null) {
                                errorMsg = "执行失败: " + response.getErrorMessage();
                            }

                            Notifications.Bus.notify(new Notification(
                                    "com.yt.huq.idea",
                                    type.getButtonText(),
                                    errorMsg,
                                    NotificationType.ERROR),
                                    project);

                            generateButton.setEnabled(true);
                            generateButton.setText(type.getButtonText());
                        });
                    }
                }
            }.queue();
        });
        buttonPanel.add(generateButton);

        JButton copyButton = new JButton("复制结果");
        copyButton.addActionListener(e -> {
            copyToClipboard(outputArea.getText());
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    type.getButtonText(),
                    "结果已复制到剪贴板",
                    NotificationType.INFORMATION),
                    project);
        });
        buttonPanel.add(copyButton);

        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }


    private static String getPromptTemplate(AnalysisType type) {
        IdeaSettings.State state = IdeaSettings.getInstance().getState();
        java.util.List<IdeaSettings.PromptConfig> prompts = null;
        switch (type) {
            case EXPLAIN: prompts = state.getExplainPrompts(); break;
            case REVIEW: prompts = state.getReviewPrompts(); break;
            case TEST: prompts = state.getTestPrompts(); break;
            case OPTIMIZE: prompts = state.getOptimizePrompts(); break;
        }
        if (prompts != null && !prompts.isEmpty()) {
            return prompts.get(0).getPrompt();
        }
        return "";
    }

    private static String getSystemMessage(AnalysisType type) {
        switch (type) {
            case EXPLAIN: return "你是一个高级Java开发专家和架构师。请提供专业、准确、易懂的代码解释。";
            case REVIEW: return "你是一个高级Java开发专家和代码审查员。请提供专业、准确、可行的代码优化和重构建议。";
            case TEST: return "你是一个高级Java开发专家和测试工程师。请提供高质量、可以直接运行的JUnit 5单元测试代码。如果包含Markdown代码块符号(如```java)，请去掉，只输出纯代码。";
            case OPTIMIZE: return "你是一个高级Java开发专家。请提供代码重构建议和优化后的完整代码。";
            default: return "你是一个高级Java开发专家。";
        }
    }

    private static void copyToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    public static String collectCodeFromCallStack(CallStack callStack) {
        StringBuilder codeBuilder = new StringBuilder();
        appendMethodCode(codeBuilder, callStack);
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, 1);
        }
        return codeBuilder.toString();
    }

    private static void collectCodeFromChildCallStack(StringBuilder codeBuilder, CallStack callStack, int depth) {
        if (depth > 10) {
            return;
        }
        if (!callStack.isRecursive()) {
            appendMethodCode(codeBuilder, callStack);
        }
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, depth + 1);
        }
    }

    private static void appendMethodCode(StringBuilder codeBuilder, CallStack callStack) {
        MethodDescription methodDesc = callStack.getMethodDescription();
        if (methodDesc == null) {
            return;
        }

        String methodCode = methodDesc.getText();
        if (methodCode == null || methodCode.isEmpty()) {
            return;
        }

        if (codeBuilder.indexOf(methodDesc.buildMethodId()) != -1) {
            return;
        }

        String className = methodDesc.getClassName();
        String methodName = methodDesc.getName();

        codeBuilder.append("\n\n// ").append("=".repeat(80)).append("\n");
        codeBuilder.append("// Class: ").append(className).append("\n");
        codeBuilder.append("// Method: ").append(methodName).append("\n");
        codeBuilder.append("// token: ").append(methodDesc.buildMethodId()).append("\n");
        codeBuilder.append(methodCode);
        codeBuilder.append("\n// ").append("=".repeat(80)).append("\n\n");
    }
}
