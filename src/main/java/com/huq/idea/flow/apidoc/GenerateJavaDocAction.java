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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Action to analyze and generate JavaDoc for Java code using AI
 */
public class GenerateJavaDocAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(GenerateJavaDocAction.class);

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        Editor editor = e.getData(LangDataKeys.EDITOR);
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);

        if (editor == null || !(psiFile instanceof PsiJavaFile)) {
            return;
        }

        PsiMethod method = ReadAction.compute(() -> {
            LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
            int offset = editor.logicalPositionToOffset(logicalPosition);
            return MethodUtils.getContainingMethodAtOffset(psiFile, offset);
        });

        if (method == null) {
            return;
        }

        String className = ReadAction.compute(() -> method.getContainingClass() != null ? method.getContainingClass().getName() : "Unknown");
        String methodName = ReadAction.compute(method::getName);
        String methodText = ReadAction.compute(method::getText);
        String returnType = ReadAction.compute(() -> method.getReturnType() != null ? method.getReturnType().getPresentableText() : "void");

        CallStack callStack = new CallStack();
        MethodDescription description = new MethodDescription(method, className, methodText, methodName, method.getDocComment(), returnType);
        callStack.setMethodDescription(description);

        String collectedCode = collectCodeFromCallStack(callStack);

        String title = className + "." + methodName;

        SwingUtilities.invokeLater(() ->
            showInitialDialog(project, callStack, collectedCode, title));
    }

    private void showInitialDialog(Project project, CallStack callStack, String collectedCode, String title) {
        CodeAnalysisUIFactory.PromptProvider promptProvider = new CodeAnalysisUIFactory.PromptProvider() {
            private ComboBox<IdeaSettings.PromptConfig> promptComboBox;

            @Override
            public String getPrompt(String collectedCode) {
                IdeaSettings.PromptConfig selectedPromptConfig = promptComboBox != null ? (IdeaSettings.PromptConfig) promptComboBox.getSelectedItem() : null;
                String javaDocPromptTemplate = selectedPromptConfig != null ? selectedPromptConfig.getPrompt() : IdeaSettings.getInstance().getState().getGenerateJavaDocPrompt();
                return String.format(javaDocPromptTemplate, collectedCode);
            }

            @Override
            public JComboBox<IdeaSettings.PromptConfig> getPromptComboBox() {
                java.util.List<IdeaSettings.PromptConfig> prompts = IdeaSettings.getInstance().getState().getJavaDocPrompts();
                promptComboBox = new ComboBox<>(prompts.toArray(new IdeaSettings.PromptConfig[0]));

                if (!prompts.isEmpty()) {
                    promptComboBox.setSelectedIndex(0);
                }
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
                return promptComboBox;
            }
        };

        String systemMessage = "你是一个高级Java开发专家。请提供详细、专业的JavaDoc注释。如果包含Markdown代码块符号(如```java)，请去掉，只输出纯代码。";
        CodeAnalysisUIFactory.showInitialDialog(project, collectedCode, "生成JavaDoc: " + title, promptProvider, "生成JavaDoc", systemMessage);
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

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        Project project = e.getProject();
        Editor editor = e.getData(LangDataKeys.EDITOR);
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);

        boolean enabled = project != null && editor != null && psiFile instanceof PsiJavaFile;
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}
