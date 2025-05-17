package com.huq.idea.flow.util;

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
}
