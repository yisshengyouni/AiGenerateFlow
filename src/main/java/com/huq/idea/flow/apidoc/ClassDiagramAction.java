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

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
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
                SwingUtilities.invokeLater(() ->
                    showInitialDialog(project, collectedCode, currentClass.getName()));
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

    private void showInitialDialog(Project project, String collectedCode, String title) {
        JTabbedPane tabbedPane = new JTabbedPane();
        JPanel plantUmlTab = createInitialPlantUmlTab(project, title, collectedCode);
        tabbedPane.addTab("PlantUML视图", plantUmlTab);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel enhancedLabel = new JLabel();
        enhancedLabel.setForeground(Color.BLUE);
        bottomPanel.add(enhancedLabel);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        UmlFlowService plugin = project.getService(UmlFlowService.class);
        mainPanel.setName(title);
        plugin.addFlow(mainPanel);
    }

    private JPanel createInitialPlantUmlTab(Project project, String title, String collectedCode) {
        JPanel panel = new JPanel(new BorderLayout());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(500);
        splitPane.setResizeWeight(0.5);

        JPanel codePanel = new JPanel(new BorderLayout());
        JTextArea textArea = new JTextArea();
        textArea.setEditable(true);
        textArea.setText(collectedCode);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        codePanel.add(new JScrollPane(textArea), BorderLayout.CENTER);

        JPanel diagramPanel = new JPanel(new BorderLayout());
        JLabel waitingLabel = new JLabel("点击\"生成类图\"按钮生成类图", SwingConstants.CENTER);
        waitingLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        diagramPanel.add(waitingLabel, BorderLayout.CENTER);

        splitPane.setLeftComponent(codePanel);
        splitPane.setRightComponent(diagramPanel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JLabel providerLabel = new JLabel("AI提供商:");
        java.util.List<IdeaSettings.CustomAiProviderConfig> availableProviders = AiUtils.getCustomProviders();
        JComboBox<IdeaSettings.CustomAiProviderConfig> providerComboBox = new JComboBox<>(availableProviders.toArray(new IdeaSettings.CustomAiProviderConfig[0]));

        JLabel specificModelLabel = new JLabel("具体模型:");
        JComboBox<String> specificModelComboBox = new JComboBox<>();

        providerComboBox.addActionListener(e -> {
            IdeaSettings.CustomAiProviderConfig selected = (IdeaSettings.CustomAiProviderConfig) providerComboBox.getSelectedItem();
            specificModelComboBox.removeAllItems();
            if (selected != null && selected.getModels() != null) {
                String[] models = selected.getModels().split(",");
                for (String model : models) {
                    specificModelComboBox.addItem(model.trim());
                }
                if (models.length > 0) {
                    specificModelComboBox.setSelectedIndex(0);
                }
            }
        });

        if (!availableProviders.isEmpty()) {
            providerComboBox.setSelectedIndex(-1);
            providerComboBox.setSelectedIndex(0);
        }

        providerComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof IdeaSettings.CustomAiProviderConfig) {
                    IdeaSettings.CustomAiProviderConfig provider = (IdeaSettings.CustomAiProviderConfig) value;
                    setText(provider.getName());
                }
                return this;
            }
        });

        buttonPanel.add(providerLabel);
        buttonPanel.add(providerComboBox);
        buttonPanel.add(specificModelLabel);
        buttonPanel.add(specificModelComboBox);

        JButton generateButton = new JButton("生成类图");
        generateButton.addActionListener(e -> {
            generateButton.setEnabled(false);
            generateButton.setText("生成中...");

            IdeaSettings.CustomAiProviderConfig selectedProvider = (IdeaSettings.CustomAiProviderConfig) providerComboBox.getSelectedItem();
            String selectedModel = (String) specificModelComboBox.getSelectedItem();

            if (selectedProvider == null) {
                Notifications.Bus.notify(new Notification(
                        "com.yt.huq.idea",
                        "无可用AI模型",
                        "未选择AI提供商或没有可用的AI提供商。请在设置 > UmlFlowAiConfigurable 中配置至少一个提供商",
                        NotificationType.ERROR),
                        project);
                generateButton.setEnabled(true);
                generateButton.setText("生成类图");
                return;
            }
            if (IdeaSettings.getInstance().getState().getPlantumlPathVal() == null ||
                    IdeaSettings.getInstance().getState().getPlantumlPathVal().trim().isEmpty()) {
                Notifications.Bus.notify(new Notification(
                        "com.yt.huq.idea",
                        "PlantUML路径缺失",
                        "PlantUML路径未配置。请在设置 > UmlFlowAiConfigurable 中设置",
                        NotificationType.ERROR),
                        project);
                generateButton.setEnabled(true);
                generateButton.setText("生成类图");
                return;
            }

            new Task.Backgroundable(project, "生成类图", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("正在生成PlantUML类图...");
                    indicator.setIndeterminate(true);

                    String classDiagramPromptTemplate = IdeaSettings.getInstance().getState().getClassDiagramPrompt();
                    String flowPrompt = String.format(classDiagramPromptTemplate, collectedCode);

                    AiUtils.AiConfig config = new AiUtils.AiConfig(selectedProvider, selectedModel);
                    if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
                        SwingUtilities.invokeLater(() -> {
                            generateButton.setEnabled(true);
                            Notifications.Bus.notify(new Notification(
                                "com.yt.huq.idea",
                                "API密钥未配置",
                                "请在设置中为 " + selectedProvider.getName() + " 配置API密钥",
                                NotificationType.WARNING),
                                project);
                        });
                        return;
                    }

                    config.setSystemMessage("你是一个专业的PlantUML类图生成专家，擅长分析Java代码并生成高质量的类图。")
                          .setTemperature(0.7)
                          .setMaxTokens(8000);

                    AiUtils.AiResponse response = AiUtils.callAi(flowPrompt, config);
                    String classDiagram = response.isSuccess() ? response.getContent() : null;

                    if (classDiagram != null && !classDiagram.isEmpty()) {
                        classDiagram = cleanupUmlResponse(classDiagram);

                        String finalClassDiagram = classDiagram;
                        SwingUtilities.invokeLater(() -> {
                            textArea.setText(finalClassDiagram);
                            JPanel newDiagramPanel = PlantUmlRenderer.createPlantUmlPanel(finalClassDiagram);
                            splitPane.setRightComponent(newDiagramPanel);

                            generateButton.setEnabled(true);
                            generateButton.setText("重新生成");

                            panel.revalidate();
                            panel.repaint();
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            String errorMsg = "生成类图失败，请检查API设置和网络连接";
                            if (response != null && !response.isSuccess() && response.getErrorMessage() != null) {
                                errorMsg = "生成类图失败: " + response.getErrorMessage();
                            }

                            Notifications.Bus.notify(new Notification(
                                    "com.yt.huq.idea",
                                    "类图生成",
                                    errorMsg,
                                    NotificationType.ERROR),
                                    project);

                            generateButton.setEnabled(true);
                            generateButton.setText("生成类图");
                        });
                    }
                }
            }.queue();
        });
        buttonPanel.add(generateButton);

        JButton copyButton = new JButton("复制到剪贴板");
        copyButton.addActionListener(e -> {
            copyToClipboard(textArea.getText());
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "UML图表",
                    "UML图表已复制到剪贴板",
                    NotificationType.INFORMATION),
                    project);
        });
        buttonPanel.add(copyButton);

        JButton saveCodeButton = new JButton("保存代码");
        saveCodeButton.addActionListener(e -> {
            com.intellij.openapi.fileChooser.FileChooserDescriptor descriptor =
                com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor();
            descriptor.setTitle("选择保存目录");

            com.intellij.openapi.vfs.VirtualFile dir = com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, project, null);
            if (dir != null) {
                java.io.File fileToSave = new java.io.File(dir.getPath(), title.replaceAll("[^a-zA-Z0-9]", "_") + ".puml");
                try {
                    java.io.FileWriter writer = new java.io.FileWriter(fileToSave);
                    writer.write(textArea.getText());
                    writer.close();
                    Notifications.Bus.notify(new Notification(
                            "com.yt.huq.idea",
                            "UML图表",
                            "UML代码已保存到 " + fileToSave.getAbsolutePath(),
                            NotificationType.INFORMATION),
                            project);
                } catch (Exception ex) {
                    Notifications.Bus.notify(new Notification(
                            "com.yt.huq.idea",
                            "UML图表",
                            "保存UML代码失败: " + ex.getMessage(),
                            NotificationType.ERROR),
                            project);
                }
            }
        });
        buttonPanel.add(saveCodeButton);

        JButton saveImageButton = new JButton("保存图像");
        saveImageButton.addActionListener(e -> {
            if (textArea.getText().trim().isEmpty() || !textArea.getText().contains("@startuml")) {
                Notifications.Bus.notify(new Notification(
                        "com.yt.huq.idea",
                        "UML图表",
                        "没有有效的UML图表可以保存",
                        NotificationType.WARNING),
                        project);
                return;
            }

            com.intellij.openapi.fileChooser.FileChooserDescriptor descriptor =
                com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor();
            descriptor.setTitle("选择保存目录");

            com.intellij.openapi.vfs.VirtualFile dir = com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, project, null);
            if (dir != null) {
                java.io.File fileToSave = new java.io.File(dir.getPath(), title.replaceAll("[^a-zA-Z0-9]", "_") + ".png");
                try {
                    byte[] pngData = PlantUmlRenderer.renderPlantUmlToPng(textArea.getText());
                    if (pngData != null) {
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(fileToSave);
                        fos.write(pngData);
                        fos.close();
                        Notifications.Bus.notify(new Notification(
                                "com.yt.huq.idea",
                                "UML图表",
                                "UML图像已保存到 " + fileToSave.getAbsolutePath(),
                                NotificationType.INFORMATION),
                                project);
                    }
                } catch (PlantUmlRenderException ex) {
                    LOG.error("Failed to render UML diagram", ex);
                    Notifications.Bus.notify(new Notification(
                            "com.yt.huq.idea",
                            "UML图表",
                            "渲染UML图像失败: " + ex.getMessage(),
                            NotificationType.ERROR),
                            project);
                } catch (Exception ex) {
                    Notifications.Bus.notify(new Notification(
                            "com.yt.huq.idea",
                            "UML图表",
                            "保存UML图像失败: " + ex.getMessage(),
                            NotificationType.ERROR),
                            project);
                }
            }
        });
        buttonPanel.add(saveImageButton);

        JButton saveSvgButton = new JButton("保存SVG");
        saveSvgButton.addActionListener(e -> {
            if (textArea.getText().trim().isEmpty() || !textArea.getText().contains("@startuml")) {
                Notifications.Bus.notify(new Notification(
                        "com.yt.huq.idea",
                        "UML图表",
                        "没有有效的UML图表可以保存",
                        NotificationType.WARNING),
                        project);
                return;
            }

            com.intellij.openapi.fileChooser.FileChooserDescriptor descriptor =
                com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor();
            descriptor.setTitle("选择保存目录");

            com.intellij.openapi.vfs.VirtualFile dir = com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, project, null);
            if (dir != null) {
                java.io.File fileToSave = new java.io.File(dir.getPath(), title.replaceAll("[^a-zA-Z0-9]", "_") + ".svg");
                try {
                    String svgData = PlantUmlRenderer.renderPlantUmlToSvg(textArea.getText());
                    if (svgData != null) {
                        java.io.FileWriter writer = new java.io.FileWriter(fileToSave);
                        writer.write(svgData);
                        writer.close();
                        Notifications.Bus.notify(new Notification(
                                "com.yt.huq.idea",
                                "UML图表",
                                "UML SVG已保存到 " + fileToSave.getAbsolutePath(),
                                NotificationType.INFORMATION),
                                project);
                    }
                } catch (PlantUmlRenderException ex) {
                    LOG.error("Failed to render UML diagram to SVG", ex);
                    Notifications.Bus.notify(new Notification(
                            "com.yt.huq.idea",
                            "UML图表",
                            "渲染UML SVG失败: " + ex.getMessage(),
                            NotificationType.ERROR),
                            project);
                } catch (Exception ex) {
                    Notifications.Bus.notify(new Notification(
                            "com.yt.huq.idea",
                            "UML图表",
                            "保存UML SVG失败: " + ex.getMessage(),
                            NotificationType.ERROR),
                            project);
                }
            }
        });
        buttonPanel.add(saveSvgButton);

        JButton refreshButton = new JButton("刷新图像");
        refreshButton.addActionListener(e -> {
            String updatedUmlContent = textArea.getText();
            JPanel newDiagramPanel = PlantUmlRenderer.createPlantUmlPanel(updatedUmlContent);
            splitPane.setRightComponent(newDiagramPanel);
            splitPane.revalidate();
            splitPane.repaint();
            panel.revalidate();
            panel.repaint();
        });
        buttonPanel.add(refreshButton);

        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void copyToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    private String cleanupUmlResponse(String umlResponse) {
        if (umlResponse == null || umlResponse.isEmpty()) {
            return umlResponse;
        }

        if (!umlResponse.startsWith("@startuml")) {
            int startIndex = umlResponse.indexOf("@startuml");
            if (startIndex >= 0) {
                umlResponse = umlResponse.substring(startIndex);
            } else {
                umlResponse = "@startuml\n" + umlResponse;
            }
        }

        if (!umlResponse.endsWith("@enduml")) {
            if (umlResponse.contains("@enduml")) {
                int endIndex = umlResponse.lastIndexOf("@enduml") + "@enduml".length();
                umlResponse = umlResponse.substring(0, endIndex);
            } else {
                umlResponse = umlResponse + "\n@enduml";
            }
        }

        return umlResponse;
    }
}