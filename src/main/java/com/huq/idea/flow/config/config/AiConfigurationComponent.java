package com.huq.idea.flow.config.config;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author huqiang
 * @since 2024/8/1 21:11
 */
public class AiConfigurationComponent {
    private JTextArea flowPromptTextArea;
    private JTextField apiKeyText;
    private JPanel jPanel;
    private JTextField plantumlPathVal;

    private JTextArea relevantPatternsArea;
    private JTextArea excludedPatternsArea;

    public void init(IdeaSettings.State state) {
        plantumlPathVal.setText(state.getPlantumlPathVal());
        apiKeyText.setText(state.getApiKey());
        flowPromptTextArea.setText(state.getBuildFlowPrompt());
        relevantPatternsArea.setText(String.join("\n", state.getRelevantClassPatterns()));
        excludedPatternsArea.setText(String.join("\n", state.getExcludedClassPatterns()));
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


    public JTextField getApiKeyText() {
        return this.apiKeyText;
    }

    public JPanel getjPanel() {
        return this.jPanel;
    }

    public String getApiKey() {
        return this.apiKeyText.getText();
    }

    public String getBuildFlowPrompt() {
        return this.flowPromptTextArea.getText();
    }


    public String getPlantumlPathValue() {
        return this.plantumlPathVal.getText();
    }
}
