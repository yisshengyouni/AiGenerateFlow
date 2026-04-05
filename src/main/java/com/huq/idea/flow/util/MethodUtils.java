package com.huq.idea.flow.util;

import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author huqiang
 * @since 2024/7/14 12:31
 */
public class MethodUtils {

    public static PsiMethod getContainingMethodAtOffset(PsiFile psiFile, int offset) {
        PsiElement elementAtOffset = psiFile.findElementAt(offset);
        if (elementAtOffset == null) {
            return null;
        }
        return PsiTreeUtil.getParentOfType(elementAtOffset, PsiMethod.class);
    }

    public static String collectCodeFromCallStack(CallStack callStack) {
        StringBuilder codeBuilder = new StringBuilder();
        appendMethodCode(codeBuilder, callStack);
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, 1);
        }
        return codeBuilder.toString();
    }

    private static void collectCodeFromChildCallStack(StringBuilder codeBuilder, CallStack callStack, int depth) {
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

    private static void appendMethodCode(StringBuilder codeBuilder, CallStack callStack) {
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

    public static PsiMethod findMethodUnderCaret(PsiFile psiFile, int offset) {
        return getContainingMethodAtOffset(psiFile, offset);
    }
}
