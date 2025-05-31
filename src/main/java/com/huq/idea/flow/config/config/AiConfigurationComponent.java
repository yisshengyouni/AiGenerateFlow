package com.huq.idea.flow.config.config;

import javax.swing.*;

/**
 * @author huqiang
 * @since 2024/8/1 21:11
 */
public class AiConfigurationComponent {
    private JTextArea flowPromptTextArea;
    private JTextField apiKeyText;
    private JPanel jPanel;
    private JTextField plantumlPathVal;

    public void init(IdeaSettings.State state) {
        plantumlPathVal.setText(state.getPlantumlPathVal());
        apiKeyText.setText(state.getApiKey());
        flowPromptTextArea.setText(state.getBuildFlowPrompt());
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
