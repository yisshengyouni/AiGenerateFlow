package com.huq.idea.flow.apidoc;


import com.huq.idea.flow.apidoc.ui.CodeAnalysisUIFactory;
import com.huq.idea.flow.apidoc.service.UmlFlowService;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
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
 * Action to explain Java code using AI
 */
public class ExplainCodeAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(ExplainCodeAction.class);

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
                    "代码解释",
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
                    "代码解释",
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

        String className = method.getContainingClass() != null ? method.getContainingClass().getName() : "Unknown";
        String title = className + "." + method.getName();

        SwingUtilities.invokeLater(() -> showInitialDialog(project, callStack, collectedCode, title));
    }

    private void showInitialDialog(Project project, CallStack callStack, String collectedCode, String title) {
        CodeAnalysisUIFactory.showInitialDialog(
                project,
                collectedCode,
                title,
                new CodeAnalysisUIFactory.PromptProvider() {
                    private JComboBox<IdeaSettings.PromptConfig> comboBox;

                    @Override
                    public JComboBox<IdeaSettings.PromptConfig> getPromptComboBox() {
                        if (comboBox == null) {
                            java.util.List<IdeaSettings.PromptConfig> configs = IdeaSettings.getInstance().getState().getExplainPrompts();
                            IdeaSettings.PromptConfig[] array = configs.toArray(new IdeaSettings.PromptConfig[0]);
                            comboBox = new JComboBox<>(array);
                            comboBox.setRenderer(new DefaultListCellRenderer() {
                                @Override
                                public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                                    if (value instanceof IdeaSettings.PromptConfig) {
                                        setText(((IdeaSettings.PromptConfig) value).getName());
                                    }
                                    return this;
                                }
                            });
                        }
                        return comboBox;
                    }

                    @Override
                    public String getPrompt(String code) {
                        String promptTemplate = "";
                        if (comboBox != null && comboBox.getSelectedItem() != null) {
                            promptTemplate = ((IdeaSettings.PromptConfig) comboBox.getSelectedItem()).getPrompt();
                        } else {
                            java.util.List<IdeaSettings.PromptConfig> configs = IdeaSettings.getInstance().getState().getExplainPrompts();
                            if (configs != null && !configs.isEmpty()) {
                                promptTemplate = configs.get(0).getPrompt();
                            } else {
                                promptTemplate = IdeaSettings.getInstance().getState().getExplainCodePrompt();
                            }
                        }
                        return String.format(promptTemplate, code);
                    }
                },
                "解释代码",
                "点击\"解释代码\"按钮开始分析...",
                "你是一个高级Java开发专家和架构师。请提供专业、准确、易懂的代码解释。",
                "代码解释"
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
