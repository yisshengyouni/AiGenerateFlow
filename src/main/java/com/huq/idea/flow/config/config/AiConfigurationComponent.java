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
    private JComboBox<String> diagramTypeComboBox;
    private List<IdeaSettings.PromptConfig> flowPromptConfigs;
    private List<IdeaSettings.PromptConfig> classPromptConfigs;
    private List<IdeaSettings.PromptConfig> sequencePromptConfigs;
    private List<IdeaSettings.PromptConfig> statePromptConfigs;
    private List<IdeaSettings.PromptConfig> explainPromptConfigs;
    private List<IdeaSettings.PromptConfig> reviewPromptConfigs;
    private List<IdeaSettings.PromptConfig> testPromptConfigs;

    private int currentPromptIndex = -1;
    private int currentDiagramTypeIndex = 0;
    private JTextArea relevantPatternsArea;
    private JTextArea excludedPatternsArea;
    private JTextArea classRelevantPatternsArea;
    private JTextArea classExcludedPatternsArea;
    private JSpinner classDiagramDepthSpinner;
    private JCheckBox includeLibrarySourcesCheckBox;
    
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
        // 深拷贝 flowPromptConfigs
        flowPromptConfigs = new ArrayList<>();
        if (state.getFlowPrompts() != null) {
            for (IdeaSettings.PromptConfig config : state.getFlowPrompts()) {
                flowPromptConfigs.add(new IdeaSettings.PromptConfig(config.getName(), config.getPrompt()));
            }
        }

        classPromptConfigs = new ArrayList<>();
        if (state.getClassPrompts() != null) {
            for (IdeaSettings.PromptConfig config : state.getClassPrompts()) {
                classPromptConfigs.add(new IdeaSettings.PromptConfig(config.getName(), config.getPrompt()));
            }
        }

        sequencePromptConfigs = new ArrayList<>();
        if (state.getSequencePrompts() != null) {
            for (IdeaSettings.PromptConfig config : state.getSequencePrompts()) {
                sequencePromptConfigs.add(new IdeaSettings.PromptConfig(config.getName(), config.getPrompt()));
            }
        }

        statePromptConfigs = new ArrayList<>();
        if (state.getStatePrompts() != null) {
            for (IdeaSettings.PromptConfig config : state.getStatePrompts()) {
                statePromptConfigs.add(new IdeaSettings.PromptConfig(config.getName(), config.getPrompt()));
            }
        }

        explainPromptConfigs = new ArrayList<>();
        if (state.getExplainPrompts() != null) {
            for (IdeaSettings.PromptConfig config : state.getExplainPrompts()) {
                explainPromptConfigs.add(new IdeaSettings.PromptConfig(config.getName(), config.getPrompt()));
            }
        }

        reviewPromptConfigs = new ArrayList<>();
        if (state.getReviewPrompts() != null) {
            for (IdeaSettings.PromptConfig config : state.getReviewPrompts()) {
                reviewPromptConfigs.add(new IdeaSettings.PromptConfig(config.getName(), config.getPrompt()));
            }
        }

        testPromptConfigs = new ArrayList<>();
        if (state.getTestPrompts() != null) {
            for (IdeaSettings.PromptConfig config : state.getTestPrompts()) {
                testPromptConfigs.add(new IdeaSettings.PromptConfig(config.getName(), config.getPrompt()));
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

        // Initialize the prompt list for the default selected tab (index 0)
        currentDiagramTypeIndex = diagramTypeComboBox.getSelectedIndex();
        populatePromptListForActiveTab();

        relevantPatternsArea.setText(String.join("\n", state.getRelevantClassPatterns()));
        excludedPatternsArea.setText(String.join("\n", state.getExcludedClassPatterns()));

        classRelevantPatternsArea.setText(String.join("\n", state.getClassRelevantClassPatterns()));
        classExcludedPatternsArea.setText(String.join("\n", state.getClassExcludedClassPatterns()));
        classDiagramDepthSpinner.setValue(state.getClassDiagramDepth());
        includeLibrarySourcesCheckBox.setSelected(state.isIncludeLibrarySources());

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
        
        // 组装主面板，使用TabbedPane以提升UI可用性
        com.intellij.ui.components.JBTabbedPane tabbedPane = new com.intellij.ui.components.JBTabbedPane();

        // Tab 1: 基础与AI配置
        JPanel generalAndAiPanel = new JPanel(new BorderLayout());
        generalAndAiPanel.add(generalConfigPanel, BorderLayout.NORTH);
        generalAndAiPanel.add(aiConfigPanel, BorderLayout.CENTER);
        generalAndAiPanel.setBorder(com.intellij.util.ui.JBUI.Borders.empty(5));
        tabbedPane.addTab("AI 模型配置", generalAndAiPanel);
        
        // Tab 2: 提示词配置
        JPanel promptPanel = new JPanel(new BorderLayout());
        promptPanel.add(promptConfigPanel, BorderLayout.CENTER);
        promptPanel.setBorder(com.intellij.util.ui.JBUI.Borders.empty(5));
        tabbedPane.addTab("提示词", promptPanel);
        
        // Tab 3: 匹配模式与规则
        JPanel filterPanel = new JPanel(new BorderLayout());
        filterPanel.add(patternConfigPanel, BorderLayout.CENTER);
        filterPanel.setBorder(com.intellij.util.ui.JBUI.Borders.empty(5));
        tabbedPane.addTab("过滤与扫描规则", filterPanel);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
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
        aiProviderNameField = new JTextField();
        aiApiUrlField = new JTextField();
        aiApiKeyField = new JPasswordField(); // Ensure security
        aiModelsField = new JTextField();

        JLabel nameLabel = new JLabel("名称 (&N):");
        nameLabel.setDisplayedMnemonic('N');
        nameLabel.setLabelFor(aiProviderNameField);

        JLabel urlLabel = new JLabel("域名代理 URL (&U):");
        urlLabel.setDisplayedMnemonic('U');
        urlLabel.setLabelFor(aiApiUrlField);

        JLabel keyLabel = new JLabel("API Key (&K):");
        keyLabel.setDisplayedMnemonic('K');
        keyLabel.setLabelFor(aiApiKeyField);

        JLabel modelsLabel = new JLabel("可用模型 (&M):");
        modelsLabel.setDisplayedMnemonic('M');
        modelsLabel.setLabelFor(aiModelsField);
        aiModelsField.setToolTipText("输入模型名称，多个模型使用英文逗号分隔");

        JPanel rightPanel = com.intellij.util.ui.FormBuilder.createFormBuilder()
                .addLabeledComponent(nameLabel, aiProviderNameField)
                .addLabeledComponent(urlLabel, aiApiUrlField)
                .addLabeledComponent(keyLabel, aiApiKeyField)
                .addLabeledComponent(modelsLabel, aiModelsField)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        rightPanel.setBorder(com.intellij.util.ui.JBUI.Borders.empty(10));

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

        // Top Panel: Diagram Type Selector
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("功能类型: "));
        diagramTypeComboBox = new JComboBox<>(new String[]{"流程图 (Flow Diagram)", "类图 (Class Diagram)", "时序图 (Sequence Diagram)", "状态图 (State Diagram)", "解释代码 (Explain Code)", "审查代码 (Review Code)", "生成测试 (Generate Unit Test)"});
        topPanel.add(diagramTypeComboBox);
        promptConfigPanel.add(topPanel, BorderLayout.NORTH);

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
        flowPromptTextArea.setToolTipText("自定义AI生成流程图/类图/时序图的提示词");
        flowPromptTextArea.setLineWrap(true);
        flowPromptTextArea.setWrapStyleWord(true);
        JScrollPane promptScrollPane = new JScrollPane(flowPromptTextArea);
        promptScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        rightPanel.add(new JLabel("提示词内容:"), BorderLayout.NORTH);
        rightPanel.add(promptScrollPane, BorderLayout.CENTER);

        // Create Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(200);
        promptConfigPanel.add(splitPane, BorderLayout.CENTER);

        // Event Listeners
        diagramTypeComboBox.addActionListener(e -> {
            int newTypeIndex = diagramTypeComboBox.getSelectedIndex();
            if (newTypeIndex == currentDiagramTypeIndex) {
                return;
            }

            // Save current before switching
            saveCurrentPrompt();

            // Update the tracked type index to the new one
            currentDiagramTypeIndex = newTypeIndex;

            populatePromptListForActiveTab();
        });

        promptList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    // Save previous prompt if we were editing one
                    saveCurrentPrompt();

                    currentPromptIndex = promptList.getSelectedIndex();
                    List<IdeaSettings.PromptConfig> activeConfigs = getActivePromptConfigs();
                    if (currentPromptIndex >= 0 && currentPromptIndex < activeConfigs.size()) {
                        flowPromptTextArea.setText(activeConfigs.get(currentPromptIndex).getPrompt());
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
                String defaultPrompt = "";
                int typeIndex = diagramTypeComboBox.getSelectedIndex();
                if (typeIndex == 0) defaultPrompt = IdeaSettings.DEFAULT_BUILD_FLOW_PROMPT;
                else if (typeIndex == 1) defaultPrompt = IdeaSettings.DEFAULT_CLASS_DIAGRAM_PROMPT;
                else if (typeIndex == 2) defaultPrompt = IdeaSettings.DEFAULT_UML_SEQUENCE_PROMPT;
                else if (typeIndex == 3) defaultPrompt = IdeaSettings.DEFAULT_STATE_DIAGRAM_PROMPT;
                else if (typeIndex == 4) defaultPrompt = IdeaSettings.DEFAULT_EXPLAIN_CODE_PROMPT;
                else if (typeIndex == 5) defaultPrompt = IdeaSettings.DEFAULT_REVIEW_CODE_PROMPT;
                else if (typeIndex == 6) defaultPrompt = IdeaSettings.DEFAULT_GENERATE_TEST_PROMPT;

                IdeaSettings.PromptConfig newConfig = new IdeaSettings.PromptConfig(name.trim(), defaultPrompt);
                List<IdeaSettings.PromptConfig> activeConfigs = getActivePromptConfigs();
                activeConfigs.add(newConfig);
                promptListModel.addElement(newConfig.getName());
                promptList.setSelectedIndex(promptListModel.size() - 1);
            }
        });

        removeButton.addActionListener(e -> {
            int selectedIndex = promptList.getSelectedIndex();
            if (selectedIndex >= 0) {
                List<IdeaSettings.PromptConfig> activeConfigs = getActivePromptConfigs();
                // If we are deleting the last item, ensure there is at least one
                if (activeConfigs.size() <= 1) {
                    JOptionPane.showMessageDialog(mainPanel, "必须保留至少一个提示词配置。", "无法删除", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                currentPromptIndex = -1; // Prevent saving to the deleted index
                activeConfigs.remove(selectedIndex);
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
                List<IdeaSettings.PromptConfig> activeConfigs = getActivePromptConfigs();
                String oldName = activeConfigs.get(selectedIndex).getName();
                String newName = JOptionPane.showInputDialog(mainPanel, "请输入新名称:", oldName);
                if (newName != null && !newName.trim().isEmpty()) {
                    activeConfigs.get(selectedIndex).setName(newName.trim());
                    promptListModel.set(selectedIndex, newName.trim());
                }
            }
        });
    }

    private void saveCurrentPrompt() {
        if (currentPromptIndex >= 0) {
            List<IdeaSettings.PromptConfig> activeConfigs = getActivePromptConfigs();
            if (currentPromptIndex < activeConfigs.size()) {
                activeConfigs.get(currentPromptIndex).setPrompt(flowPromptTextArea.getText());
            }
        }
    }

    private void populatePromptListForActiveTab() {
        promptListModel.clear();
        List<IdeaSettings.PromptConfig> activeConfigs = getActivePromptConfigs();
        for (IdeaSettings.PromptConfig config : activeConfigs) {
            promptListModel.addElement(config.getName());
        }

        if (!activeConfigs.isEmpty()) {
            promptList.setSelectedIndex(0);
        } else {
            flowPromptTextArea.setText("");
            flowPromptTextArea.setEnabled(false);
            currentPromptIndex = -1;
        }
    }

    private List<IdeaSettings.PromptConfig> getActivePromptConfigs() {
        int index = currentDiagramTypeIndex;
        if (index == 0) return flowPromptConfigs;
        else if (index == 1) return classPromptConfigs;
        else if (index == 2) return sequencePromptConfigs;
        else if (index == 3) return statePromptConfigs;
        else if (index == 4) return explainPromptConfigs;
        else if (index == 5) return reviewPromptConfigs;
        else if (index == 6) return testPromptConfigs;
        return flowPromptConfigs;
    }
    
    /**
     * 创建模式配置面板
     */
    private void createPatternConfigPanel() {
        patternConfigPanel = new JPanel(new BorderLayout());
        patternConfigPanel.setBorder(com.intellij.util.ui.JBUI.Borders.empty(10));

        // 流程图/时序图相关类模式
        relevantPatternsArea = new JTextArea(2, 30);
        relevantPatternsArea.setToolTipText("匹配相关类的正则表达式模式，每行一个");
        relevantPatternsArea.setLineWrap(true);
        relevantPatternsArea.setWrapStyleWord(true);
        JScrollPane relevantScrollPane = new com.intellij.ui.components.JBScrollPane(relevantPatternsArea);

        JLabel relevantLabel = new JLabel("流程图/时序图 - 相关类模式 (&R):");
        relevantLabel.setDisplayedMnemonic('R');
        relevantLabel.setLabelFor(relevantPatternsArea);

        // 流程图/时序图排除类模式
        excludedPatternsArea = new JTextArea(2, 30);
        excludedPatternsArea.setToolTipText("排除类的正则表达式模式，每行一个");
        excludedPatternsArea.setLineWrap(true);
        excludedPatternsArea.setWrapStyleWord(true);
        JScrollPane excludedScrollPane = new com.intellij.ui.components.JBScrollPane(excludedPatternsArea);

        JLabel excludedLabel = new JLabel("流程图/时序图 - 排除类模式 (&E):");
        excludedLabel.setDisplayedMnemonic('E');
        excludedLabel.setLabelFor(excludedPatternsArea);

        // 类图深度
        classDiagramDepthSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 10, 1));
        classDiagramDepthSpinner.setToolTipText("类图关联类扫描的深度(1-10)");
        JLabel depthLabel = new JLabel("类图 - 扫描深度 (&D):");
        depthLabel.setDisplayedMnemonic('D');
        depthLabel.setLabelFor(classDiagramDepthSpinner);

        // 包含库源码
        includeLibrarySourcesCheckBox = new JCheckBox("分析外部库或非项目源码 (仅限带有源码的类)");
        includeLibrarySourcesCheckBox.setMnemonic('S');
        includeLibrarySourcesCheckBox.setToolTipText("勾选此项以在类图生成时深入解析第三方依赖库源码");

        // 类图相关类模式
        classRelevantPatternsArea = new JTextArea(2, 30);
        classRelevantPatternsArea.setToolTipText("类图中匹配相关类的正则表达式模式，每行一个");
        classRelevantPatternsArea.setLineWrap(true);
        classRelevantPatternsArea.setWrapStyleWord(true);
        JScrollPane classRelevantScrollPane = new com.intellij.ui.components.JBScrollPane(classRelevantPatternsArea);

        JLabel classRelevantLabel = new JLabel("类图 - 相关类模式 (&C):");
        classRelevantLabel.setDisplayedMnemonic('C');
        classRelevantLabel.setLabelFor(classRelevantPatternsArea);

        // 类图排除类模式
        classExcludedPatternsArea = new JTextArea(2, 30);
        classExcludedPatternsArea.setToolTipText("类图中排除类的正则表达式模式，每行一个");
        classExcludedPatternsArea.setLineWrap(true);
        classExcludedPatternsArea.setWrapStyleWord(true);
        JScrollPane classExcludedScrollPane = new com.intellij.ui.components.JBScrollPane(classExcludedPatternsArea);

        JLabel classExcludedLabel = new JLabel("类图 - 排除类模式 (&X):");
        classExcludedLabel.setDisplayedMnemonic('X');
        classExcludedLabel.setLabelFor(classExcludedPatternsArea);

        JPanel innerForm = com.intellij.util.ui.FormBuilder.createFormBuilder()
                .addLabeledComponent(relevantLabel, relevantScrollPane)
                .addLabeledComponent(excludedLabel, excludedScrollPane)
                .addSeparator(10)
                .addLabeledComponent(depthLabel, classDiagramDepthSpinner)
                .addComponentToRightColumn(includeLibrarySourcesCheckBox)
                .addLabeledComponent(classRelevantLabel, classRelevantScrollPane)
                .addLabeledComponent(classExcludedLabel, classExcludedScrollPane)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        patternConfigPanel.add(innerForm, BorderLayout.CENTER);
    }
    


    public List<String> getRelevantPatterns() {
        return Arrays.asList(relevantPatternsArea.getText().split("\n"));
    }

    public List<String> getExcludedPatterns() {
        return Arrays.asList(excludedPatternsArea.getText().split("\n"));
    }

    public List<String> getClassRelevantPatterns() {
        return Arrays.asList(classRelevantPatternsArea.getText().split("\n"));
    }

    public List<String> getClassExcludedPatterns() {
        return Arrays.asList(classExcludedPatternsArea.getText().split("\n"));
    }

    public int getClassDiagramDepth() {
        return (Integer) classDiagramDepthSpinner.getValue();
    }

    public boolean isIncludeLibrarySources() {
        return includeLibrarySourcesCheckBox.isSelected();
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
        saveCurrentPrompt();
        return flowPromptConfigs;
    }

    public List<IdeaSettings.PromptConfig> getClassPrompts() {
        saveCurrentPrompt();
        return classPromptConfigs;
    }

    public List<IdeaSettings.PromptConfig> getSequencePrompts() {
        saveCurrentPrompt();
        return sequencePromptConfigs;
    }

    public List<IdeaSettings.PromptConfig> getStatePrompts() {
        saveCurrentPrompt();
        return statePromptConfigs;
    }

    public List<IdeaSettings.PromptConfig> getExplainPrompts() {
        saveCurrentPrompt();
        return explainPromptConfigs;
    }

    public List<IdeaSettings.PromptConfig> getReviewPrompts() {
        saveCurrentPrompt();
        return reviewPromptConfigs;
    }

    public List<IdeaSettings.PromptConfig> getTestPrompts() {
        saveCurrentPrompt();
        return testPromptConfigs;
    }



    public String getPlantumlPathValue() {
        return this.plantumlPathVal.getText();
    }
}
