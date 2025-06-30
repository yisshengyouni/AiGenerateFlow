package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.apidoc.service.UmlFlowService;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
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
import com.intellij.openapi.project.DumbAware;
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

        // 显示初始对话框，包含"生成流程"按钮
        SwingUtilities.invokeLater(() ->
            showInitialDialog(project, callStack, collectedCode, currentMethod.getClass().getSimpleName()+"."+currentMethod.getName()));
    }

    /**
     * 显示初始对话框，包含"生成流程"按钮
     */
    private void showInitialDialog(Project project, CallStack callStack, String collectedCode, String title) {
        JFrame frame = new JFrame("UML流程图: " + title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1200, 800);


        // 创建选项卡面板
        JTabbedPane tabbedPane = new JTabbedPane();

        // 创建初始PlantUML选项卡（空白）
        JPanel plantUmlTab = createInitialPlantUmlTab(project, frame, collectedCode);
        tabbedPane.addTab("PlantUML视图", plantUmlTab);


        // 底部面板
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel enhancedLabel = new JLabel();
        enhancedLabel.setForeground(Color.BLUE);
        bottomPanel.add(enhancedLabel);

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        UmlFlowService plugin = project.getService(UmlFlowService.class);
        mainPanel.setName(title);
        plugin.addFlow(mainPanel);

//        frame.add(mainPanel);
//        frame.setVisible(true);
    }

    /**
     * 创建初始PlantUML选项卡，包含"生成流程"按钮
     */
    private JPanel createInitialPlantUmlTab(Project project, JFrame parentFrame, String collectedCode) {
        JPanel panel = new JPanel(new BorderLayout());

        // 创建一个分割面板，左侧显示代码，右侧显示图形
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(500);
        splitPane.setResizeWeight(0.5);

        // 左侧代码面板
        JPanel codePanel = new JPanel(new BorderLayout());
        JTextArea textArea = new JTextArea();
        textArea.setEditable(true);
        textArea.setText(collectedCode);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        codePanel.add(new JScrollPane(textArea), BorderLayout.CENTER);

        // 右侧图形面板（初始为空白）
        JPanel diagramPanel = new JPanel(new BorderLayout());
        JLabel waitingLabel = new JLabel("点击\"生成流程\"按钮生成流程图", SwingConstants.CENTER);
        waitingLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        diagramPanel.add(waitingLabel, BorderLayout.CENTER);

        // 添加到分割面板
        splitPane.setLeftComponent(codePanel);
        splitPane.setRightComponent(diagramPanel);

        // 底部按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // AI模型选择下拉框（只显示已配置API密钥的模型）
        JLabel modelLabel = new JLabel("AI模型:");
        java.util.List<AiUtils.AiProvider> availableProviders = AiUtils.getAvailableProviders();
        JComboBox<AiUtils.AiProvider> modelComboBox = new JComboBox<>(availableProviders.toArray(new AiUtils.AiProvider[0]));
        
        // 设置默认选择
        if (!availableProviders.isEmpty()) {
            modelComboBox.setSelectedItem(availableProviders.get(0));
        }
        modelComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof AiUtils.AiProvider) {
                    AiUtils.AiProvider provider = (AiUtils.AiProvider) value;
                    setText(getProviderDisplayName(provider) + " (" + provider.getDefaultModel() + ")");
                }
                return this;
            }
        });
        buttonPanel.add(modelLabel);
        buttonPanel.add(modelComboBox);

        // 生成流程按钮
        JButton generateButton = new JButton("生成流程");
        generateButton.addActionListener(e -> {
            // 禁用按钮，防止重复点击
            generateButton.setEnabled(false);
            generateButton.setText("生成中...");

            // 获取选择的AI提供商
            AiUtils.AiProvider selectedProvider = (AiUtils.AiProvider) modelComboBox.getSelectedItem();
            if (selectedProvider == null) {
                LOG.info("No AI provider selected or available");
                Notifications.Bus.notify(new Notification(
                        "com.yt.huq.idea",
                        "无可用AI模型",
                        "未选择AI模型或没有可用的AI模型。请在设置 > UmlFlowAiConfigurable 中配置至少一个AI模型的API密钥",
                        NotificationType.ERROR),
                        project);
                // 重新启用按钮
                generateButton.setEnabled(true);
                generateButton.setText("生成流程");
                return;
            }
            if (IdeaSettings.getInstance().getState().getPlantumlPathVal() == null ||
                    IdeaSettings.getInstance().getState().getPlantumlPathVal().trim().isEmpty()) {
                LOG.info("PlantUML安装路径未配置，无法渲染流程图");
                Notifications.Bus.notify(new Notification(
                        "com.yt.huq.idea",
                        "PlantUML路径缺失",
                        "PlantUML路径未配置。请在设置 > UmlFlowAiConfigurable 中设置",
                        NotificationType.ERROR),
                        project);
                // 重新启用按钮
                generateButton.setEnabled(true);
                generateButton.setText("生成流程");
                return;
            }

            // 在后台任务中执行AI调用
            new Task.Backgroundable(project, "生成流程图", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("正在生成PlantUML流程图...");
                    indicator.setIndeterminate(true);

                    // 生成PlantUML流程图
                    String flowPromptTemplate = getFlowDiagramPrompt();
                    String flowPrompt = String.format(flowPromptTemplate, collectedCode);
                    
                    // 使用选择的AI模型调用（自动获取API密钥）
                    AiUtils.AiConfig config = AiUtils.createConfigWithApiKey(selectedProvider);
                    if (config == null) {
                        SwingUtilities.invokeLater(() -> {
                            generateButton.setEnabled(true);
                            Notifications.Bus.notify(new Notification(
                                "com.yt.huq.idea",
                                "API密钥未配置",
                                "请在设置中为 " + getProviderDisplayName(selectedProvider) + " 配置API密钥",
                                NotificationType.WARNING),
                                project);
                        });
                        return;
                    }
                    
                    config.setSystemMessage("你是一个专业的PlantUML流程图生成专家，擅长分析Java代码并生成高质量的流程图。")
                          .setTemperature(0.7)
                          .setMaxTokens(8000);
                    
                    AiUtils.AiResponse response = AiUtils.callAi(flowPrompt, config);
                    String flowDiagram = response.isSuccess() ? response.getContent() : null;


                    if (flowDiagram != null && !flowDiagram.isEmpty()) {
                        // 清理响应
                        flowDiagram = cleanupUmlResponse(flowDiagram);

                        // 更新UI
                        String finalFlowDiagram = flowDiagram;
                        SwingUtilities.invokeLater(() -> {
                            // 更新文本区域
                            textArea.setText(finalFlowDiagram);

                            // 更新图形面板
                            JPanel newDiagramPanel = PlantUmlRenderer.createPlantUmlPanel(finalFlowDiagram);
                            splitPane.setRightComponent(newDiagramPanel);


                            // 重新启用按钮
                            generateButton.setEnabled(true);
                            generateButton.setText("重新生成");

                            // 刷新UI
                            panel.revalidate();
                            panel.repaint();
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            String errorMsg = "生成流程图失败，请检查API设置和网络连接";
                            if (response != null && !response.isSuccess() && response.getErrorMessage() != null) {
                                errorMsg = "生成流程图失败: " + response.getErrorMessage();
                            }
                            
                            Notifications.Bus.notify(new Notification(
                                    "com.yt.huq.idea",
                                    "流程图生成",
                                    errorMsg,
                                    NotificationType.ERROR),
                                    project);

                            // 重新启用按钮
                            generateButton.setEnabled(true);
                            generateButton.setText("生成流程");
                        });
                    }
                }
            }.queue();
        });
        buttonPanel.add(generateButton);

        // 复制按钮
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

        // 保存代码按钮
        JButton saveCodeButton = new JButton("保存代码");
        saveCodeButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("保存UML代码");
            fileChooser.setSelectedFile(new java.io.File(parentFrame.getTitle().replaceAll("[^a-zA-Z0-9]", "_") + ".puml"));

            int userSelection = fileChooser.showSaveDialog(parentFrame);
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                java.io.File fileToSave = fileChooser.getSelectedFile();
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

        // 保存图像按钮
        JButton saveImageButton = new JButton("保存图像");
        saveImageButton.addActionListener(e -> {
            // 检查是否有内容可以保存
            if (textArea.getText().trim().isEmpty() || !textArea.getText().contains("@startuml")) {
                Notifications.Bus.notify(new Notification(
                        "com.yt.huq.idea",
                        "UML图表",
                        "没有有效的UML图表可以保存",
                        NotificationType.WARNING),
                        project);
                return;
            }

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("保存UML图像");
            fileChooser.setSelectedFile(new java.io.File(parentFrame.getTitle().replaceAll("[^a-zA-Z0-9]", "_") + ".png"));

            int userSelection = fileChooser.showSaveDialog(parentFrame);
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                java.io.File fileToSave = fileChooser.getSelectedFile();
                try {
                    // 渲染并保存图像
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

            // 刷新UI
            panel.revalidate();
            panel.repaint();
        });
        buttonPanel.add(refreshButton);

        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }


    /**
     * 复制文本到剪贴板
     */
    private void copyToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    /**
     * 清理UML响应，确保它是有效的PlantUML代码
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

    /**
     * 获取AI提供商的显示名称
     */
    private String getProviderDisplayName(AiUtils.AiProvider provider) {
        switch (provider) {
            case DEEPSEEK: return "DeepSeek";
            case OPENAI: return "OpenAI";
            case ANTHROPIC: return "Anthropic (Claude)";
            case MOONSHOT: return "月之暗面 (Moonshot)";
            case BAIDU: return "百度文心一言";
            case ALIBABA: return "阿里通义千问";
            case ZHIPU: return "智谱AI (GLM)";
            default: return provider.name();
        }
    }
}
