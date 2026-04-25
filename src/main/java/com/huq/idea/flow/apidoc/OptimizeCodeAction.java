package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.apidoc.service.UmlFlowService;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
import com.huq.idea.flow.apidoc.ui.CodeAnalysisUIFactory;
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
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * Action to optimize Java code using AI
 */
public class OptimizeCodeAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(OptimizeCodeAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        Editor editor = e.getData(LangDataKeys.EDITOR);
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);

        if (editor == null || !(psiFile instanceof PsiJavaFile)) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "无效操作",
                    "请在Java文件编辑器中使用此功能",
                    NotificationType.WARNING),
                    project);
            return;
        }

        LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
        int offset = editor.logicalPositionToOffset(logicalPosition);

        PsiMethod method = ReadAction.compute(() -> MethodUtils.getContainingMethodAtOffset(psiFile, offset));

        if (method == null) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "未找到方法",
                    "请将光标置于Java方法内部",
                    NotificationType.WARNING),
                    project);
            return;
        }

        try {
            CallStack rootStack = ReadAction.compute(() -> {
                EnhancedMethodChainVisitor visitor = new EnhancedMethodChainVisitor();
                return visitor.generateMethodChains(method, null);
            });

            if (rootStack == null || rootStack.getMethodDescription() == null) {
                Notifications.Bus.notify(new Notification(
                        "com.yt.huq.idea",
                        "生成失败",
                        "无法解析方法调用栈",
                        NotificationType.ERROR),
                        project);
                return;
            }

            String collectedCode = ReadAction.compute(() -> collectCodeFromCallStack(rootStack));

            String className = ReadAction.compute(() -> method.getContainingClass().getName());
            String methodName = ReadAction.compute(() -> method.getName());
            String title = "代码优化: " + className + "." + methodName;

            showUI(project, title, collectedCode);

        } catch (Exception ex) {
            LOG.error("Failed to collect code for optimization", ex);
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "生成失败",
                    "收集代码失败: " + ex.getMessage(),
                    NotificationType.ERROR),
                    project);
        }
    }

    private void showUI(Project project, String title, String collectedCode) {
        CodeAnalysisUIFactory.showInitialDialog(
            project,
            collectedCode,
            title,
            code -> String.format(IdeaSettings.getInstance().getState().getOptimizeCodePrompt(), code),
            "优化代码",
            "你是一个高级Java开发专家。请提供专业、准确、可行的代码优化和重构建议，并直接给出优化后的代码实现。如果包含Markdown代码块符号(如```java)，请去掉，只输出纯代码。",
            "点击\"优化代码\"按钮开始分析并获取优化后的代码..."
        );
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

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(LangDataKeys.EDITOR);
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);

        boolean enabled = project != null && editor != null && psiFile instanceof PsiJavaFile;
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}
