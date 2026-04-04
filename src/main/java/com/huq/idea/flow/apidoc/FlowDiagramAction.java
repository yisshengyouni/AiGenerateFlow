package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.apidoc.service.UmlFlowService;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
import com.huq.idea.flow.util.AiUtils;
import com.huq.idea.flow.util.MethodUtils;
import com.huq.idea.flow.util.PlantUmlRenderException;
import com.huq.idea.flow.util.PlantUmlRenderer;
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
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import com.huq.idea.flow.apidoc.ui.UmlDiagramUIFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Action to generate UML flow diagrams from Java code
 *
 * @author huqiang
 * @since 2024/8/10
 */
public class FlowDiagramAction extends AnAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(FlowDiagramAction.class);

    // 存储当前方法，用于在生成流程图时使用
    private PsiMethod currentMethod;

    // 存储当前项目，用于在生成流程图时使用
    private Project currentProject;

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // 保存当前项目
        this.currentProject = project;

        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile)) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "流程图生成",
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
        this.currentMethod = ReadAction.compute(() -> {
            LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
            int offset = editor.logicalPositionToOffset(logicalPosition);
            return MethodUtils.getContainingMethodAtOffset(psiFile, offset);
        });

        if (this.currentMethod == null) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "流程图生成",
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

        // 收集代码
        String collectedCode = collectCodeFromCallStack(callStack);

        // 显示初始对话框
        SwingUtilities.invokeLater(() -> {
            String title = currentMethod.getClass().getSimpleName() + "." + currentMethod.getName();

            UmlDiagramUIFactory.PromptProvider promptProvider = new UmlDiagramUIFactory.PromptProvider() {
                private ComboBox<IdeaSettings.PromptConfig> promptComboBox;

                @Override
                public String getPrompt(String collectedCode) {
                    IdeaSettings.PromptConfig selectedPromptConfig = promptComboBox != null ? (IdeaSettings.PromptConfig) promptComboBox.getSelectedItem() : null;
                    String flowPromptTemplate = selectedPromptConfig != null ? selectedPromptConfig.getPrompt() : getFlowDiagramPrompt();
                    return String.format(flowPromptTemplate, collectedCode);
                }

                @Override
                public JComboBox<IdeaSettings.PromptConfig> getPromptComboBox() {
                    java.util.List<IdeaSettings.PromptConfig> prompts = IdeaSettings.getInstance().getState().getFlowPrompts();
                    promptComboBox = new ComboBox<>(prompts.toArray(new IdeaSettings.PromptConfig[0]));

                    if (!prompts.isEmpty()) {
                        promptComboBox.setSelectedIndex(0);
                    }
                    promptComboBox.setRenderer(new DefaultListCellRenderer() {
                        @Override
                        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                            if (value instanceof IdeaSettings.PromptConfig) {
                                setText(((IdeaSettings.PromptConfig) value).getName());
                            }
                            return this;
                        }
                    });
                    return promptComboBox;
                }
            };

            UmlDiagramUIFactory.showInitialDialog(project, collectedCode, "UML流程图: " + title, promptProvider, "生成流程");
        });
    }


    /**
     * 收集方法调用链中的所有代码
     */
    private String collectCodeFromCallStack(CallStack callStack) {
        StringBuilder codeBuilder = new StringBuilder();

        // 添加根方法的代码
        appendMethodCode(codeBuilder, callStack);

        // 递归添加子方法的代码
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, 1);
        }

        return codeBuilder.toString();
    }

    /**
     * 递归收集子调用栈中的代码
     */
    private void collectCodeFromChildCallStack(StringBuilder codeBuilder, CallStack callStack, int depth) {
        // 限制递归深度，避免代码过多
        if (depth > 10) {
            return;
        }
        if (!callStack.isRecursive()) {
            // 添加当前方法的代码
            appendMethodCode(codeBuilder, callStack);
        }

        // 递归处理子节点
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, depth + 1);
        }
    }

    /**
     * 将方法的代码添加到构建器中
     */
    private void appendMethodCode(StringBuilder codeBuilder, CallStack callStack) {
        MethodDescription methodDesc = callStack.getMethodDescription();
        if (methodDesc == null) {
            return;
        }

        // 获取方法的完整代码
        String methodCode = methodDesc.getText();
        if (methodCode == null || methodCode.isEmpty()) {
            return;
        }

        if (codeBuilder.indexOf(methodDesc.buildMethodId()) != -1) {
            // 如果已经添加过这个方法的代码，就不再重复添加
            LOG.info("Method already added: " + methodDesc.buildMethodId());
            return;
        }

        // 获取类名和方法名
        String className = methodDesc.getClassName();
        String methodName = methodDesc.getName();

        // 添加分隔符和方法信息
        codeBuilder.append("\n\n// ").append("=".repeat(80)).append("\n");
        codeBuilder.append("// Class: ").append(className).append("\n");
        codeBuilder.append("// Method: ").append(methodName).append("\n");
        codeBuilder.append("// token: ").append(methodDesc.buildMethodId()).append("\n");

        // 添加方法代码
        codeBuilder.append(methodCode);
        codeBuilder.append("\n// ").append("=".repeat(80)).append("\n\n");
    }

    /**
     * 获取流程图提示词
     */
    private String getFlowDiagramPrompt() {
        // 从设置中获取流程图提示词
        return IdeaSettings.getInstance().getState().getBuildFlowPrompt();
    }

}
