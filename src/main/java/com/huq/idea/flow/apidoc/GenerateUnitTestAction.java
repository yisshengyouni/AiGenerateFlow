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

import javax.swing.*;
import java.awt.*;

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

        JPanel testTab = CodeAnalysisUIFactory.createCodeAnalysisPanel(
                project, title, collectedCode, "单元测试", "点击\"生成测试代码\"按钮开始生成...",
                "生成测试代码", "你是一个高级Java开发专家和测试工程师。请提供高质量、可以直接运行的JUnit 5单元测试代码。如果包含Markdown代码块符号(如```java)，请去掉，只输出纯代码。", 0.2,
                code -> {
                    java.util.List<IdeaSettings.PromptConfig> configs = IdeaSettings.getInstance().getState().getGenerateTestPrompts();
                    String generateTestPromptTemplate = configs.isEmpty() ? IdeaSettings.DEFAULT_GENERATE_TEST_PROMPT : configs.get(0).getPrompt();
                    return String.format(generateTestPromptTemplate, code);
                });

        tabbedPane.addTab("单元测试生成", testTab);

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
