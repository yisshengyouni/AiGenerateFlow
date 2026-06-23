package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.apidoc.service.UmlFlowService;
import com.huq.idea.flow.apidoc.ui.CodeAnalysisUIFactory;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
import com.huq.idea.flow.util.AiUtils;
import com.huq.idea.flow.util.MethodUtils;
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
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;


import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

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

        String collectedCode = collectCodeFromCallStack(callStack);

        String className = ReadAction.compute(() -> method.getContainingClass() != null ? method.getContainingClass().getName() : "Unknown");
        String methodName = ReadAction.compute(method::getName);
        String title = className + "." + methodName;

        SwingUtilities.invokeLater(() ->
            showInitialDialog(project, callStack, collectedCode, title));
    }

    private void showInitialDialog(Project project, CallStack callStack, String collectedCode, String title) {
        JTabbedPane tabbedPane = new JTabbedPane();

        JPanel reviewTab = createReviewTab(project, title, collectedCode);
        tabbedPane.addTab("代码审查", reviewTab);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel enhancedLabel = new JLabel();
        enhancedLabel.setForeground(Color.BLUE);
        bottomPanel.add(enhancedLabel);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        UmlFlowService plugin = project.getService(UmlFlowService.class);
        mainPanel.setName(title + " (审查)");
        plugin.addFlow(mainPanel);
    }

    private JPanel createReviewTab(Project project, String title, String collectedCode) {
        JPanel panel = CodeAnalysisUIFactory.createAnalysisPanel(
                project,
                collectedCode,
                title,
                "审查代码",
                "正在审查代码...",
                "点击\"审查代码\"按钮开始分析并获取优化建议...",
                code -> {
                    IdeaSettings.State state = IdeaSettings.getInstance().getState();
                    String promptTemplate = IdeaSettings.DEFAULT_REVIEW_CODE_PROMPT;
                    if (state.getReviewPrompts() != null && !state.getReviewPrompts().isEmpty()) {
                        promptTemplate = state.getReviewPrompts().get(0).getPrompt();
                    }
                    return String.format(promptTemplate, code);
                },
                "你是一个高级Java开发专家和代码审查员。请提供专业、准确、可行的代码优化和重构建议。",
                "代码审查"
        );
        return panel;
    }



    private String collectCodeFromCallStack(CallStack callStack) {
        StringBuilder codeBuilder = new StringBuilder();
        Set<String> seenMethodIds = new HashSet<>();
        appendMethodCode(codeBuilder, callStack, seenMethodIds);
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, 1, seenMethodIds);
        }
        return codeBuilder.toString();
    }

    private void collectCodeFromChildCallStack(StringBuilder codeBuilder, CallStack callStack, int depth, Set<String> seenMethodIds) {
        if (depth > 10) {
            return;
        }
        if (!callStack.isRecursive()) {
            appendMethodCode(codeBuilder, callStack, seenMethodIds);
        }
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, depth + 1, seenMethodIds);
        }
    }

    private static final String SEPARATOR = "\n\n// " + "=".repeat(80) + "\n";
    private static final String SEPARATOR_END = "\n// " + "=".repeat(80) + "\n\n";

    private void appendMethodCode(StringBuilder codeBuilder, CallStack callStack, Set<String> seenMethodIds) {
        MethodDescription methodDesc = callStack.getMethodDescription();
        if (methodDesc == null) {
            return;
        }

        String methodCode = methodDesc.getText();
        if (methodCode == null || methodCode.isEmpty()) {
            return;
        }

        String methodId = methodDesc.buildMethodId();
        if (seenMethodIds.contains(methodId)) {
            return;
        }
        seenMethodIds.add(methodId);

        String className = methodDesc.getClassName();
        String methodName = methodDesc.getName();

        codeBuilder.append(SEPARATOR);
        codeBuilder.append("// Class: ").append(className).append("\n");
        codeBuilder.append("// Method: ").append(methodName).append("\n");
        codeBuilder.append("// token: ").append(methodId).append("\n");
        codeBuilder.append(methodCode);
        codeBuilder.append(SEPARATOR_END);
    }
}
