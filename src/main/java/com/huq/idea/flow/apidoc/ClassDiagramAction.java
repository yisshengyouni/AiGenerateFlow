package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.apidoc.service.UmlFlowService;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.huq.idea.flow.util.AiUtils;
import com.huq.idea.flow.util.MyPsiUtil;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import com.huq.idea.flow.apidoc.ui.UmlDiagramUIFactory;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Action to generate UML class diagrams from Java code
 *
 * @author huqiang
 * @since 2024/8/10
 */
public class ClassDiagramAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(ClassDiagramAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile)) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "类图生成",
                    "此操作仅适用于Java文件",
                    NotificationType.ERROR),
                    project);
            return;
        }

        Editor editor = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);
        PsiClass targetClass = null;

        if (editor != null) {
            // 获取当前光标位置的类
            targetClass = ReadAction.compute(() -> {
                LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
                int offset = editor.logicalPositionToOffset(logicalPosition);
                PsiElement element = psiFile.findElementAt(offset);
                return PsiTreeUtil.getParentOfType(element, PsiClass.class);
            });
        } else {
            // 从文件获取类（例如在项目视图中右键点击）
            targetClass = ReadAction.compute(() -> {
                PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
                if (classes.length > 0) {
                    return classes[0];
                }
                return null;
            });
        }

        if (targetClass == null) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "类图生成",
                    "未找到类",
                    NotificationType.ERROR),
                    project);
            return;
        }

        final PsiClass currentClass = targetClass;

        IdeaSettings.State settings = IdeaSettings.getInstance().getState();

        // 在后台任务中执行耗时的扫描，避免冻结UI
        new Task.Backgroundable(project, "扫描类关联", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("正在扫描关联类...");

                // 收集关联的类
                Set<PsiClass> associatedClasses = ReadAction.compute(() -> {
                    Set<PsiClass> classes = new HashSet<>();
                    // The root class is always included, ignore the generic filter for depth 0
                    collectAssociatedClasses(currentClass, classes, 0, settings);
                    return classes;
                });

                indicator.setText("正在收集源码...");
                // 收集代码
                String collectedCode = ReadAction.compute(() -> collectCodeFromClasses(associatedClasses));

                // 显示初始对话框
                SwingUtilities.invokeLater(() -> {
                    UmlDiagramUIFactory.PromptProvider promptProvider = new UmlDiagramUIFactory.PromptProvider() {
                        @Override
                        public String getPrompt(String collectedCode) {
                            String classDiagramPromptTemplate = IdeaSettings.getInstance().getState().getClassDiagramPrompt();
                            return String.format(classDiagramPromptTemplate, collectedCode);
                        }
                    };
                    UmlDiagramUIFactory.showInitialDialog(project, collectedCode, "UML类图: " + currentClass.getName(), promptProvider, "生成类图");
                });
            }
        }.queue();
    }

    private void collectAssociatedClasses(PsiClass psiClass, Set<PsiClass> collected, int depth, IdeaSettings.State settings) {
        if (psiClass == null || depth > settings.getClassDiagramDepth() || collected.contains(psiClass)) {
            return;
        }

        // 排除 compiled class files with no readable source or strictly jar file system
        if (MyPsiUtil.isInClassFile(psiClass)) {
            return;
        }

        if (!settings.isIncludeLibrarySources() && MyPsiUtil.isInJarFileSystem(psiClass)) {
            return;
        }

        // Additional safeguard for JDK classes even if includeLibrarySources is true
        if (settings.isIncludeLibrarySources() && MyPsiUtil.isInJarFileSystem(psiClass)) {
             String qName = psiClass.getQualifiedName();
             if (qName != null && (qName.startsWith("java.") || qName.startsWith("javax.") || qName.startsWith("jdk."))) {
                 return;
             }
        }

        String qualifiedName = psiClass.getQualifiedName();
        // Skip filtering for the root target class (depth == 0) so the diagram always generates the target
        if (depth > 0 && qualifiedName != null) {
            // Check if class matches any excluded pattern
            boolean isExcludedClass = settings.getClassExcludedClassPatterns().stream()
                    .anyMatch(pattern -> matchesWildcardPattern(qualifiedName, pattern));
            if (isExcludedClass) {
                return;
            }

            // Check if class matches any relevant pattern
            boolean isRelevantClass = settings.getClassRelevantClassPatterns().stream()
                    .anyMatch(pattern -> matchesWildcardPattern(qualifiedName, pattern));
            if (!isRelevantClass) {
                return;
            }
        }

        collected.add(psiClass);

        // 父类
        PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null) {
            collectAssociatedClasses(superClass, collected, depth + 1, settings);
        }

        // 接口
        PsiClass[] interfaces = psiClass.getInterfaces();
        for (PsiClass intf : interfaces) {
            collectAssociatedClasses(intf, collected, depth + 1, settings);
        }

        // 字段
        PsiField[] fields = psiClass.getFields();
        for (PsiField field : fields) {
            resolveAllClassesInType(field.getType(), collected, depth + 1, settings);
        }

        // 方法返回值和参数
        PsiMethod[] methods = psiClass.getMethods();
        for (PsiMethod method : methods) {
            PsiType returnType = method.getReturnType();
            if (returnType != null) {
                resolveAllClassesInType(returnType, collected, depth + 1, settings);
            }

            PsiParameter[] parameters = method.getParameterList().getParameters();
            for (PsiParameter parameter : parameters) {
                resolveAllClassesInType(parameter.getType(), collected, depth + 1, settings);
            }
        }
    }

    private void resolveAllClassesInType(PsiType type, Set<PsiClass> collected, int depth, IdeaSettings.State settings) {
        if (type == null) {
            return;
        }

        if (type instanceof com.intellij.psi.PsiClassType) {
            com.intellij.psi.PsiClassType classType = (com.intellij.psi.PsiClassType) type;
            PsiClass resolvedClass = classType.resolve();
            if (resolvedClass != null) {
                collectAssociatedClasses(resolvedClass, collected, depth, settings);
            }

            PsiType[] parameters = classType.getParameters();
            for (PsiType paramType : parameters) {
                resolveAllClassesInType(paramType, collected, depth, settings);
            }
        }
    }

    private boolean matchesWildcardPattern(String str, String wildcardPattern) {
        if (str == null || wildcardPattern == null) {
            return false;
        }

        // Convert wildcard pattern to regex pattern
        String regexPattern = wildcardPattern
                .replace(".", "\\.")  // Escape dots
                .replace("*", ".*");  // Convert * to .*

        return str.matches(regexPattern);
    }

    private String collectCodeFromClasses(Set<PsiClass> classes) {
        StringBuilder codeBuilder = new StringBuilder();
        for (PsiClass psiClass : classes) {
            String classCode = psiClass.getText();
            if (classCode != null && !classCode.isEmpty()) {
                codeBuilder.append("\n\n// ").append("=".repeat(80)).append("\n");
                codeBuilder.append("// Class: ").append(psiClass.getQualifiedName()).append("\n");
                codeBuilder.append("// ").append("=".repeat(80)).append("\n\n");
                codeBuilder.append(classCode);
            }
        }
        return codeBuilder.toString();
    }
}