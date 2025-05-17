package com.huq.idea.flow.apidoc.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;

import javax.swing.*;

/**
 * @author huqiang
 * @since 2025/5/16 18:19
 */
public class UmlFlowServiceImpl implements UmlFlowService{


    private final Project _project;
    private final ToolWindow _toolWindow;

    public UmlFlowServiceImpl(Project _project) {
        this._project = _project;
        this._toolWindow = ToolWindowManager.getInstance(_project).getToolWindow(PLUGIN_NAME);
    }

    @Override
    public void addFlow(JComponent jComponent) {
        ContentManager contentManager = _toolWindow.getContentManager();
        final Content content = contentManager.getFactory().createContent(jComponent, jComponent.getName(), false);
        contentManager.addContent(content);
        contentManager.setSelectedContent(content);
        if (_toolWindow.isActive()) {
            _toolWindow.show();
        } else {
            _toolWindow.activate(null);
        }
    }
}
