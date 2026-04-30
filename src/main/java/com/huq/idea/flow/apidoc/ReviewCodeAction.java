package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.apidoc.service.UmlFlowService;
import com.huq.idea.flow.model.CallStack;
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
import com.huq.idea.flow.apidoc.ui.CodeAnalysisUIFactory;

import javax.swing.*;
import java.util.List;

/**
 * Action to review Java code using AI
 */
public class ReviewCodeAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(ReviewCodeAction.class);

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
                    "代码审查",
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
                    "代码审查",
                    "光标位置未找到方法",
                    NotificationType.ERROR),
                    project);
            return;
        }

        CallStack callStack = ReadAction.compute(() -> {
            EnhancedMethodChainVisitor methodChainVisitor = new EnhancedMethodChainVisitor();
            return methodChainVisitor.generateMethodChains(method, null);
        });

        String collectedCode = CodeAnalysisUIFactory.collectCodeFromCallStack(callStack);

        String className = method.getContainingClass() != null ? method.getContainingClass().getName() : "Unknown";
        String title = className + "." + method.getName();

        SwingUtilities.invokeLater(() ->
            showInitialDialog(project, callStack, collectedCode, title));
    }

    private void showInitialDialog(Project project, CallStack callStack, String collectedCode, String title) {
        CodeAnalysisUIFactory.PromptProvider promptProvider = code -> {
            List<com.huq.idea.flow.config.config.IdeaSettings.PromptConfig> prompts = com.huq.idea.flow.config.config.IdeaSettings.getInstance().getState().getReviewPrompts();
            String promptTemplate = prompts != null && !prompts.isEmpty() ? prompts.get(0).getPrompt() : null;
            if (promptTemplate == null || promptTemplate.isEmpty()) {
                promptTemplate = com.huq.idea.flow.config.config.IdeaSettings.DEFAULT_REVIEW_CODE_PROMPT;
            }
            return String.format(promptTemplate, code);
        };

        JPanel reviewTab = CodeAnalysisUIFactory.createAnalysisTab(
            project,
            collectedCode,
            "点击\"审查代码\"按钮开始分析并获取优化建议...",
            "审查代码",
            "正在审查代码...",
            "你是一个高级Java开发专家和代码审查员。请提供专业、准确、可行的代码优化和重构建议。",
            promptProvider,
            false
        );

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("代码审查", reviewTab);

        JPanel mainPanel = new JPanel(new java.awt.BorderLayout());
        mainPanel.add(tabbedPane, java.awt.BorderLayout.CENTER);

        UmlFlowService plugin = project.getService(UmlFlowService.class);
        mainPanel.setName(title + " (审查)");
        plugin.addFlow(mainPanel);
    }
}
