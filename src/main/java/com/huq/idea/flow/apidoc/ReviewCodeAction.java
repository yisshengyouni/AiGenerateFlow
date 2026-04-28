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

import java.util.HashSet;
import java.util.List;

public class ReviewCodeAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(ReviewCodeAction.class);

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile)) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea", "审查代码", "此操作仅适用于Java文件", NotificationType.ERROR), project);
            return;
        }

        Editor editor = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);
        if (editor == null) return;

        PsiMethod currentMethod = ReadAction.compute(() -> {
            LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
            int offset = editor.logicalPositionToOffset(logicalPosition);
            return MethodUtils.getContainingMethodAtOffset(psiFile, offset);
        });

        if (currentMethod == null) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea", "审查代码", "光标位置未找到方法", NotificationType.ERROR), project);
            return;
        }

        CallStack callStack = ReadAction.compute(() -> {
            EnhancedMethodChainVisitor methodChainVisitor = new EnhancedMethodChainVisitor();
            return methodChainVisitor.generateMethodChains(currentMethod, null);
        });

        String collectedCode = collectCodeFromCallStack(callStack);
        String title = currentMethod.getContainingClass() != null ? currentMethod.getContainingClass().getName() + "." + currentMethod.getName() : currentMethod.getName();

        CodeAnalysisUIFactory.CodeAnalysisAction actionConfig = new CodeAnalysisUIFactory.CodeAnalysisAction() {
            @Override
            public String getPromptTemplate() {
                List<IdeaSettings.PromptConfig> prompts = IdeaSettings.getInstance().getState().getReviewPrompts();
                return prompts.isEmpty() ? IdeaSettings.DEFAULT_REVIEW_CODE_PROMPT : prompts.get(0).getPrompt();
            }

            @Override
            public String getSystemMessage() {
                return "你是一个高级Java开发专家和代码审查员。请提供专业、准确、可行的代码优化和重构建议。";
            }

            @Override
            public double getTemperature() { return 0.7; }

            @Override
            public String getButtonText() { return "审查代码"; }

            @Override
            public String getWaitText() { return "审查中..."; }

            @Override
            public String getActionName() { return "代码审查"; }

            @Override
            public String getEmptyResultText() { return "点击\"审查代码\"按钮开始分析并获取优化建议..."; }

            @Override
            public boolean isCleanupMarkdown() { return false; }
        };

        CodeAnalysisUIFactory.showAnalysisDialog(project, collectedCode, "审查代码: " + title, actionConfig);
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
        if (depth > 10) return;
        if (!callStack.isRecursive()) {
            appendMethodCode(codeBuilder, callStack, seenMethods);
        }
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, depth + 1, seenMethods);
        }
    }

    private void appendMethodCode(StringBuilder codeBuilder, CallStack callStack, HashSet<String> seenMethods) {
        MethodDescription methodDesc = callStack.getMethodDescription();
        if (methodDesc == null || methodDesc.getText() == null || methodDesc.getText().isEmpty()) return;

        String methodId = methodDesc.buildMethodId();
        if (seenMethods.contains(methodId)) return;
        seenMethods.add(methodId);

        codeBuilder.append("\n\n// ").append("=".repeat(80)).append("\n");
        codeBuilder.append("// Class: ").append(methodDesc.getClassName()).append("\n");
        codeBuilder.append("// Method: ").append(methodDesc.getName()).append("\n");
        codeBuilder.append("// token: ").append(methodId).append("\n");
        codeBuilder.append(methodDesc.getText());
        codeBuilder.append("\n// ").append("=".repeat(80)).append("\n\n");
    }
}
