package com.huq.idea.flow.config.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.BorderLayout;

/**
 * @author huqiang
 * @since 2024/8/1 17:42
 */
public class IdeaConfigurable implements Configurable {

    private AiConfigurationComponent settingsComponent;

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "UmlFlowAiConfigurable";
    }

    @Override
    public @Nullable JComponent createComponent() {
        // 这里的判断没啥用， 因为每次的 ideaConfigurable 都会创建一个新的对象
        if (settingsComponent == null) {
            settingsComponent = new AiConfigurationComponent();
        }
        settingsComponent.init(IdeaSettings.getInstance().getState());
        
        // 直接返回新的主面板，已经包含了所有配置组件
        return settingsComponent.getjPanel();
    }

    @Override
    public boolean isModified() {
        return true;
    }

    @Override
    public void apply() {
        IdeaSettings.State state = IdeaSettings.getInstance().getState();
        // 保存多AI模型API密钥配置
        state.setAiApiKeys(settingsComponent.getAiApiKeys());
        
        state.setBuildFlowPrompt(settingsComponent.getBuildFlowPrompt());
        state.setPlantumlPathVal(settingsComponent.getPlantumlPathValue());
        state.setRelevantClassPatterns(settingsComponent.getRelevantPatterns());
        state.setExcludedClassPatterns(settingsComponent.getExcludedPatterns());
    }
}
