package com.huq.idea.flow.config.config;

import com.huq.idea.flow.util.AiUtils;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author huqiang
 * @since 2024/8/1 21:11
 */
public class AiConfigurationComponent {
    // 移除旧的form组件，改为程序化创建
    private JPanel mainPanel;
    private JTextField plantumlPathVal;
    private JTextArea flowPromptTextArea;
    private JTextArea relevantPatternsArea;
    private JTextArea excludedPatternsArea;
    
    // 多AI模型API密钥配置
    private Map<String, JTextField> aiApiKeyFields = new HashMap<>();
    private JPanel aiConfigPanel;
    private JPanel generalConfigPanel;
    private JPanel promptConfigPanel;
    private JPanel patternConfigPanel;

    public void init(IdeaSettings.State state) {
        // 创建所有UI组件
        createUIComponents();
        
        // 设置数据
        plantumlPathVal.setText(state.getPlantumlPathVal());
        flowPromptTextArea.setText(state.getBuildFlowPrompt());
        relevantPatternsArea.setText(String.join("\n", state.getRelevantClassPatterns()));
        excludedPatternsArea.setText(String.join("\n", state.getExcludedClassPatterns()));

        // 加载AI API密钥配置
        Map<String, String> aiApiKeys = state.getAiApiKeys();
        for (AiUtils.AiProvider provider : AiUtils.AiProvider.values()) {
            String providerName = provider.name();
            JTextField field = aiApiKeyFields.get(providerName);
            if (field != null) {
                String apiKey = state.getAiApiKey(providerName);
                field.setText(apiKey != null ? apiKey : "");
            }
        }
    }


    /**
     * 创建所有UI组件
     */
    private void createUIComponents() {
        // 创建主面板
        mainPanel = new JPanel(new BorderLayout());
        
        // 创建各个配置面板
        createGeneralConfigPanel();
        createAiConfigPanel();
        createPromptConfigPanel();
        createPatternConfigPanel();
        
        // 组装主面板
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(generalConfigPanel, BorderLayout.NORTH);
        topPanel.add(aiConfigPanel, BorderLayout.CENTER);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(promptConfigPanel, BorderLayout.NORTH);
        bottomPanel.add(patternConfigPanel, BorderLayout.CENTER);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(bottomPanel, BorderLayout.CENTER);
    }
    
    /**
     * 创建通用配置面板
     */
    private void createGeneralConfigPanel() {
        generalConfigPanel = new JPanel(new GridBagLayout());
        generalConfigPanel.setBorder(new TitledBorder("基础配置"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // PlantUML路径配置
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel plantumlLabel = new JLabel("PlantUML路径:");
        plantumlLabel.setPreferredSize(new Dimension(120, 25));
        generalConfigPanel.add(plantumlLabel, gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        plantumlPathVal = new JTextField(30);
        plantumlPathVal.setToolTipText("请输入PlantUML的安装路径");
        generalConfigPanel.add(plantumlPathVal, gbc);
    }
    
    /**
     * 创建AI配置面板
     */
    private void createAiConfigPanel() {
        aiConfigPanel = new JPanel(new GridBagLayout());
        aiConfigPanel.setBorder(new TitledBorder("AI模型API密钥配置"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // 添加说明文本
        JLabel instructionLabel = new JLabel("<html><body style='width: 500px'>" +
                "<b>配置说明：</b><br/>" +
                "• 请为您要使用的AI模型配置相应的API密钥<br/>" +
                "• 只需配置您实际使用的模型，未配置的模型将不可选择<br/>" +
                "• API密钥获取方式：<br/>" +
                "&nbsp;&nbsp;- DeepSeek: <a href='https://platform.deepseek.com'>https://platform.deepseek.com</a><br/>" +
                "&nbsp;&nbsp;- OpenAI: <a href='https://platform.openai.com'>https://platform.openai.com</a><br/>" +
                "&nbsp;&nbsp;- Anthropic: <a href='https://console.anthropic.com'>https://console.anthropic.com</a><br/>" +
                "&nbsp;&nbsp;- 月之暗面: <a href='https://platform.moonshot.cn'>https://platform.moonshot.cn</a><br/>" +
                "&nbsp;&nbsp;- 百度文心: <a href='https://console.bce.baidu.com'>https://console.bce.baidu.com</a><br/>" +
                "&nbsp;&nbsp;- 阿里通义: <a href='https://dashscope.console.aliyun.com'>https://dashscope.console.aliyun.com</a><br/>" +
                "&nbsp;&nbsp;- 智谱AI: <a href='https://open.bigmodel.cn'>https://open.bigmodel.cn</a>" +
                "</body></html>");
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        aiConfigPanel.add(instructionLabel, gbc);
        
        // 添加分隔线
        gbc.gridy++;
        gbc.insets = new Insets(10, 5, 10, 5);
        aiConfigPanel.add(new JSeparator(), gbc);
        
        // 为每个AI提供商创建输入框
        gbc.gridwidth = 1;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        for (AiUtils.AiProvider provider : AiUtils.AiProvider.values()) {
            gbc.gridy++;
            
            // 标签
            String displayName = getProviderDisplayName(provider);
            JLabel label = new JLabel(displayName + ":");
            label.setPreferredSize(new Dimension(120, 25));
            gbc.gridx = 0;
            gbc.fill = GridBagConstraints.NONE;
            aiConfigPanel.add(label, gbc);
            
            // 输入框
            JTextField textField = new JTextField(30);
            textField.setToolTipText("请输入" + displayName + "的API密钥");
            aiApiKeyFields.put(provider.name(), textField);
            
            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            aiConfigPanel.add(textField, gbc);
            gbc.weightx = 0;
        }
    }
    
    /**
     * 创建提示词配置面板
     */
    private void createPromptConfigPanel() {
        promptConfigPanel = new JPanel(new GridBagLayout());
        promptConfigPanel.setBorder(new TitledBorder("提示词配置"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // 流程图提示词配置
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel promptLabel = new JLabel("流程图生成提示词:");
        promptLabel.setPreferredSize(new Dimension(120, 25));
        promptConfigPanel.add(promptLabel, gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        flowPromptTextArea = new JTextArea(5, 30);
        flowPromptTextArea.setToolTipText("自定义AI生成流程图的提示词");
        flowPromptTextArea.setLineWrap(true);
        flowPromptTextArea.setWrapStyleWord(true);
        JScrollPane promptScrollPane = new JScrollPane(flowPromptTextArea);
        promptScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        promptConfigPanel.add(promptScrollPane, gbc);
    }
    
    /**
     * 创建模式配置面板
     */
    private void createPatternConfigPanel() {
        patternConfigPanel = new JPanel(new GridBagLayout());
        patternConfigPanel.setBorder(new TitledBorder("类匹配模式配置"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // 相关类模式
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel relevantLabel = new JLabel("相关类模式:");
        relevantLabel.setPreferredSize(new Dimension(120, 25));
        patternConfigPanel.add(relevantLabel, gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 0.5;
        relevantPatternsArea = new JTextArea(3, 30);
        relevantPatternsArea.setToolTipText("匹配相关类的正则表达式模式，每行一个");
        relevantPatternsArea.setLineWrap(true);
        relevantPatternsArea.setWrapStyleWord(true);
        JScrollPane relevantScrollPane = new JScrollPane(relevantPatternsArea);
        relevantScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        patternConfigPanel.add(relevantScrollPane, gbc);
        
        // 排除类模式
        gbc.gridy++;
        gbc.gridx = 0;
        JLabel excludedLabel = new JLabel("排除类模式:");
        excludedLabel.setPreferredSize(new Dimension(120, 25));
        patternConfigPanel.add(excludedLabel, gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 0.5;
        excludedPatternsArea = new JTextArea(3, 30);
        excludedPatternsArea.setToolTipText("排除类的正则表达式模式，每行一个");
        excludedPatternsArea.setLineWrap(true);
        excludedPatternsArea.setWrapStyleWord(true);
        JScrollPane excludedScrollPane = new JScrollPane(excludedPatternsArea);
        excludedScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        patternConfigPanel.add(excludedScrollPane, gbc);
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


    public List<String> getRelevantPatterns() {
        return Arrays.asList(relevantPatternsArea.getText().split("\n"));
    }

    public List<String> getExcludedPatterns() {
        return Arrays.asList(excludedPatternsArea.getText().split("\n"));
    }

    public JTextArea getFlowPromptTextArea() {
        return this.flowPromptTextArea;
    }


    /**
     * 获取主面板
     */
    public JPanel getjPanel() {
        return mainPanel;
    }
    
    /**
     * 获取主面板（新方法名）
     */
    public JPanel getMainPanel() {
        return mainPanel;
    }
    
    /**
     * 获取AI配置面板
     */
    public JPanel getAiConfigPanel() {
        return aiConfigPanel;
    }
    
    /**
     * 获取所有AI提供商的API密钥配置
     * @return API密钥配置Map
     */
    public Map<String, String> getAiApiKeys() {
        Map<String, String> apiKeys = new HashMap<>();
        for (Map.Entry<String, JTextField> entry : aiApiKeyFields.entrySet()) {
            String provider = entry.getKey();
            String apiKey = entry.getValue().getText();
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                apiKeys.put(provider, apiKey.trim());
            }
        }
        return apiKeys;
    }
    
    /**
     * 获取指定AI提供商的API密钥
     * @param provider AI提供商名称
     * @return API密钥
     */
    public String getAiApiKey(String provider) {
        JTextField field = aiApiKeyFields.get(provider);
        if (field != null) {
            String apiKey = field.getText();
            return apiKey != null ? apiKey.trim() : "";
        }
        return "";
    }

    public String getBuildFlowPrompt() {
        return this.flowPromptTextArea.getText();
    }


    public String getPlantumlPathValue() {
        return this.plantumlPathVal.getText();
    }
}
