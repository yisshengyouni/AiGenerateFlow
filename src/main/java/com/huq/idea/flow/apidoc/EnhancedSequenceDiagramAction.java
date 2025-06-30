package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.util.AiUtils;
import com.huq.idea.flow.util.MethodUtils;
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

/**
 * Action to analyze method call chain with enhanced visitor and generate UML sequence diagram
 *
 * @author huqiang
 * @since 2024/8/10
 */
public class EnhancedSequenceDiagramAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(EnhancedSequenceDiagramAction.class);

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
                    "Method Chain Analysis",
                    "This action only works with Java files",
                    NotificationType.ERROR),
                    project);
            return;
        }

        Editor editor = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);
        if (editor == null) {
            return;
        }

        // Get the method at the current cursor position
        // Wrap in a read action to avoid threading issues
        this.currentMethod = ReadAction.compute(() -> {
            LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
            int offset = editor.logicalPositionToOffset(logicalPosition);
            return MethodUtils.getContainingMethodAtOffset(psiFile, offset);
        });
        if (this.currentMethod == null) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "方法链分析",
                    "光标位置未找到方法",
                    NotificationType.ERROR),
                    project);
            return;
        }

        // Start the analysis in a background task
        new Task.Backgroundable(project, "Analyzing Method Chain", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Analyzing method call chain with enhanced visitor...");
                indicator.setIndeterminate(true);

                // Generate the method call chain using the enhanced visitor
                // Wrap in a read action to avoid threading issues
                CallStack callStack = com.intellij.openapi.application.ReadAction.compute(() -> {
                    EnhancedMethodChainVisitor methodChainVisitor = new EnhancedMethodChainVisitor();
                    return methodChainVisitor.generateMethodChains(currentMethod, null);
                });

                // First show the raw PlantUML sequence diagram
                String plantUmlCode = callStack.generateUml();
                SwingUtilities.invokeLater(() -> showUmlDialog(project, plantUmlCode, currentMethod.getName() + " (Sequence Diagram)", false));

                // 检查是否有可用的AI模型配置
                java.util.List<AiUtils.AiProvider> availableProviders = AiUtils.getAvailableProviders();
                if (availableProviders.isEmpty()) {
                    LOG.info("No AI provider configured, skipping AI API call");
                    Notifications.Bus.notify(new Notification(
                            "com.yt.huq.idea",
                            "AI Configuration Missing",
                            "No AI provider is configured. Please configure at least one AI provider in Settings > UmlFlowAiConfigurable",
                            NotificationType.ERROR),
                            project);
                    return;
                }

                // 流程图生成功能已移至FlowDiagramAction

                // 生成增强的序列图
                /*indicator.setText("Generating enhanced sequence diagram with DeepSeek API...");
                String sequencePromptTemplate = IdeaSettings.getInstance().getState().getUmlSequencePrompt();
                String sequencePrompt = String.format(sequencePromptTemplate, plantUmlCode);
                String enhancedSequenceUml = AiUtils.okRequest(sequencePrompt);

                if (enhancedSequenceUml != null && !enhancedSequenceUml.isEmpty()) {
                    // 清理响应
                    enhancedSequenceUml = cleanupUmlResponse(enhancedSequenceUml);

                    // 显示增强的序列图
                    String finalEnhancedUml = enhancedSequenceUml;
                    SwingUtilities.invokeLater(() -> showUmlDialog(project, finalEnhancedUml,
                            currentMethod.getName() + " (Enhanced Sequence Diagram)", true));
                }*/
            }

        }.queue();
    }

    private void showUmlDialog(Project project, String umlContent, String title, boolean isEnhanced) {
        String dialogTitle = "UML序列图: ";
        JFrame frame = new JFrame(dialogTitle + title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1200, 800);

        // 创建一个分割面板，左侧显示代码，右侧显示图形
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(500);
        splitPane.setResizeWeight(0.5);

        // 左侧代码面板
        JPanel codePanel = new JPanel(new BorderLayout());
        JTextArea textArea = new JTextArea();
        textArea.setEditable(true);
        textArea.setText(umlContent);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        codePanel.add(new JScrollPane(textArea), BorderLayout.CENTER);

        // 右侧图形面板
        JPanel diagramPanel = PlantUmlRenderer.createPlantUmlPanel(umlContent);

        // 添加到分割面板
        splitPane.setLeftComponent(codePanel);
        splitPane.setRightComponent(diagramPanel);

        // 底部按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // 复制按钮
        JButton copyButton = new JButton("复制到剪贴板");
        copyButton.addActionListener(e -> {
            copyToClipboard(umlContent);
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "UML图表",
                    "UML图表已复制到剪贴板",
                    NotificationType.INFORMATION),
                    project);
        });
        buttonPanel.add(copyButton);

        // 保存代码按钮
        JButton saveCodeButton = new JButton("保存代码");
        saveCodeButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("保存UML代码");
            fileChooser.setSelectedFile(new java.io.File(title.replaceAll("[^a-zA-Z0-9]", "_") + ".puml"));

            int userSelection = fileChooser.showSaveDialog(frame);
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                java.io.File fileToSave = fileChooser.getSelectedFile();
                try {
                    java.io.FileWriter writer = new java.io.FileWriter(fileToSave);
                    writer.write(umlContent);
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

        // 保存图像按钮
        JButton saveImageButton = new JButton("保存图像");
        saveImageButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("保存UML图像");
            fileChooser.setSelectedFile(new java.io.File(title.replaceAll("[^a-zA-Z0-9]", "_") + ".png"));

            int userSelection = fileChooser.showSaveDialog(frame);
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                java.io.File fileToSave = fileChooser.getSelectedFile();
                try {
                    // 渲染并保存图像
                    byte[] pngData = PlantUmlRenderer.renderPlantUmlToPng(umlContent);
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
                    } else {
                        Notifications.Bus.notify(new Notification(
                                "com.yt.huq.idea",
                                "UML图表",
                                "渲染UML图像失败",
                                NotificationType.ERROR),
                                project);
                    }
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

        // 刷新图像按钮
        JButton refreshButton = new JButton("刷新图像");
        refreshButton.addActionListener(e -> {
            // 获取当前文本区域的内容
            String updatedUmlContent = textArea.getText();

            // 创建新的图形面板
            JPanel newDiagramPanel = PlantUmlRenderer.createPlantUmlPanel(updatedUmlContent);

            // 替换旧的图形面板
            splitPane.setRightComponent(newDiagramPanel);
            splitPane.revalidate();
            splitPane.repaint();
        });
        buttonPanel.add(refreshButton);

        // 添加增强标签
        if (isEnhanced) {
            JLabel enhancedLabel = new JLabel("由DeepSeek AI增强");
            enhancedLabel.setForeground(Color.BLUE);
            buttonPanel.add(Box.createHorizontalStrut(20));
            buttonPanel.add(enhancedLabel);
        }

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    // 存储当前方法，用于在生成流程图时使用
    private PsiMethod currentMethod;

    private void copyToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    /**
     * 清理UML响应，确保它是有效的PlantUML代码
     * @param umlResponse AI返回的UML代码
     * @return 清理后的UML代码
     */
    private String cleanupUmlResponse(String umlResponse) {
        if (umlResponse == null || umlResponse.isEmpty()) {
            return umlResponse;
        }

        // 确保以@startuml开始
        if (!umlResponse.startsWith("@startuml")) {
            int startIndex = umlResponse.indexOf("@startuml");
            if (startIndex >= 0) {
                umlResponse = umlResponse.substring(startIndex);
            } else {
                umlResponse = "@startuml\n" + umlResponse;
            }
        }

        // 确保以@enduml结束
        if (!umlResponse.endsWith("@enduml")) {
            if (umlResponse.contains("@enduml")) {
                // 截取到最后一个@enduml
                int endIndex = umlResponse.lastIndexOf("@enduml") + "@enduml".length();
                umlResponse = umlResponse.substring(0, endIndex);
            } else {
                umlResponse = umlResponse + "\n@enduml";
            }
        }

        return umlResponse;
    }

    // 流程图相关方法已移至FlowDiagramAction
}
