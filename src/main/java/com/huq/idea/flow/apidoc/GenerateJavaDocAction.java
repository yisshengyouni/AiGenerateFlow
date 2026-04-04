package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.apidoc.ui.CodeAnalysisUIFactory;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
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
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;

import javax.swing.*;
import java.awt.*;

/**
 * Action to generate JavaDoc for Java code using AI
 */
public class GenerateJavaDocAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(GenerateJavaDocAction.class);

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
                    "生成JavaDoc",
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
                    "生成JavaDoc",
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
            CodeAnalysisUIFactory.showInitialDialog(project, collectedCode, title, new CodeAnalysisUIFactory.AnalysisConfig() {
                private JComboBox<IdeaSettings.PromptConfig> promptComboBox;

                @Override
                public String getTabName() {
                    return "JavaDoc生成";
                }

                @Override
                public String getActionName() {
                    return "生成JavaDoc";
                }

                @Override
                public String getInitialMessage() {
                    return "点击\"生成JavaDoc\"按钮开始生成...";
                }

                @Override
                public String getSystemMessage() {
                    return "你是一个高级Java开发专家。请提供专业、准确的JavaDoc注释，包含方法描述、@param、@return、@throws等信息。只输出带有JavaDoc注释的Java代码，如果包含Markdown代码块符号(如```java)，请去掉，只输出纯代码。";
                }

                @Override
                public String getPrompt(String collectedCode) {
                    IdeaSettings.PromptConfig selectedPrompt = (IdeaSettings.PromptConfig) getPromptComboBox().getSelectedItem();
                    String template = selectedPrompt != null ? selectedPrompt.getPrompt() : IdeaSettings.getInstance().getState().getGenerateJavaDocPrompt();
                    return String.format(template, collectedCode);
                }

                @Override
                public boolean isCodeOutput() {
                    return true;
                }

                @Override
                public JComboBox<IdeaSettings.PromptConfig> getPromptComboBox() {
                    if (promptComboBox == null) {
                        java.util.List<IdeaSettings.PromptConfig> prompts = IdeaSettings.getInstance().getState().getJavaDocPrompts();
                        promptComboBox = new JComboBox<>(prompts.toArray(new IdeaSettings.PromptConfig[0]));
                        promptComboBox.setRenderer(new DefaultListCellRenderer() {
                            @Override
                            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                                if (value instanceof IdeaSettings.PromptConfig) {
                                    setText(((IdeaSettings.PromptConfig) value).getName());
                                }
                                return this;
                            }
                        });
                    }
                    return promptComboBox;
                }
            })
        );
    }

    private String collectCodeFromCallStack(CallStack callStack) {
        StringBuilder codeBuilder = new StringBuilder();
        appendMethodCode(codeBuilder, callStack);
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, 1);
        }
        return codeBuilder.toString();
    }

    private void collectCodeFromChildCallStack(StringBuilder codeBuilder, CallStack callStack, int depth) {
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

    private void appendMethodCode(StringBuilder codeBuilder, CallStack callStack) {
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
