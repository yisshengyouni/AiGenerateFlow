package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.util.MethodUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;

import java.util.HashMap;
import java.util.Map;

/**
 * @author huqiang
 * @since 2024/7/13 16:01
 */
public class MethodChainAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(MethodChainAction.class);

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile)) {
            return;
        }
        Editor editor = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);
        if (editor == null) {
            return;
        }

        /*PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
            return;
        }*/

        Map<PsiMethod, PsiMethod> methodChains = new HashMap<>();

        LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
        int offset = editor.logicalPositionToOffset(logicalPosition);
        PsiMethod currentMethod = MethodUtils.getContainingMethodAtOffset(psiFile, offset);
        if (currentMethod == null) {
            return;
        }
        MethodChainVisitor methodChainVisitor = new MethodChainVisitor();
//        currentMethod.accept(methodChainVisitor);
        CallStack callStack = methodChainVisitor.generateMethodChains(currentMethod, null);
//        currentMethod.accept();

        MethodChainGraph graph = new MethodChainGraph(callStack);
        graph.show();
    }
}
