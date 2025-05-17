package com.huq.idea.build;

import com.huq.idea.config.IdeaSettings;
import com.huq.idea.flow.util.AiUtils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.RunnableCallable;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.Arrays;

/**
 * 通过 ai 生成 pojo的组装方法，填充属性值
 *
 * @author huqiang
 * @since 2024/8/1 14:12
 */
public class BuildObjectAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        Project project = e.getProject();
        if (!(psiFile instanceof PsiJavaFile)) {
            return;
        }
        PsiClass psiClass = PsiTreeUtil.getChildOfType(psiFile, PsiClass.class);
        if (psiClass == null) {
            return;
        }
        String psiClassText = psiClass.getText();
        PsiField[] allFields = psiClass.getAllFields();
        PsiClass[] interfaces = psiClass.getInterfaces();
        boolean serializable = Arrays.stream(interfaces).anyMatch(p -> p.getName().equals("Serializable"));
        if (!serializable) {
            // 未实现 serializable 接口的对象，不需要处理
            return;
        }
        String methodName = "build" + psiClass.getName();
        // 引导提示词：
        IdeaSettings.State state = IdeaSettings.getInstance().getState();
        String guidePrompt = state.getBuildMethodPrompt().replaceAll("methodName", methodName);
        /*String prompt = "引导提示词：\n" +
                "创建一个 `" + methodName + "` 方法，并根据每个属性的注释和名称，填充符合实际业务场景的示例值。确保所有属性都被赋值。\n" +
                "输出结果中只保留 `" + methodName + "` 方法的代码 。\n" + psiClassText;*/
        String prompt = guidePrompt + psiClassText;
        WriteCommandAction.Builder builder = WriteCommandAction.writeCommandAction(project).withGlobalUndo()
                .withGroupId("BuildObjectAction").withName("BuildObjectAction");
        long start = System.currentTimeMillis();

        final BackgroundableProcessIndicator progressIndicator =
                new BackgroundableProcessIndicator(
                        project,
                        "Generate build method ...",
                        PerformInBackgroundOption.ALWAYS_BACKGROUND,
                        "Stop",
                        "Stop",
                        false);

        RunnableCallable callable = new RunnableCallable(() -> {
            String response = AiUtils.okRequest(prompt);
            long end = System.currentTimeMillis();
            progressIndicator.processFinish();
            if (response == null || response.isEmpty()) {
                Notifications.Bus.notify(new Notification("com.yt.huq.idea", "Create build method fail, Copied to pasteboard, elapsed " + (end - start) + "ms",
                        NotificationType.WARNING), project);
                return;
            }
            response = response.replaceAll("```java", "").replaceAll("```", "");
            copyToClipboard(response);
            Notifications.Bus.notify(new Notification("com.yt.huq.idea", "Create build method finish, Copied to pasteboard, elapsed " + (end - start) + "ms",
                    NotificationType.INFORMATION), project);
        });

        ReadAction.nonBlocking(callable)
                .wrapProgress(progressIndicator)
                .inSmartMode(project)
                .finishOnUiThread(ModalityState.defaultModalityState(), (title) -> {})
                .submit(NonUrgentExecutor.getInstance());

    }


    public static void copyToClipboard(String content) {
        StringSelection selection = new StringSelection(content);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);
    }
}
