package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.apidoc.service.UmlFlowService;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
import com.huq.idea.flow.apidoc.ui.CodeAnalysisUIFactory;
import java.util.HashSet;
import com.huq.idea.flow.util.AiUtils;
import com.huq.idea.flow.util.MethodUtils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

/**
 * Action to generate JUnit 5 tests for Java code using AI
 */
public class GenerateUnitTestAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(GenerateUnitTestAction.class);

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile)) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "生成单元测试",
                    "此操作仅适用于Java文件",
                    NotificationType.ERROR),
                    project);
            return;
        }

        Editor editor = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);
        if (editor == null) {
            return;
        }

        PsiMethod method = ReadAction.compute(() -> {
            LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
            int offset = editor.logicalPositionToOffset(logicalPosition);
            return MethodUtils.getContainingMethodAtOffset(psiFile, offset);
        });

        if (method == null) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "生成单元测试",
                    "光标位置未找到方法",
                    NotificationType.ERROR),
                    project);
            return;
        }

        CallStack callStack = ReadAction.compute(() -> {
            EnhancedMethodChainVisitor methodChainVisitor = new EnhancedMethodChainVisitor();
            return methodChainVisitor.generateMethodChains(method, null);
        });

        String collectedCode = collectCodeFromCallStack(callStack);

        String className = ReadAction.compute(() -> method.getContainingClass() != null ? method.getContainingClass().getName() : "Unknown");
        String methodName = ReadAction.compute(method::getName);
        String title = className + "." + methodName;

        SwingUtilities.invokeLater(() ->
            showInitialDialog(project, callStack, collectedCode, title));
    }

    private void showInitialDialog(Project project, CallStack callStack, String collectedCode, String title) {
        JTabbedPane tabbedPane = new JTabbedPane();

        JPanel generateTestTab = createGenerateTestTab(project, title, collectedCode);
        tabbedPane.addTab("单元测试生成", generateTestTab);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel enhancedLabel = new JLabel();
        enhancedLabel.setForeground(Color.BLUE);
        bottomPanel.add(enhancedLabel);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        UmlFlowService plugin = project.getService(UmlFlowService.class);
        mainPanel.setName(title + " (单元测试)");
        plugin.addFlow(mainPanel);
    }

    private JPanel createGenerateTestTab(Project project, String title, String collectedCode) {
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

        JPanel testPanel = new JPanel(new BorderLayout());
        JTextArea testArea = new JTextArea();
        testArea.setEditable(true);
        testArea.setLineWrap(false);
        testArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        testArea.setText("点击\"生成测试代码\"按钮开始生成...");

        testArea.setMargin(new Insets(10, 10, 10, 10));

        testPanel.add(new JScrollPane(testArea), BorderLayout.CENTER);

        splitPane.setLeftComponent(codePanel);
        splitPane.setRightComponent(testPanel);

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

        JButton generateButton = new JButton("生成测试代码");
        generateButton.addActionListener(e -> {
            generateButton.setEnabled(false);
            generateButton.setText("生成中...");

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
                generateButton.setText("生成测试代码");
                return;
            }

            new Task.Backgroundable(project, "生成测试代码", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("正在生成单元测试...");
                    indicator.setIndeterminate(true);

                    String generateTestPromptTemplate = IdeaSettings.getInstance().getState().getGenerateTestPrompt();
                    String prompt = String.format(generateTestPromptTemplate, codeArea.getText());

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

                    config.setSystemMessage("你是一个高级Java开发专家和测试工程师。请提供高质量、可以直接运行的JUnit 5单元测试代码。如果包含Markdown代码块符号(如```java)，请去掉，只输出纯代码。")
                          .setTemperature(0.2)
                          .setMaxTokens(8000);

                    AiUtils.AiResponse response = AiUtils.callAi(prompt, config);
                    String generatedTest = response.isSuccess() ? response.getContent() : null;

                    if (generatedTest != null && !generatedTest.isEmpty()) {
                        // 清理可能包含的 Markdown 代码块标签
                        if (generatedTest.startsWith("```java")) {
                            generatedTest = generatedTest.substring(7);
                        } else if (generatedTest.startsWith("```")) {
                            generatedTest = generatedTest.substring(3);
                        }
                        if (generatedTest.endsWith("```")) {
                            generatedTest = generatedTest.substring(0, generatedTest.length() - 3);
                        }
                        String finalGeneratedTest = generatedTest.trim();

                        SwingUtilities.invokeLater(() -> {
                            testArea.setText(finalGeneratedTest);
                            // Move caret to top
                            testArea.setCaretPosition(0);

                            generateButton.setEnabled(true);
                            generateButton.setText("重新生成");
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            String errorMsg = "生成测试代码失败，请检查API设置和网络连接";
                            if (response != null && !response.isSuccess() && response.getErrorMessage() != null) {
                                errorMsg = "生成测试代码失败: " + response.getErrorMessage();
                            }

                            Notifications.Bus.notify(new Notification(
                                    "com.yt.huq.idea",
                                    "生成单元测试",
                                    errorMsg,
                                    NotificationType.ERROR),
                                    project);

                            generateButton.setEnabled(true);
                            generateButton.setText("生成测试代码");
                        });
                    }
                }
            }.queue();
        });
        buttonPanel.add(generateButton);

        JButton copyButton = new JButton("复制测试代码");
        copyButton.addActionListener(e -> {
            copyToClipboard(testArea.getText());
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "生成单元测试",
                    "测试代码已复制到剪贴板",
                    NotificationType.INFORMATION),
                    project);
        });
        buttonPanel.add(copyButton);

        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void copyToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    private String collectCodeFromCallStack(CallStack callStack) {
        StringBuilder codeBuilder = new StringBuilder();
        HashSet<String> seenMethods = new HashSet<>();
        appendMethodCode(codeBuilder, callStack, seenMethods);
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, 1, seenMethods);
        }
        return codeBuilder.toString();
    }

    private void collectCodeFromChildCallStack(StringBuilder codeBuilder, CallStack callStack, int depth, HashSet<String> seenMethods) {
        if (depth > 10) {
            return;
        }
        if (!callStack.isRecursive()) {
            appendMethodCode(codeBuilder, callStack, seenMethods);
        }
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, depth + 1, seenMethods);
        }
    }

    private void appendMethodCode(StringBuilder codeBuilder, CallStack callStack, HashSet<String> seenMethods) {
        MethodDescription methodDesc = callStack.getMethodDescription();
        if (methodDesc == null) {
            return;
        }

        String methodCode = methodDesc.getText();
        if (methodCode == null || methodCode.isEmpty()) {
            return;
        }

        if (!seenMethods.add(methodDesc.buildMethodId())) {
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
