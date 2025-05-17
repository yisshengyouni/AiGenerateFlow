package com.huq.idea.flow.apidoc.window;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author huqiang
 * @since 2025/5/16 18:02
 */
public class UmlFlowToolWindow implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        JPanel welcome = new JPanel();
        welcome.add(new JLabel("Welcome to UML Flow Diagram Tool Window"));
        ContentManager contentManager = toolWindow.getContentManager();
        Content emptyDiagram = contentManager.getFactory().createContent(welcome, "Welcome", false);
        emptyDiagram.setCloseable(false);
        contentManager.addContent(emptyDiagram);

    }
}
