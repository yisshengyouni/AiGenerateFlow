package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.apidoc.service.UmlFlowService;
import com.huq.idea.flow.apidoc.ui.CodeAnalysisUIFactory;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;


import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class OptimizeCodeAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(OptimizeCodeAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || editor == null || psiFile == null) {
            return;
        }

        int offset = ReadAction.compute(() -> editor.getCaretModel().getOffset());
        PsiElement elementAtCaret = ReadAction.compute(() -> psiFile.findElementAt(offset));

        PsiMethod targetMethod = ReadAction.compute(() -> {
            PsiElement parent = elementAtCaret;
            while (parent != null) {
                if (parent instanceof PsiMethod) {
                    return (PsiMethod) parent;
                }
                parent = parent.getParent();
            }
            return null;
        });

        if (targetMethod == null) {
            return;
        }

        CallStack callStack = ReadAction.compute(() -> {
            EnhancedMethodChainVisitor visitor = new EnhancedMethodChainVisitor();
            return visitor.generateMethodChains(targetMethod, null);
        });

        String collectedCode = ReadAction.compute(() -> collectCodeFromCallStack(callStack));

        String title = ReadAction.compute(() -> "Optimize: " + targetMethod.getName());
        IdeaSettings.PromptConfig defaultPromptConfig = new IdeaSettings.PromptConfig("Default", IdeaSettings.DEFAULT_OPTIMIZE_CODE_PROMPT);

        JPanel mainPanel = CodeAnalysisUIFactory.createAnalysisPanel(
                project,
                collectedCode,
                title,
                "优化代码",
                "正在优化代码...",
                "点击\"优化代码\"按钮开始分析并获取优化后的代码...",
                code -> {
                    IdeaSettings.State state = IdeaSettings.getInstance().getState();
                    String promptTemplate = IdeaSettings.DEFAULT_OPTIMIZE_CODE_PROMPT;
                    if (state.getOptimizePrompts() != null && !state.getOptimizePrompts().isEmpty()) {
                        promptTemplate = state.getOptimizePrompts().get(0).getPrompt();
                    }
                    return String.format(promptTemplate, code);
                },
                "你是一个高级Java开发专家。请提供专业、准确的代码优化并输出优化后的代码。",
                "代码优化"
        );
        mainPanel.setName(title);

        UmlFlowService plugin = project.getService(UmlFlowService.class);
        plugin.addFlow(mainPanel);
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
