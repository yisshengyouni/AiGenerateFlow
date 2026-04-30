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
 * Action to optimize Java code using AI
 */
public class OptimizeCodeAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(OptimizeCodeAction.class);

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
                    "优化代码",
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
                    "优化代码",
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
            List<com.huq.idea.flow.config.config.IdeaSettings.PromptConfig> prompts = com.huq.idea.flow.config.config.IdeaSettings.getInstance().getState().getOptimizePrompts();
            String promptTemplate = prompts != null && !prompts.isEmpty() ? prompts.get(0).getPrompt() : null;
            if (promptTemplate == null || promptTemplate.isEmpty()) {
                promptTemplate = com.huq.idea.flow.config.config.IdeaSettings.DEFAULT_OPTIMIZE_CODE_PROMPT;
            }
            return String.format(promptTemplate, code);
        };

        JPanel optimizeTab = CodeAnalysisUIFactory.createAnalysisTab(
            project,
            collectedCode,
            "点击\"优化代码\"按钮开始分析并获取优化建议...",
            "优化代码",
            "正在分析代码结构并生成优化建议...",
            "你是一个高级Java开发专家和架构师。请仔细分析提供的Java代码，指出可以优化的点（如性能、可读性、可维护性等），并提供优化后的代码。",
            promptProvider,
            true
        );

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("代码优化", optimizeTab);

        JPanel mainPanel = new JPanel(new java.awt.BorderLayout());
        mainPanel.add(tabbedPane, java.awt.BorderLayout.CENTER);

        UmlFlowService plugin = project.getService(UmlFlowService.class);
        mainPanel.setName(title + " (优化)");
        plugin.addFlow(mainPanel);
    }
}
