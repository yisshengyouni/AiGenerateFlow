package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
import com.huq.idea.flow.apidoc.service.UmlFlowService;
import com.huq.idea.flow.apidoc.ui.CodeAnalysisUIFactory;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ExplainCodeAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(ExplainCodeAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || editor == null || psiFile == null) {
            return;
        }

        int offset = editor.getCaretModel().getOffset();
        PsiElement elementAt = psiFile.findElementAt(offset);
        PsiMethod psiMethod = PsiTreeUtil.getParentOfType(elementAt, PsiMethod.class);

        if (psiMethod == null) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "分析代码",
                    "光标位置未找到方法",
                    NotificationType.ERROR),
                    project);
            return;
        }

        UmlFlowService plugin = project.getService(UmlFlowService.class);

        EnhancedMethodChainVisitor visitor = new EnhancedMethodChainVisitor();
        CallStack rootStack = visitor.generateMethodChains(psiMethod, null);
        String collectedCode = collectCodeFromCallStack(rootStack);

        JPanel panel = CodeAnalysisUIFactory.createPanel(
                project,
                "代码解释 - " + psiMethod.getName(),
                collectedCode,
                "解释代码",
                "点击\"解释代码\"按钮开始分析...",
                "你是一个高级Java开发专家和架构师。请提供专业、准确、易懂的代码解释。",
                code -> {
                    String explainPromptTemplate = IdeaSettings.getInstance().getState().getExplainCodePrompt();
                    return String.format(explainPromptTemplate, code);
                }
        );

        panel.setName(psiMethod.getName());
        plugin.addFlow(panel);
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
