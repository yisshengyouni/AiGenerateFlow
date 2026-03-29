package com.huq.idea.flow.apidoc.window;

import com.huq.idea.flow.config.config.IdeaSettings;
import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
import com.huq.idea.flow.util.AiUtils;
import com.huq.idea.flow.apidoc.service.AgenticCallChainAnalyzer;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import com.intellij.icons.AllIcons;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.util.List;

public class CallChainAnalysisDialog extends JFrame {
    private static final Logger LOG = Logger.getInstance(CallChainAnalysisDialog.class);
    private final Project project;
    private final CallStack rootStack;
    private final String title;
    private JTree callTree;
    private JTextArea codeTextArea;
    private JTextArea aiResponseArea;
    private JComboBox<IdeaSettings.CustomAiProviderConfig> aiProviderCombo;
    private JComboBox<String> specificModelCombo;
    private JButton aiAnalysisButton;
    private JButton agentAnalysisButton;

    public CallChainAnalysisDialog(Project project, CallStack callStack, String title) {
        this.project = project;
        this.rootStack = callStack;
        this.title = title;

        setTitle("代码深度调用链分析: " + title);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1200, 800);
        setLayout(new BorderLayout());

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerLocation(400);

        JPanel treePanel = createTreePanel(rootStack);
        mainSplitPane.setLeftComponent(treePanel);

        JSplitPane detailsSplitPane = createDetailsPanel();
        mainSplitPane.setRightComponent(detailsSplitPane);

        add(mainSplitPane, BorderLayout.CENTER);
    }

    private JSplitPane createDetailsPanel() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(300);

        // Top: Code Viewer
        JPanel codePanel = new JPanel(new BorderLayout());
        codePanel.setBorder(BorderFactory.createTitledBorder("方法代码"));
        codeTextArea = new JTextArea();
        codeTextArea.setEditable(false);
        codeTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        codePanel.add(new JScrollPane(codeTextArea), BorderLayout.CENTER);

        // Bottom: AI Summary
        JPanel aiPanel = new JPanel(new BorderLayout());
        aiPanel.setBorder(BorderFactory.createTitledBorder("AI 深度分析总结"));

        aiResponseArea = new JTextArea();
        aiResponseArea.setEditable(false);
        aiResponseArea.setLineWrap(true);
        aiResponseArea.setWrapStyleWord(true);
        aiResponseArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        aiPanel.add(new JScrollPane(aiResponseArea), BorderLayout.CENTER);

        // Add AI Control Panel
        JPanel controlPanel = createAiControlPanel();
        aiPanel.add(controlPanel, BorderLayout.NORTH);

        splitPane.setTopComponent(codePanel);
        splitPane.setBottomComponent(aiPanel);

        return splitPane;
    }

    private JPanel createAiControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        panel.add(new JLabel("AI 提供商:"));
        List<IdeaSettings.CustomAiProviderConfig> providers = AiUtils.getCustomProviders();
        aiProviderCombo = new JComboBox<>(providers.toArray(new IdeaSettings.CustomAiProviderConfig[0]));

        panel.add(new JLabel("具体模型:"));
        specificModelCombo = new JComboBox<>();

        aiProviderCombo.addActionListener(e -> {
            IdeaSettings.CustomAiProviderConfig selected = (IdeaSettings.CustomAiProviderConfig) aiProviderCombo.getSelectedItem();
            specificModelCombo.removeAllItems();
            if (selected != null && selected.getModels() != null) {
                String[] models = selected.getModels().split(",");
                for (String model : models) {
                    specificModelCombo.addItem(model.trim());
                }
                if (models.length > 0) {
                    specificModelCombo.setSelectedIndex(0);
                }
            }
        });

        if (!providers.isEmpty()) {
            aiProviderCombo.setSelectedIndex(-1);
            aiProviderCombo.setSelectedIndex(0);
        }

        aiProviderCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof IdeaSettings.CustomAiProviderConfig) {
                    setText(((IdeaSettings.CustomAiProviderConfig) value).getName());
                }
                return this;
            }
        });

        panel.add(aiProviderCombo);
        panel.add(specificModelCombo);

        aiAnalysisButton = new JButton("AI 静态分析 (当前视图)");
        aiAnalysisButton.addActionListener(e -> performAiAnalysis());
        panel.add(aiAnalysisButton);

        agentAnalysisButton = new JButton("AI 智能探究 (Agent模式)");
        agentAnalysisButton.setToolTipText("AI 将自动阅读入口代码并动态请求底层源码，直到完全理解业务逻辑。");
        agentAnalysisButton.addActionListener(e -> performAgentAnalysis());
        panel.add(agentAnalysisButton);

        return panel;
    }

    private void performAgentAnalysis() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) callTree.getLastSelectedPathComponent();
        CallStack stackToAnalyze;
        if (selectedNode != null && selectedNode.getUserObject() instanceof CallStack) {
            stackToAnalyze = (CallStack) selectedNode.getUserObject();
        } else {
            stackToAnalyze = rootStack;
        }

        MethodDescription desc = stackToAnalyze.getMethodDescription();
        if (desc == null || desc.getText() == null || desc.getText().isEmpty()) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "分析失败",
                    "无法获取所选节点的源代码。",
                    NotificationType.ERROR),
                    project);
            return;
        }

        IdeaSettings.CustomAiProviderConfig selectedProvider = (IdeaSettings.CustomAiProviderConfig) aiProviderCombo.getSelectedItem();
        String selectedModel = (String) specificModelCombo.getSelectedItem();

        if (selectedProvider == null) {
            Notifications.Bus.notify(new Notification("com.yt.huq.idea", "配置错误", "未选择AI提供商", NotificationType.ERROR), project);
            return;
        }

        agentAnalysisButton.setEnabled(false);
        aiAnalysisButton.setEnabled(false);
        agentAnalysisButton.setText("Agent 探究中...");
        aiResponseArea.setText("正在启动 AI Agent 探究模式...\n");

        new Task.Backgroundable(project, "AI Agent 深度调用链分析", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                AgenticCallChainAnalyzer analyzer = new AgenticCallChainAnalyzer(project, selectedProvider, selectedModel, msg -> {
                    SwingUtilities.invokeLater(() -> aiResponseArea.append(msg + "\n"));
                });

                String finalReport = analyzer.analyze(desc.getText(), indicator);

                SwingUtilities.invokeLater(() -> {
                    agentAnalysisButton.setEnabled(true);
                    aiAnalysisButton.setEnabled(true);
                    agentAnalysisButton.setText("AI 智能探究 (Agent模式)");
                    aiResponseArea.setText(finalReport);
                    aiResponseArea.setCaretPosition(0);
                });
            }
        }.queue();
    }

    private void performAiAnalysis() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) callTree.getLastSelectedPathComponent();
        CallStack stackToAnalyze;
        if (selectedNode != null && selectedNode.getUserObject() instanceof CallStack) {
            stackToAnalyze = (CallStack) selectedNode.getUserObject();
        } else {
            stackToAnalyze = rootStack; // Default to root if nothing is selected
        }

        IdeaSettings.CustomAiProviderConfig selectedProvider = (IdeaSettings.CustomAiProviderConfig) aiProviderCombo.getSelectedItem();
        String selectedModel = (String) specificModelCombo.getSelectedItem();

        if (selectedProvider == null) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "无可用AI模型",
                    "未选择AI提供商或没有可用的AI提供商。请在设置中配置。",
                    NotificationType.ERROR),
                    project);
            return;
        }

        aiAnalysisButton.setEnabled(false);
        aiAnalysisButton.setText("分析中...");
        aiResponseArea.setText("正在收集代码并调用 AI 模型进行深度分析，请稍候...");

        new Task.Backgroundable(project, "代码调用链深度分析", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("正在进行深度分析...");
                indicator.setIndeterminate(true);

                String collectedCode = collectCodeFromCallStack(stackToAnalyze);
                String promptTemplate = IdeaSettings.getInstance().getState().getCallChainAnalysisPrompt();
                String prompt = promptTemplate.replace("%s", collectedCode);

                AiUtils.AiConfig config = new AiUtils.AiConfig(selectedProvider, selectedModel);
                if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        aiAnalysisButton.setEnabled(true);
                        aiAnalysisButton.setText("AI 深度分析");
                        aiResponseArea.setText("API密钥未配置");
                        Notifications.Bus.notify(new Notification(
                            "com.yt.huq.idea",
                            "API密钥未配置",
                            "请在设置中为 " + selectedProvider.getName() + " 配置API密钥",
                            NotificationType.WARNING),
                            project);
                    });
                    return;
                }

                config.setSystemMessage("你是一个专业的Java开发和架构审查专家。")
                      .setTemperature(0.7)
                      .setMaxTokens(8000);

                AiUtils.AiResponse response = AiUtils.callAi(prompt, config);

                SwingUtilities.invokeLater(() -> {
                    aiAnalysisButton.setEnabled(true);
                    aiAnalysisButton.setText("AI 深度分析");
                    if (response.isSuccess()) {
                        aiResponseArea.setText(response.getContent());
                        aiResponseArea.setCaretPosition(0);
                    } else {
                        aiResponseArea.setText("分析失败: " + response.getErrorMessage());
                        Notifications.Bus.notify(new Notification(
                                "com.yt.huq.idea",
                                "AI 分析失败",
                                response.getErrorMessage() != null ? response.getErrorMessage() : "未知错误",
                                NotificationType.ERROR),
                                project);
                    }
                });
            }
        }.queue();
    }

    private String collectCodeFromCallStack(CallStack callStack) {
        StringBuilder codeBuilder = new StringBuilder();
        appendMethodCode(codeBuilder, callStack);
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, 1);
        }
        return codeBuilder.toString();
    }

    private void collectCodeFromChildCallStack(StringBuilder codeBuilder, CallStack callStack, int depth) {
        if (depth > 10) return;
        if (!callStack.isRecursive()) {
            appendMethodCode(codeBuilder, callStack);
        }
        for (CallStack child : callStack.getChildren()) {
            collectCodeFromChildCallStack(codeBuilder, child, depth + 1);
        }
    }

    private void appendMethodCode(StringBuilder codeBuilder, CallStack callStack) {
        MethodDescription methodDesc = callStack.getMethodDescription();
        if (methodDesc == null || methodDesc.getText() == null || methodDesc.getText().isEmpty()) {
            return;
        }

        if (codeBuilder.indexOf(methodDesc.buildMethodId()) != -1) {
            return; // Already added
        }

        codeBuilder.append("\n\n// ").append("=".repeat(80)).append("\n");
        codeBuilder.append("// Class: ").append(methodDesc.getClassName()).append("\n");
        codeBuilder.append("// Method: ").append(methodDesc.getName()).append("\n");
        codeBuilder.append("// token: ").append(methodDesc.buildMethodId()).append("\n");
        codeBuilder.append(methodDesc.getText());
        codeBuilder.append("\n// ").append("=".repeat(80)).append("\n\n");
    }

    private JPanel createTreePanel(CallStack rootStack) {
        JPanel panel = new JPanel(new BorderLayout());

        DefaultMutableTreeNode rootNode = createTreeNode(rootStack);
        callTree = new JTree(rootNode);

        // Tree Selection Listener
        callTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) callTree.getLastSelectedPathComponent();
            if (selectedNode != null) {
                Object userObject = selectedNode.getUserObject();
                if (userObject instanceof CallStack) {
                    CallStack stack = (CallStack) userObject;
                    if (!stack.isMultiImplementationGroup() && stack.getMethodDescription() != null) {
                        codeTextArea.setText(stack.getMethodDescription().getText());
                        codeTextArea.setCaretPosition(0);
                    } else {
                        codeTextArea.setText("// 多实现并行分支，请选择具体的方法实现查看代码。");
                    }
                }
            }
        });

        // Custom Enhanced Cell Renderer
        callTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

                if (value instanceof DefaultMutableTreeNode) {
                    Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                    if (userObject instanceof CallStack) {
                        CallStack stack = (CallStack) userObject;

                        if (stack.isMultiImplementationGroup()) {
                            setText("<html><b><font color='blue'>Par (多实现并行分支)</font></b></html>");
                            setIcon(AllIcons.Nodes.Folder);
                        } else {
                            MethodDescription desc = stack.getMethodDescription();
                            if (desc != null) {
                                setIcon("true".equals(desc.getAttr("implementation")) ? AllIcons.Nodes.Interface : AllIcons.Nodes.Method);

                                String className = desc.getSimpleClassName();
                                String methodName = desc.getName();
                                String params = desc.getAttr("parameters");
                                String returnType = desc.getReturnType();

                                StringBuilder html = new StringBuilder("<html>");
                                html.append("<font color='gray'>").append(className).append(".</font>");
                                html.append("<b>").append(methodName).append("</b>");

                                if (params != null && !params.isEmpty()) {
                                    html.append("(<font color='#007A33'>").append(params).append("</font>)");
                                } else {
                                    html.append("()");
                                }

                                if (returnType != null && !returnType.equals("void")) {
                                    html.append(" : <font color='#A52A2A'>").append(returnType).append("</font>");
                                }

                                if (stack.isRecursive()) {
                                    html.append(" <font color='red'><b>[递归]</b></font>");
                                } else if ("true".equals(desc.getAttr("implementation"))) {
                                    html.append(" <font color='gray'><i>[实现]</i></font>");
                                }

                                // Attempt to add a short comment summary if available
                                String commentText = desc.getAttr("docCommentText");
                                if (commentText != null && !commentText.isEmpty()) {
                                    String comment = commentText.replaceAll("/\\*\\*|\\*/|\\*|\n|\r", "").trim();
                                    if (!comment.isEmpty()) {
                                        if (comment.length() > 30) comment = comment.substring(0, 27) + "...";
                                        html.append(" <font color='#808080'>// ").append(comment).append("</font>");
                                    }
                                }

                                html.append("</html>");
                                setText(html.toString());
                            }
                        }
                    }
                }
                return this;
            }
        });

        // Add some padding to the tree cells for better readability
        callTree.setRowHeight(24);
        panel.add(new JScrollPane(callTree), BorderLayout.CENTER);
        return panel;
    }

    private DefaultMutableTreeNode createTreeNode(CallStack stack) {
        if (stack == null) return new DefaultMutableTreeNode("Root");
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(stack);

        for (CallStack child : stack.getChildren()) {
            // Avoid deep recursion if it's already marked as recursive
            if (stack.isRecursive() && child.isRecursive()) {
                continue;
            }
            node.add(createTreeNode(child));
        }

        return node;
    }
}
