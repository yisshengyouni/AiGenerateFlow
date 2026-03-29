package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.apidoc.window.CallChainAnalysisDialog;
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

import javax.swing.*;

/**
 * Action to analyze method call chain and provide AI summary
 */
public class CallChainAnalysisAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(CallChainAnalysisAction.class);

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);

        // Enable only if there's an active editor and a Java file
        e.getPresentation().setEnabledAndVisible(project != null && editor != null && psiFile instanceof PsiJavaFile);
    }

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
                    "代码调用链分析",
                    "此操作仅适用于Java文件",
                    NotificationType.ERROR),
                    project);
            return;
        }

        Editor editor = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);
        if (editor == null) {
            return;
        }

        // 获取当前光标位置的方法
        PsiMethod currentMethod = ReadAction.compute(() -> {
            LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
            int offset = editor.logicalPositionToOffset(logicalPosition);
            return MethodUtils.getContainingMethodAtOffset(psiFile, offset);
        });

        if (currentMethod == null) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "代码调用链分析",
                    "光标位置未找到方法",
                    NotificationType.ERROR),
                    project);
            return;
        }

        // 首先分析方法调用链
        CallStack callStack = ReadAction.compute(() -> {
            EnhancedMethodChainVisitor methodChainVisitor = new EnhancedMethodChainVisitor();
            return methodChainVisitor.generateMethodChains(currentMethod, null);
        });

        // 计算标题，必须在 ReadAction 中进行
        String dialogTitle = ReadAction.compute(() -> {
            if (currentMethod.getContainingClass() != null) {
                return currentMethod.getContainingClass().getName() + "." + currentMethod.getName();
            }
            return currentMethod.getName();
        });

        // 显示对话框
        SwingUtilities.invokeLater(() -> {
            CallChainAnalysisDialog dialog = new CallChainAnalysisDialog(project, callStack, dialogTitle);
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        });
    }
}
