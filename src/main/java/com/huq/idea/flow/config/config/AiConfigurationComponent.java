package com.huq.idea.flow.config.config;

import com.huq.idea.flow.util.AiUtils;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
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
    private DefaultListModel<String> promptListModel;
    private JList<String> promptList;
    private List<IdeaSettings.PromptConfig> promptConfigs;
    private int currentPromptIndex = -1;
    private JTextArea relevantPatternsArea;
    private JTextArea excludedPatternsArea;
    
    // 多AI模型API密钥配置
    private Map<String, JTextField> aiApiKeyFields = new HashMap<>();
    private JPanel aiConfigPanel;
    private JPanel generalConfigPanel;
    private JPanel promptConfigPanel;
    private JPanel patternConfigPanel;

    private DefaultListModel<String> aiProviderListModel;
    private JList<String> aiProviderList;
    private List<IdeaSettings.CustomAiProviderConfig> customAiProviders;
    private int currentAiProviderIndex = -1;
    private JTextField aiProviderNameField;
    private JTextField aiApiUrlField;
    private JTextField aiApiKeyField;
    private JTextField aiModelsField;

    public void init(IdeaSettings.State state) {
        // 深拷贝 promptConfigs
        promptConfigs = new ArrayList<>();
        if (state.getFlowPrompts() != null) {
            for (IdeaSettings.PromptConfig config : state.getFlowPrompts()) {
                promptConfigs.add(new IdeaSettings.PromptConfig(config.getName(), config.getPrompt()));
            }
        }

        customAiProviders = new ArrayList<>();
        if (state.getCustomAiProviders() != null) {
            for (IdeaSettings.CustomAiProviderConfig config : state.getCustomAiProviders()) {
                customAiProviders.add(new IdeaSettings.CustomAiProviderConfig(config.getName(), config.getApiUrl(), config.getApiKey(), config.getModels()));
            }
        }

        // 创建所有UI组件
        createUIComponents();
        
        // 设置数据
        plantumlPathVal.setText(state.getPlantumlPathVal());

        promptListModel.clear();
        for (IdeaSettings.PromptConfig config : promptConfigs) {
            promptListModel.addElement(config.getName());
        }

        if (!promptConfigs.isEmpty()) {
            promptList.setSelectedIndex(0);
        }

        relevantPatternsArea.setText(String.join("\n", state.getRelevantClassPatterns()));
        excludedPatternsArea.setText(String.join("\n", state.getExcludedClassPatterns()));

        aiProviderListModel.clear();
        for (IdeaSettings.CustomAiProviderConfig config : customAiProviders) {
            aiProviderListModel.addElement(config.getName());
        }

        if (!customAiProviders.isEmpty()) {
            aiProviderList.setSelectedIndex(0);
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
        JLabel plantumlLabel = new JLabel("PlantUML路径(P):");
        plantumlLabel.setDisplayedMnemonic('P');
        plantumlLabel.setPreferredSize(new Dimension(120, 25));
        generalConfigPanel.add(plantumlLabel, gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        plantumlPathVal = new JTextField(30);
        plantumlPathVal.setToolTipText("请输入PlantUML的安装路径 (Alt+P)");
        plantumlLabel.setLabelFor(plantumlPathVal);
        generalConfigPanel.add(plantumlPathVal, gbc);
    }
    
    /**
     * 创建AI配置面板
     */
    private void createAiConfigPanel() {
        aiConfigPanel = new JPanel(new BorderLayout());
        aiConfigPanel.setBorder(new TitledBorder("AI提供商及模型配置 (OpenAI 兼容)"));

        // Left Panel: List of providers
        JPanel leftPanel = new JPanel(new BorderLayout());
        aiProviderListModel = new DefaultListModel<>();
        aiProviderList = new JList<>(aiProviderListModel);
        aiProviderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScrollPane = new JScrollPane(aiProviderList);
        listScrollPane.setPreferredSize(new Dimension(150, 0));
        leftPanel.add(listScrollPane, BorderLayout.CENTER);

        // Buttons for provider list
        JPanel listButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        JButton addButton = new JButton("添加");
        JButton removeButton = new JButton("删除");
        listButtonsPanel.add(addButton);
        listButtonsPanel.add(removeButton);
        leftPanel.add(listButtonsPanel, BorderLayout.SOUTH);

        // Right Panel: Form for selected provider
        JPanel rightPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        JLabel nameLabel = new JLabel("名称(N):");
        nameLabel.setDisplayedMnemonic('N');
        rightPanel.add(nameLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        aiProviderNameField = new JTextField(30);
        aiProviderNameField.setToolTipText("AI提供商名称 (Alt+N)");
        nameLabel.setLabelFor(aiProviderNameField);
        rightPanel.add(aiProviderNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        JLabel urlLabel = new JLabel("域名代理 (URL)(U):");
        urlLabel.setDisplayedMnemonic('U');
        rightPanel.add(urlLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        aiApiUrlField = new JTextField(30);
        aiApiUrlField.setToolTipText("AI API接口地址 (Alt+U)");
        urlLabel.setLabelFor(aiApiUrlField);
        rightPanel.add(aiApiUrlField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        JLabel keyLabel = new JLabel("API Key(K):");
        keyLabel.setDisplayedMnemonic('K');
        rightPanel.add(keyLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        aiApiKeyField = new JTextField(30);
        aiApiKeyField.setToolTipText("AI API密钥 (Alt+K)");
        keyLabel.setLabelFor(aiApiKeyField);
        rightPanel.add(aiApiKeyField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        JLabel modelsLabel = new JLabel("可用模型(M) (逗号分隔):");
        modelsLabel.setDisplayedMnemonic('M');
        rightPanel.add(modelsLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        aiModelsField = new JTextField(30);
        aiModelsField.setToolTipText("支持的模型列表，用逗号分隔 (Alt+M)");
        modelsLabel.setLabelFor(aiModelsField);
        rightPanel.add(aiModelsField, gbc);

        // Spacer
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        rightPanel.add(new JPanel(), gbc);

        // Create Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(200);
        aiConfigPanel.add(splitPane, BorderLayout.CENTER);

        boolean[] isUpdatingUI = {false};

        // Setup listeners to save data when moving away
        Runnable saveCurrentProvider = () -> {
            if (isUpdatingUI[0]) return;
            if (currentAiProviderIndex >= 0 && currentAiProviderIndex < customAiProviders.size()) {
                IdeaSettings.CustomAiProviderConfig config = customAiProviders.get(currentAiProviderIndex);
                config.setName(aiProviderNameField.getText().trim());
                config.setApiUrl(aiApiUrlField.getText().trim());
                config.setApiKey(aiApiKeyField.getText().trim());
                config.setModels(aiModelsField.getText().trim());
                if (!aiProviderListModel.get(currentAiProviderIndex).equals(config.getName())) {
                    isUpdatingUI[0] = true;
                    aiProviderListModel.set(currentAiProviderIndex, config.getName());
                    isUpdatingUI[0] = false;
                }
            }
        };

        // Add document listeners to update list model name on the fly if needed
        aiProviderNameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { saveCurrentProvider.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { saveCurrentProvider.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { saveCurrentProvider.run(); }
        });
        aiApiUrlField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { saveCurrentProvider.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { saveCurrentProvider.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { saveCurrentProvider.run(); }
        });
        aiApiKeyField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { saveCurrentProvider.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { saveCurrentProvider.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { saveCurrentProvider.run(); }
        });
        aiModelsField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { saveCurrentProvider.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { saveCurrentProvider.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { saveCurrentProvider.run(); }
        });


        aiProviderList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !isUpdatingUI[0]) {
                saveCurrentProvider.run(); // Ensure current changes are saved before switching

                currentAiProviderIndex = aiProviderList.getSelectedIndex();
                isUpdatingUI[0] = true;
                if (currentAiProviderIndex >= 0 && currentAiProviderIndex < customAiProviders.size()) {
                    IdeaSettings.CustomAiProviderConfig config = customAiProviders.get(currentAiProviderIndex);
                    aiProviderNameField.setText(config.getName());
                    aiApiUrlField.setText(config.getApiUrl());
                    aiApiKeyField.setText(config.getApiKey());
                    aiModelsField.setText(config.getModels());
                    setProviderFieldsEnabled(true);
                } else {
                    aiProviderNameField.setText("");
                    aiApiUrlField.setText("");
                    aiApiKeyField.setText("");
                    aiModelsField.setText("");
                    setProviderFieldsEnabled(false);
                }
                isUpdatingUI[0] = false;
            }
        });

        addButton.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(mainPanel, "请输入新提供商名称:", "添加提供商", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                IdeaSettings.CustomAiProviderConfig newConfig = new IdeaSettings.CustomAiProviderConfig(
                        name.trim(), "https://api.openai.com/v1/chat/completions", "", "gpt-3.5-turbo");
                customAiProviders.add(newConfig);
                aiProviderListModel.addElement(newConfig.getName());
                aiProviderList.setSelectedIndex(aiProviderListModel.size() - 1);
            }
        });

        removeButton.addActionListener(e -> {
            int selectedIndex = aiProviderList.getSelectedIndex();
            if (selectedIndex >= 0) {
                if (customAiProviders.size() <= 1) {
                    JOptionPane.showMessageDialog(mainPanel, "必须保留至少一个提供商配置。", "无法删除", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                currentAiProviderIndex = -1; // Prevent saving to the deleted index
                customAiProviders.remove(selectedIndex);
                aiProviderListModel.remove(selectedIndex);
                if (selectedIndex >= aiProviderListModel.size()) {
                    aiProviderList.setSelectedIndex(aiProviderListModel.size() - 1);
                } else {
                    aiProviderList.setSelectedIndex(selectedIndex);
                }
            }
        });
    }

    private void setProviderFieldsEnabled(boolean enabled) {
        aiProviderNameField.setEnabled(enabled);
        aiApiUrlField.setEnabled(enabled);
        aiApiKeyField.setEnabled(enabled);
        aiModelsField.setEnabled(enabled);
    }
    
    /**
     * 创建提示词配置面板
     */
    private void createPromptConfigPanel() {
        promptConfigPanel = new JPanel(new BorderLayout());
        promptConfigPanel.setBorder(new TitledBorder("提示词配置"));

        // Left Panel: List of prompts
        JPanel leftPanel = new JPanel(new BorderLayout());
        promptListModel = new DefaultListModel<>();
        promptList = new JList<>(promptListModel);
        promptList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScrollPane = new JScrollPane(promptList);
        listScrollPane.setPreferredSize(new Dimension(150, 0));
        leftPanel.add(listScrollPane, BorderLayout.CENTER);

        // Buttons for list
        JPanel listButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        JButton addButton = new JButton("添加");
        JButton removeButton = new JButton("删除");
        JButton renameButton = new JButton("重命名");

        listButtonsPanel.add(addButton);
        listButtonsPanel.add(removeButton);
        listButtonsPanel.add(renameButton);
        leftPanel.add(listButtonsPanel, BorderLayout.SOUTH);

        // Right Panel: Text area for selected prompt
        JPanel rightPanel = new JPanel(new BorderLayout());
        flowPromptTextArea = new JTextArea(5, 30);
        flowPromptTextArea.setToolTipText("自定义AI生成流程图的提示词 (Alt+C)");
        flowPromptTextArea.setLineWrap(true);
        flowPromptTextArea.setWrapStyleWord(true);
        JScrollPane promptScrollPane = new JScrollPane(flowPromptTextArea);
        promptScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        JLabel promptContentLabel = new JLabel("提示词内容(C):");
        promptContentLabel.setDisplayedMnemonic('C');
        promptContentLabel.setLabelFor(flowPromptTextArea);
        rightPanel.add(promptContentLabel, BorderLayout.NORTH);
        rightPanel.add(promptScrollPane, BorderLayout.CENTER);

        // Create Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(200);
        promptConfigPanel.add(splitPane, BorderLayout.CENTER);

        // Event Listeners
        promptList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    // Save previous prompt if we were editing one
                    if (currentPromptIndex >= 0 && currentPromptIndex < promptConfigs.size()) {
                        promptConfigs.get(currentPromptIndex).setPrompt(flowPromptTextArea.getText());
                    }

                    currentPromptIndex = promptList.getSelectedIndex();
                    if (currentPromptIndex >= 0 && currentPromptIndex < promptConfigs.size()) {
                        flowPromptTextArea.setText(promptConfigs.get(currentPromptIndex).getPrompt());
                        flowPromptTextArea.setEnabled(true);
                    } else {
                        flowPromptTextArea.setText("");
                        flowPromptTextArea.setEnabled(false);
                    }
                }
            }
        });

        addButton.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(mainPanel, "请输入新提示词名称:", "添加提示词", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                IdeaSettings.PromptConfig newConfig = new IdeaSettings.PromptConfig(name.trim(), IdeaSettings.DEFAULT_BUILD_FLOW_PROMPT);
                promptConfigs.add(newConfig);
                promptListModel.addElement(newConfig.getName());
                promptList.setSelectedIndex(promptListModel.size() - 1);
            }
        });

        removeButton.addActionListener(e -> {
            int selectedIndex = promptList.getSelectedIndex();
            if (selectedIndex >= 0) {
                // If we are deleting the last item, ensure there is at least one
                if (promptConfigs.size() <= 1) {
                    JOptionPane.showMessageDialog(mainPanel, "必须保留至少一个提示词配置。", "无法删除", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                currentPromptIndex = -1; // Prevent saving to the deleted index
                promptConfigs.remove(selectedIndex);
                promptListModel.remove(selectedIndex);
                if (selectedIndex >= promptListModel.size()) {
                    promptList.setSelectedIndex(promptListModel.size() - 1);
                } else {
                    promptList.setSelectedIndex(selectedIndex);
                }
            }
        });

        renameButton.addActionListener(e -> {
            int selectedIndex = promptList.getSelectedIndex();
            if (selectedIndex >= 0) {
                String oldName = promptConfigs.get(selectedIndex).getName();
                String newName = JOptionPane.showInputDialog(mainPanel, "请输入新名称:", oldName);
                if (newName != null && !newName.trim().isEmpty()) {
                    promptConfigs.get(selectedIndex).setName(newName.trim());
                    promptListModel.set(selectedIndex, newName.trim());
                }
            }
        });
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
        JLabel relevantLabel = new JLabel("相关类模式(R):");
        relevantLabel.setDisplayedMnemonic('R');
        relevantLabel.setPreferredSize(new Dimension(120, 25));
        patternConfigPanel.add(relevantLabel, gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 0.5;
        relevantPatternsArea = new JTextArea(3, 30);
        relevantPatternsArea.setToolTipText("匹配相关类的正则表达式模式，每行一个 (Alt+R)");
        relevantLabel.setLabelFor(relevantPatternsArea);
        relevantPatternsArea.setLineWrap(true);
        relevantPatternsArea.setWrapStyleWord(true);
        JScrollPane relevantScrollPane = new JScrollPane(relevantPatternsArea);
        relevantScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        patternConfigPanel.add(relevantScrollPane, gbc);
        
        // 排除类模式
        gbc.gridy++;
        gbc.gridx = 0;
        JLabel excludedLabel = new JLabel("排除类模式(E):");
        excludedLabel.setDisplayedMnemonic('E');
        excludedLabel.setPreferredSize(new Dimension(120, 25));
        patternConfigPanel.add(excludedLabel, gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 0.5;
        excludedPatternsArea = new JTextArea(3, 30);
        excludedPatternsArea.setToolTipText("排除类的正则表达式模式，每行一个 (Alt+E)");
        excludedLabel.setLabelFor(excludedPatternsArea);
        excludedPatternsArea.setLineWrap(true);
        excludedPatternsArea.setWrapStyleWord(true);
        JScrollPane excludedScrollPane = new JScrollPane(excludedPatternsArea);
        excludedScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        patternConfigPanel.add(excludedScrollPane, gbc);
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
    
    public Map<String, String> getAiApiKeys() {
        return new HashMap<>(); // Deprecated/Not used anymore for custom ones, but kept to satisfy IdeaConfigurable compilation
    }
    
    public List<IdeaSettings.CustomAiProviderConfig> getCustomAiProviders() {
        if (currentAiProviderIndex >= 0 && currentAiProviderIndex < customAiProviders.size()) {
            IdeaSettings.CustomAiProviderConfig config = customAiProviders.get(currentAiProviderIndex);
            config.setName(aiProviderNameField.getText().trim());
            config.setApiUrl(aiApiUrlField.getText().trim());
            config.setApiKey(aiApiKeyField.getText().trim());
            config.setModels(aiModelsField.getText().trim());
        }
        return customAiProviders;
    }

    public String getBuildFlowPrompt() {
        return this.flowPromptTextArea.getText();
    }

    public List<IdeaSettings.PromptConfig> getFlowPrompts() {
        // Ensure the current edited text is saved to the active prompt config
        if (currentPromptIndex >= 0 && currentPromptIndex < promptConfigs.size()) {
            promptConfigs.get(currentPromptIndex).setPrompt(flowPromptTextArea.getText());
        }
        return promptConfigs;
    }


    public String getPlantumlPathValue() {
        return this.plantumlPathVal.getText();
    }
}
