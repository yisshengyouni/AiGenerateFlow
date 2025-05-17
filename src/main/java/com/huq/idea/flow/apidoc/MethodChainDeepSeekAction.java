package com.huq.idea.flow.apidoc;

import com.huq.idea.config.IdeaSettings;
import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.util.AiUtils;
import com.huq.idea.flow.util.MethodUtils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Action to analyze method call chain and generate UML sequence diagram using DeepSeek API
 *
 * @author huqiang
 * @since 2024/8/10
 */
public class MethodChainDeepSeekAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(MethodChainDeepSeekAction.class);

    // Prompt template is now configurable through settings

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile)) {
            return;
        }

        Editor editor = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);
        if (editor == null) {
            return;
        }

        // Get the method at the current cursor position
        LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
        int offset = editor.logicalPositionToOffset(logicalPosition);
        PsiMethod currentMethod = MethodUtils.getContainingMethodAtOffset(psiFile, offset);
        if (currentMethod == null) {
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "Method Chain Analysis",
                    "No method found at cursor position",
                    NotificationType.ERROR),
                    project);
            return;
        }

        // Start the analysis in a background task
        new Task.Backgroundable(project, "Analyzing Method Chain", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Analyzing method call chain...");
                indicator.setIndeterminate(true);

                // Generate the method call chain
                // Use the enhanced method chain visitor for better analysis
                // Wrap in a read action to avoid threading issues
                CallStack callStack = com.intellij.openapi.application.ReadAction.compute(() -> {
                    EnhancedMethodChainVisitor methodChainVisitor = new EnhancedMethodChainVisitor();
                    return methodChainVisitor.generateMethodChains(currentMethod, null);
                });

                // Convert the call stack to a string representation
                String callChainText = callStack.generateUml();

                indicator.setText("Generating UML diagram with DeepSeek API...");

                // Send to DeepSeek API using the configured prompt template
                String promptTemplate = IdeaSettings.getInstance().getState().getUmlSequencePrompt();
                String prompt = String.format(promptTemplate, callChainText);

                // Check if API key is set in settings
                String apiKey = IdeaSettings.getInstance().getState().getApiKey();
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        Notifications.Bus.notify(new Notification(
                                "com.yt.huq.idea",
                                "API Key Missing",
                                "DeepSeek API key is not configured. Please set it in Settings > UmlFlowAiConfigurable",
                                NotificationType.ERROR),
                                project);
                    });
                    return;
                }

                String umlDiagram = AiUtils.okRequest(prompt);

                if (umlDiagram == null || umlDiagram.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        Notifications.Bus.notify(new Notification(
                                "com.yt.huq.idea",
                                "UML Generation",
                                "Failed to generate UML diagram",
                                NotificationType.ERROR),
                                project);
                    });
                    return;
                }

                // Clean up the response if needed
                if (!umlDiagram.startsWith("@startuml")) {
                    umlDiagram = umlDiagram.substring(umlDiagram.indexOf("@startuml"));
                }
                if (!umlDiagram.endsWith("@enduml")) {
                    umlDiagram = umlDiagram + "\n@enduml";
                }

                // Show the result in a dialog
                String finalUmlDiagram = umlDiagram;
                SwingUtilities.invokeLater(() -> showUmlDialog(project, finalUmlDiagram, currentMethod.getName()));
            }
        }.queue();
    }

    private void showUmlDialog(Project project, String umlContent, String methodName) {
        JFrame frame = new JFrame("UML Sequence Diagram: " + methodName);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(800, 600);

        JPanel panel = new JPanel(new BorderLayout());
        JTextArea textArea = new JTextArea();
        textArea.setEditable(true);
        textArea.setText(umlContent);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton copyButton = new JButton("Copy to Clipboard");
        copyButton.addActionListener(e -> {
            textArea.selectAll();
            textArea.copy();
            Notifications.Bus.notify(new Notification(
                    "com.yt.huq.idea",
                    "UML Diagram",
                    "UML diagram copied to clipboard",
                    NotificationType.INFORMATION),
                    project);
        });
        buttonPanel.add(copyButton);

        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        frame.add(panel);
        frame.setVisible(true);
    }
}
