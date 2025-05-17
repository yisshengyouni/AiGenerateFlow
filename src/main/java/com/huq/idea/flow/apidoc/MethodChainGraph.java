package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
import com.huq.idea.flow.view.FlowNode;
import com.huq.idea.flow.view.MultiProcessFlowchart;
import com.huq.idea.flow.view.PlanUMLUtil;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.collections.CollectionUtils;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author huqiang
 * @since 2024/7/13 15:59
 */
public class MethodChainGraph {

    private static final Logger log = Logger.getInstance(MethodChainGraph.class);

    private CallStack callStack;

    public MethodChainGraph(CallStack callStack) {
        this.callStack = callStack;
    }

    public void recursionFlow(List<FlowNode> mainProcesses, CallStack callStack){
        List<CallStack> children = callStack.getChildren();
        if (CollectionUtils.isEmpty(children)) {
            return;
        }
        for (CallStack child : children) {
            FlowNode node = new FlowNode(child.getMethodDescription().getName(), 0, 0);
            mainProcesses.add(node);
            recursionFlow(node.getChildren(), child);
        }
    }

    public void showFlow() {
        JFrame frame = new JFrame("多流程展示示例");

        List<FlowNode> mainProcesses = new ArrayList<>();
        recursionFlow(mainProcesses, callStack);
        MultiProcessFlowchart panel = new MultiProcessFlowchart(mainProcesses);

        frame.add(panel);
        frame.setSize(500, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    public void show() {
        JFrame frame = new JFrame("Method Chain");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(800, 600);

        JPanel panel = new JPanel(new BorderLayout());
        JTextArea textArea = new JTextArea();
        textArea.setEditable(true);

        String plantUmlSequenceDiagram = PlanUMLUtil.format(callStack);
        textArea.setText(plantUmlSequenceDiagram);

        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
        frame.add(panel);
        frame.setVisible(true);
    }

    public void drawSequenceDiagram() {
        JFrame frame = new JFrame("Method Chain");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(800, 600);

        JPanel panel = new JPanel(new BorderLayout());
        JTextArea textArea = new JTextArea();
        textArea.setEditable(true);
        MethodDescription methodDescription = callStack.getMethodDescription();
        String comment = methodDescription.getDocComment() == null ? "" : methodDescription.getDocComment().getText();
        String uml = callStack.generateUml();
        textArea.setText(uml);
        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
        frame.add(panel);
        frame.setVisible(true);
    }

    public void drawFlow(JTextArea textArea) {
        MethodDescription methodDescription = callStack.getMethodDescription();
        String comment = methodDescription.getDocComment() == null ? "" : methodDescription.getDocComment().getText();
        textArea.append("@startuml\n");
        textArea.append("| " + methodDescription.getSimpleClassName() + " | \n");
        textArea.append("start \n");
        textArea.append(":");
        textArea.append(methodDescription.getSimpleClassName() + " # " + methodDescription.getName());
        textArea.append("; \n");
        textArea.append("note \n");
//        textArea.append(AiUtils.okRequest(methodDescription.getText())+"\n");
        textArea.append("end note \n");

        textArea.append("| " + methodDescription.getSimpleClassName() + " | \n");

        draw(textArea, callStack, "\t");

//        textArea.append("|" + methodDescription.getSimpleClassName() + "| \n");

        textArea.append("\n end\n@enduml");
    }

    public void draw(JTextArea textArea, CallStack callStack, String level) {
        if (callStack != null && callStack.getChildren() != null && !callStack.getChildren().isEmpty()) {
            MethodDescription parent = callStack.getMethodDescription();
            for (CallStack child : callStack.getChildren()) {
                MethodDescription methodDescription = child.getMethodDescription();

                boolean isExpression = "true".equals(methodDescription.getAttr("expression"));

                if (!isExpression) {
                    textArea.append("| " + methodDescription.getSimpleClassName() + " |  \n");
                }
                String comment = methodDescription.getDocComment() == null ? "" : methodDescription.getDocComment().getText();
//                textArea.append(level+"+++ "+methodDescription.getClassName() + " -> " + methodDescription.getName() + "  comment: " + comment+ "\n");
//                textArea.append("\n |" + methodDescription.getSimpleClassName() + "| \n");
                textArea.append(":");
                textArea.append(methodDescription.getSimpleClassName() + " # " + methodDescription.getName());
                if (methodDescription.getAttr("expression") != null) {
                    textArea.append("#"+methodDescription.getAttr("expression"));
                }
                textArea.append(";\n");
                textArea.append("note \n");
//                textArea.append(comment + "\n");
                if (!isExpression) {
//                    String response = AiUtils.okRequest(methodDescription.getText());
//                    textArea.append(response +"\n");
                } else {
                    textArea.append(methodDescription.getAttr("expression.subBody") +"\n");
                }
                textArea.append("end note \n");
                draw(textArea, child, level + "\t");
                if (!isExpression) {
                    textArea.append("| " + parent.getSimpleClassName() + " |  \n");
                }
            }
//            textArea.append("| " + parent.getSimpleClassName() + " |  \n");
            textArea.append(":;\n");
        }
    }
}
