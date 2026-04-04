package com.huq.idea.flow.apidoc.ui;

import com.huq.idea.flow.apidoc.service.UmlFlowService;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.huq.idea.flow.util.AiUtils;
import com.huq.idea.flow.util.PlantUmlRenderException;
import com.huq.idea.flow.util.PlantUmlRenderer;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Unified UI factory for UML Diagram generation actions
 */
public class UmlDiagramUIFactory {
    private static final Logger LOG = Logger.getInstance(UmlDiagramUIFactory.class);

    public interface PromptProvider {
        String getPrompt(String collectedCode);
        default JComboBox<IdeaSettings.PromptConfig> getPromptComboBox() {
            return null;
        }
    }

    public static void showInitialDialog(Project project, String collectedCode, String title, PromptProvider promptProvider, String generateButtonText) {
        JTabbedPane tabbedPane = new JTabbedPane();

        JPanel plantUmlTab = createInitialPlantUmlTab(project, title, collectedCode, promptProvider, generateButtonText);
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

    private static JPanel createInitialPlantUmlTab(Project project, String title, String collectedCode, PromptProvider promptProvider, String generateButtonText) {
        JPanel panel = new JPanel(new BorderLayout());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(500);
        splitPane.setResizeWeight(0.5);

        // ==== REDESIGNED LEFT PANE ====
        JTabbedPane leftCodeTabbedPane = new JTabbedPane();

        // 1. Source Code Tab (Read-only view of collectedCode)
        JPanel sourceCodePanel = new JPanel(new BorderLayout());
        JTextArea sourceTextArea = new JTextArea();
        sourceTextArea.setEditable(false);
        sourceTextArea.setText(collectedCode);
        sourceTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        sourceCodePanel.add(new JScrollPane(sourceTextArea), BorderLayout.CENTER);
        leftCodeTabbedPane.addTab("源码视图", sourceCodePanel);

        // 2. PlantUML Code Tab (Editable view for generated UML code)
        JPanel umlCodePanel = new JPanel(new BorderLayout());
        JTextArea umlTextArea = new JTextArea();
        umlTextArea.setEditable(true);
        umlTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        umlCodePanel.add(new JScrollPane(umlTextArea), BorderLayout.CENTER);
        leftCodeTabbedPane.addTab("PlantUML代码", umlCodePanel);


        // Right pane (Diagram)
        JPanel diagramPanel = new JPanel(new BorderLayout());
        JLabel waitingLabel = new JLabel("点击\"" + generateButtonText + "\"按钮生成图表", SwingConstants.CENTER);
        waitingLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        diagramPanel.add(waitingLabel, BorderLayout.CENTER);

        splitPane.setLeftComponent(leftCodeTabbedPane);
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

        JComboBox<IdeaSettings.PromptConfig> promptComboBox = promptProvider.getPromptComboBox();
        if (promptComboBox != null) {
            JLabel promptLabel = new JLabel("提示词:");
            buttonPanel.add(promptLabel);
            buttonPanel.add(promptComboBox);
        }

        JButton generateButton = new JButton(generateButtonText);
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
                generateButton.setText(generateButtonText);
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
                generateButton.setText(generateButtonText);
                return;
            }

            new Task.Backgroundable(project, "生成图表", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("正在生成PlantUML图表...");
                    indicator.setIndeterminate(true);

                    String flowPrompt = promptProvider.getPrompt(collectedCode);

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

                    config.setSystemMessage("你是一个专业的PlantUML图表生成专家，擅长分析Java代码并生成高质量的图表。")
                          .setTemperature(0.7)
                          .setMaxTokens(8000);

                    AiUtils.AiResponse response = AiUtils.callAi(flowPrompt, config);
                    String diagram = response.isSuccess() ? response.getContent() : null;

                    if (diagram != null && !diagram.isEmpty()) {
                        diagram = cleanupUmlResponse(diagram);
                        String finalDiagram = diagram;

                        SwingUtilities.invokeLater(() -> {
                            umlTextArea.setText(finalDiagram);
                            leftCodeTabbedPane.setSelectedIndex(1); // Auto switch to UML Code tab
                            JPanel newDiagramPanel = PlantUmlRenderer.createPlantUmlPanel(finalDiagram);
                            splitPane.setRightComponent(newDiagramPanel);

                            generateButton.setEnabled(true);
                            generateButton.setText("重新生成");

                            panel.revalidate();
                            panel.repaint();
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            String errorMsg = "生成图表失败，请检查API设置和网络连接";
                            if (response != null && !response.isSuccess() && response.getErrorMessage() != null) {
                                errorMsg = "生成图表失败: " + response.getErrorMessage();
                            }

                            Notifications.Bus.notify(new Notification(
                                    "com.yt.huq.idea",
                                    "图表生成",
                                    errorMsg,
                                    NotificationType.ERROR),
                                    project);

                            generateButton.setEnabled(true);
                            generateButton.setText(generateButtonText);
                        });
                    }
                }
            }.queue();
        });
        buttonPanel.add(generateButton);

        JButton copyButton = new JButton("复制到剪贴板");
        copyButton.addActionListener(e -> {
            copyToClipboard(umlTextArea.getText());
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
                    writer.write(umlTextArea.getText());
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
            if (umlTextArea.getText().trim().isEmpty() || !umlTextArea.getText().contains("@startuml")) {
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
                    byte[] pngData = PlantUmlRenderer.renderPlantUmlToPng(umlTextArea.getText());
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

        JButton refreshButton = new JButton("刷新图像");
        refreshButton.addActionListener(e -> {
            String updatedUmlContent = umlTextArea.getText();
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

    private static void copyToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    private static String cleanupUmlResponse(String umlResponse) {
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
