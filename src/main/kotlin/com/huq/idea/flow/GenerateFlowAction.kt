package com.huq.idea.flow

import com.huq.idea.flow.apidoc.MethodChainGraph
import com.huq.idea.flow.apidoc.MethodChainVisitor
import com.huq.idea.flow.model.CallStack
import com.huq.idea.flow.util.MethodUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod

/**
 * @author huqiang
 * @since 2024/7/20 18:24
 */
class GenerateFlowAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val psiFile = e.getData<PsiFile>(LangDataKeys.PSI_FILE) as? PsiJavaFile ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
            ?: return

        /*PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
            return;
        }*/
        val methodChains: Map<PsiMethod, PsiMethod> = HashMap<PsiMethod, PsiMethod>()

        val logicalPosition = editor.caretModel.logicalPosition
        val offset = editor.logicalPositionToOffset(logicalPosition)
        val currentMethod: PsiMethod = MethodUtils.getContainingMethodAtOffset(psiFile, offset)
            ?: return
        val methodChainVisitor = MethodChainVisitor()
        //        currentMethod.accept(methodChainVisitor);
        val callStack: CallStack = methodChainVisitor.generateMethodChains(currentMethod, null)

        //        currentMethod.accept();
        val graph = MethodChainGraph(callStack)
        graph.showFlow()
    }
}
