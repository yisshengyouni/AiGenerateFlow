package com.huq.idea.flow.apidoc.window;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author huqiang
 * @since 2025/5/16 18:02
 */
public class UmlFlowToolWindow implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        // 设置工具窗口图标
        toolWindow.setIcon(IconLoader.getIcon("/icons/pluginIcon_13.png", getClass()));

        // 创建欢迎页面
        JPanel welcomePanel = createWelcomePanel();
        
        ContentManager contentManager = toolWindow.getContentManager();
        Content welcomeContent = contentManager.getFactory().createContent(welcomePanel, "欢迎", false);
        welcomeContent.setCloseable(false);
        contentManager.addContent(welcomeContent);
    }
    
    /**
     * 创建欢迎页面
     */
    private JPanel createWelcomePanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(JBUI.insets(20)));
        
        // 创建标题区域
        JPanel headerPanel = createHeaderPanel();
        
        // 创建内容区域
        JPanel contentPanel = createContentPanel();
        
        // 创建底部按钮区域
        JPanel footerPanel = createFooterPanel();
        
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(new JBScrollPane(contentPanel), BorderLayout.CENTER);
        mainPanel.add(footerPanel, BorderLayout.SOUTH);
        
        return mainPanel;
    }
    
    /**
     * 创建标题区域
     */
    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(new EmptyBorder(JBUI.insets(0, 0, 20, 0)));
        
        // 插件图标
        JLabel iconLabel = new JLabel(IconLoader.getIcon("/icons/pluginIcon_13.png", getClass()));
        
        // 标题和副标题
        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 5));
        JLabel titleLabel = new JLabel("<html><h1>AI UML 流程图生成器</h1></html>");
        JLabel subtitleLabel = new JLabel("<html><i>使用AI技术从Java代码自动生成UML流程图</i></html>");
        subtitleLabel.setForeground(Color.GRAY);
        
        titlePanel.add(titleLabel);
        titlePanel.add(subtitleLabel);
        
        headerPanel.add(iconLabel, BorderLayout.WEST);
        headerPanel.add(titlePanel, BorderLayout.CENTER);
        
        return headerPanel;
    }
    
    /**
     * 创建内容区域
     */
    private JPanel createContentPanel() {
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        
        // 功能介绍
        contentPanel.add(createFeatureSection());
        contentPanel.add(Box.createVerticalStrut(20));
        
        // 使用指南
        contentPanel.add(createUsageSection());
        contentPanel.add(Box.createVerticalStrut(20));
        
        // 配置说明
        contentPanel.add(createConfigSection());
        
        return contentPanel;
    }
    
    /**
     * 创建功能介绍区域
     */
    private JPanel createFeatureSection() {
        JPanel featurePanel = new JPanel(new BorderLayout());
        featurePanel.setBorder(BorderFactory.createTitledBorder("✨ 主要功能"));
        
        JTextArea featureText = new JTextArea();
        featureText.setEditable(false);
        featureText.setOpaque(false);
        featureText.setText(
            "🎯 智能流程图生成\n" +
            "   • 从Java方法代码自动生成UML活动图\n" +
            "   • 支持复杂逻辑结构的可视化\n" +
            "   • 智能识别条件分支和循环结构\n\n" +
            "🤖 多AI模型支持\n" +
            "   • DeepSeek、OpenAI、Anthropic等主流AI模型\n" +
            "   • 智能选择最适合的模型进行代码分析\n" +
            "   • 支持自定义提示词优化生成效果\n\n" +
            "📊 可视化展示\n" +
            "   • 在IDE中直接查看生成的流程图\n" +
            "   • 支持PlantUML格式导出\n" +
            "   • 可保存为图片或代码文件"
        );
        
        featurePanel.add(featureText, BorderLayout.CENTER);
        return featurePanel;
    }
    
    /**
     * 创建使用指南区域
     */
    private JPanel createUsageSection() {
        JPanel usagePanel = new JPanel(new BorderLayout());
        usagePanel.setBorder(BorderFactory.createTitledBorder("📖 使用指南"));
        
        JTextArea usageText = new JTextArea();
        usageText.setEditable(false);
        usageText.setOpaque(false);
        usageText.setText(
            "1️⃣ 配置AI模型\n" +
            "   • 打开 Settings > Tools > UmlFlowAiConfigurable\n" +
            "   • 配置您要使用的AI模型API密钥\n" +
            "   • 可选择配置PlantUML路径以支持图片导出\n\n" +
            "2️⃣ 生成流程图\n" +
            "   • 在Java编辑器中将光标放置在方法内\n" +
            "   • 右键选择 Generate > Generate UML Flow Diagram\n" +
            "   • 或使用快捷键 Alt+Insert 打开生成菜单\n\n" +
            "3️⃣ 查看和导出\n" +
            "   • 在弹出窗口中查看生成的流程图\n" +
            "   • 可编辑PlantUML代码进行自定义调整\n" +
            "   • 支持保存为.puml文件或导出为图片"
        );
        
        usagePanel.add(usageText, BorderLayout.CENTER);
        return usagePanel;
    }
    
    /**
     * 创建配置说明区域
     */
    private JPanel createConfigSection() {
        JPanel configPanel = new JPanel(new BorderLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("⚙️ 配置说明"));
        
        JTextArea configText = new JTextArea();
        configText.setEditable(false);
        configText.setOpaque(false);
        configText.setText(
            "🔑 API密钥配置\n" +
            "   • 支持多个AI提供商，只需配置您要使用的模型\n" +
            "   • 未配置API密钥的模型将不可选择\n" +
            "   • 建议至少配置一个主要使用的AI模型\n\n" +
            "🎨 自定义设置\n" +
            "   • 可自定义流程图生成的提示词\n" +
            "   • 支持配置类扫描的包含/排除模式\n" +
            "   • PlantUML路径配置用于本地图片生成\n\n" +
            "💡 使用建议\n" +
            "   • 建议在较小的方法上使用以获得更好的效果\n" +
            "   • 复杂方法可能需要手动调整生成的流程图\n" +
            "   • 定期更新API密钥以确保服务可用性"
        );
        
        configPanel.add(configText, BorderLayout.CENTER);
        return configPanel;
    }
    
    /**
     * 创建底部按钮区域
     */
    private JPanel createFooterPanel() {
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footerPanel.setBorder(new EmptyBorder(JBUI.insets(20, 0, 0, 0)));
        
        JButton configButton = new JButton("打开配置");
        configButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 打开配置页面
                com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                    .showSettingsDialog(null, "UmlFlowAiConfigurable");
            }
        });
        
        JButton helpButton = new JButton("帮助文档");
        helpButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 可以添加打开帮助文档的逻辑
                JOptionPane.showMessageDialog(null, 
                    "更多帮助信息请查看插件说明或联系开发者\n" +
                    "Email: yifengkuaijian@gmail.com", 
                    "帮助信息", 
                    JOptionPane.INFORMATION_MESSAGE);
            }
        });
        
        footerPanel.add(configButton);
        footerPanel.add(Box.createHorizontalStrut(10));
        footerPanel.add(helpButton);
        
        return footerPanel;
    }
}
