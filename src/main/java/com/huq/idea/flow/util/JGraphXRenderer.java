package com.huq.idea.flow.util;

import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
import com.huq.idea.flow.model.FlowGraphData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxRectangle;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for rendering flow diagrams using JGraphX
 *
 * @author huqiang
 * @since 2024/8/10
 */
public class JGraphXRenderer {
    private static final Logger LOG = Logger.getInstance(JGraphXRenderer.class);

    // 节点宽度
    private static final int NODE_WIDTH = 200;
    // 节点高度
    private static final int NODE_HEIGHT = 40;
    // 节点间距
    private static final int NODE_SPACING = 60;

    /**
     * 创建一个显示JGraphX流程图的组件（从CallStack数据）
     *
     * @param callStack 方法调用栈
     * @return 包含流程图的JGraphComponent
     */
    public static JComponent createFlowDiagramComponent(CallStack callStack) {
        JPanel panel = new JPanel(new BorderLayout());

        try {
            // 创建一个标签，显示"正在加载图表..."
            JLabel loadingLabel = new JLabel("正在加载流程图...", SwingConstants.CENTER);
            loadingLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 14));
            panel.add(loadingLabel, BorderLayout.CENTER);

            // 在后台线程中渲染图像
            SwingWorker<mxGraphComponent, Void> worker = new SwingWorker<>() {
                @Override
                protected mxGraphComponent doInBackground() {
                    try {
                        // 创建图形组件
                        mxGraph graph = new mxGraph();
                        Object parent = graph.getDefaultParent();

                        // 配置图形样式
                        configureGraphStyles(graph);

                        // 开始更新图形
                        graph.getModel().beginUpdate();

                        try {
                            // 创建节点和边
                            Map<String, Object> vertexMap = new HashMap<>();
                            buildGraphFromCallStack(graph, parent, callStack, vertexMap, 0, 0);
                        } finally {
                            // 结束更新
                            graph.getModel().endUpdate();
                        }

                        // 创建图形组件
                        mxGraphComponent graphComponent = new mxGraphComponent(graph);
                        graphComponent.setConnectable(false);
                        graphComponent.getViewport().setOpaque(true);
                        graphComponent.getViewport().setBackground(Color.WHITE);

                        // 应用布局
                        mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
                        layout.setInterRankCellSpacing(NODE_SPACING);
                        layout.setIntraCellSpacing(NODE_SPACING);
                        layout.execute(parent);

                        // 自动调整大小以适应内容
                        mxRectangle bounds = graph.getGraphBounds();
                        graphComponent.getViewport().setViewSize(new Dimension(
                                (int) bounds.getWidth() + 50,
                                (int) bounds.getHeight() + 50));

                        return graphComponent;
                    } catch (Exception e) {
                        LOG.error("Failed to create flow diagram", e);
                        return null;
                    }
                }

                @Override
                protected void done() {
                    try {
                        mxGraphComponent graphComponent = get();
                        if (graphComponent != null) {
                            // 替换加载标签
                            panel.remove(loadingLabel);
                            panel.add(graphComponent, BorderLayout.CENTER);
                            panel.revalidate();
                            panel.repaint();
                        } else {
                            // 显示错误消息
                            loadingLabel.setText("无法渲染流程图。请检查数据是否有效。");
                            loadingLabel.setForeground(Color.RED);
                        }
                    } catch (Exception e) {
                        LOG.error("Failed to display flow diagram", e);
                        loadingLabel.setText("渲染流程图时出错: " + e.getMessage());
                        loadingLabel.setForeground(Color.RED);
                    }
                }
            };

            worker.execute();

        } catch (Exception e) {
            LOG.error("Failed to create flow diagram panel", e);
            JLabel errorLabel = new JLabel("创建流程图面板时出错: " + e.getMessage(), SwingConstants.CENTER);
            errorLabel.setForeground(Color.RED);
            panel.add(errorLabel, BorderLayout.CENTER);
        }

        return panel;
    }

    /**
     * 配置图形样式
     */
    private static void configureGraphStyles(mxGraph graph) {
        mxStylesheet stylesheet = graph.getStylesheet();

        // 服务层样式
        Map<String, Object> serviceStyle = new HashMap<>();
        serviceStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
        serviceStyle.put(mxConstants.STYLE_ROUNDED, true);
        serviceStyle.put(mxConstants.STYLE_FILLCOLOR, "#D4E6F1"); // 浅蓝色
        serviceStyle.put(mxConstants.STYLE_STROKECOLOR, "#2874A6");
        serviceStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
        serviceStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
        stylesheet.putCellStyle("service", serviceStyle);

        // 控制层样式
        Map<String, Object> controllerStyle = new HashMap<>();
        controllerStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
        controllerStyle.put(mxConstants.STYLE_ROUNDED, true);
        controllerStyle.put(mxConstants.STYLE_FILLCOLOR, "#D5F5E3"); // 浅绿色
        controllerStyle.put(mxConstants.STYLE_STROKECOLOR, "#1E8449");
        controllerStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
        controllerStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
        stylesheet.putCellStyle("controller", controllerStyle);

        // 数据访问层样式
        Map<String, Object> daoStyle = new HashMap<>();
        daoStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
        daoStyle.put(mxConstants.STYLE_ROUNDED, true);
        daoStyle.put(mxConstants.STYLE_FILLCOLOR, "#FCF3CF"); // 浅黄色
        daoStyle.put(mxConstants.STYLE_STROKECOLOR, "#B7950B");
        daoStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
        daoStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
        stylesheet.putCellStyle("dao", daoStyle);

        // 实现类样式
        Map<String, Object> implStyle = new HashMap<>();
        implStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
        implStyle.put(mxConstants.STYLE_ROUNDED, true);
        implStyle.put(mxConstants.STYLE_FILLCOLOR, "#E5E7E9"); // 浅灰色
        implStyle.put(mxConstants.STYLE_STROKECOLOR, "#566573");
        implStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
        implStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
        stylesheet.putCellStyle("impl", implStyle);

        // 默认样式
        Map<String, Object> defaultStyle = new HashMap<>();
        defaultStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
        defaultStyle.put(mxConstants.STYLE_ROUNDED, true);
        defaultStyle.put(mxConstants.STYLE_FILLCOLOR, "#F5F5F5");
        defaultStyle.put(mxConstants.STYLE_STROKECOLOR, "#333333");
        defaultStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
        defaultStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
        stylesheet.putCellStyle("default", defaultStyle);

        // 边样式
        Map<String, Object> edgeStyle = new HashMap<>();
        edgeStyle.put(mxConstants.STYLE_STROKECOLOR, "#333333");
        edgeStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.0);
        edgeStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
        edgeStyle.put(mxConstants.STYLE_ROUNDED, true);
        stylesheet.putCellStyle("edge", edgeStyle);

        // 自环边样式
        Map<String, Object> selfLoopStyle = new HashMap<>();
        selfLoopStyle.put(mxConstants.STYLE_STROKECOLOR, "#333333");
        selfLoopStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.0);
        selfLoopStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
        selfLoopStyle.put(mxConstants.STYLE_ROUNDED, true);
        selfLoopStyle.put(mxConstants.STYLE_LOOP, true);
        selfLoopStyle.put(mxConstants.STYLE_EDGE, "orthogonalEdgeStyle");
//        selfLoopStyle.put(mxConstants.STYLE_CURVED, true);
        stylesheet.putCellStyle("selfLoop", selfLoopStyle);
    }

    /**
     * 递归构建调用栈的图形表示
     */
    private static void buildGraphFromCallStack(mxGraph graph, Object parent, CallStack callStack,
                                               Map<String, Object> vertexMap, int x, int y) {
        if (callStack == null || callStack.getMethodDescription() == null) {
            return;
        }

        MethodDescription methodDesc = callStack.getMethodDescription();
        String className = methodDesc.getClassName();
        String methodName = methodDesc.getName();
        String returnType = methodDesc.getReturnType();

        // 创建节点标签
        String label = methodName;
        if (returnType != null && !returnType.equals("void")) {
            label += ": " + returnType;
        }

        // 确定节点样式
        String style = determineNodeStyle(className);

        // 创建或获取节点
        String nodeId = className + "." + methodName;
        Object vertex;

        if (vertexMap.containsKey(nodeId)) {
            // 如果节点已存在，使用现有节点
            vertex = vertexMap.get(nodeId);
        } else {
            // 创建新节点
            vertex = graph.insertVertex(parent, null, label, x, y, NODE_WIDTH, NODE_HEIGHT, style);
            vertexMap.put(nodeId, vertex);

            // 设置工具提示
            String tooltip = createTooltip(methodDesc);
//            ((mxCell) vertex).setToolTipText(tooltip);
        }

        // 处理父节点关系
        if (callStack.getParent() != null && callStack.getParent().getMethodDescription() != null) {
            MethodDescription parentDesc = callStack.getParent().getMethodDescription();
            String parentNodeId = parentDesc.getClassName() + "." + parentDesc.getName();

            // 如果父节点存在于图中，创建边
            if (vertexMap.containsKey(parentNodeId)) {
                Object parentVertex = vertexMap.get(parentNodeId);

                // 创建边标签（如果有参数）
                String parameters = methodDesc.getAttr("parameters", "");
                String edgeLabel = parameters.isEmpty() ? "" : "(" + parameters + ")";

                // 检查是否是回环（自环）
                if (parentVertex == vertex) {
                    // 对于回环，使用特殊的样式
                    graph.insertEdge(parent, null, edgeLabel, parentVertex, vertex, "selfLoop");
                } else {
                    // 插入普通边
                    graph.insertEdge(parent, null, edgeLabel, parentVertex, vertex, "edge");
                }
            }
        }

        // 递归处理子节点
        int childX = x + NODE_WIDTH + NODE_SPACING;
        int childY = y;

        for (CallStack child : callStack.getChildren()) {
            buildGraphFromCallStack(graph, parent, child, vertexMap, childX, childY);
            childY += NODE_HEIGHT + NODE_SPACING;
        }
    }

    /**
     * 根据类名确定节点样式
     */
    private static String determineNodeStyle(String className) {
        if (className.contains("Service") || className.contains("Manager")) {
            return "service";
        } else if (className.contains("Controller") || className.contains("Api")) {
            return "controller";
        } else if (className.contains("Repository") || className.contains("Dao")) {
            return "dao";
        } else if (className.contains("Impl")) {
            return "impl";
        } else {
            return "default";
        }
    }

    /**
     * 创建节点工具提示
     */
    private static String createTooltip(MethodDescription methodDesc) {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("<html>");
        tooltip.append("<b>类:</b> ").append(methodDesc.getClassName()).append("<br>");
        tooltip.append("<b>方法:</b> ").append(methodDesc.getName()).append("<br>");

        String parameters = methodDesc.getAttr("parameters", "");
        if (!parameters.isEmpty()) {
            tooltip.append("<b>参数:</b> ").append(parameters).append("<br>");
        }

        String returnType = methodDesc.getReturnType();
        if (returnType != null && !returnType.equals("void")) {
            tooltip.append("<b>返回类型:</b> ").append(returnType).append("<br>");
        }

        String docComment = methodDesc.getDocComment() != null ? methodDesc.getDocComment().getText() : "";
        if (docComment != null && !docComment.isEmpty()) {
            // 清理JavaDoc注释
            docComment = docComment.replaceAll("/\\*\\*|\\*/|\\*", "").trim();
            docComment = docComment.replaceAll("@[a-zA-Z]+[^@]*", "").trim();

            if (!docComment.isEmpty()) {
                tooltip.append("<b>描述:</b> ").append(docComment).append("<br>");
            }
        }

        tooltip.append("</html>");
        return tooltip.toString();
    }

    /**
     * 创建一个显示JGraphX流程图的组件（从JSON数据）
     *
     * @param jsonData JSON格式的流程图数据
     * @param project  当前项目
     * @return 包含流程图的JGraphComponent
     */
    public static JComponent createFlowDiagramComponentFromJson(String jsonData, Project project) {
        JPanel panel = new JPanel(new BorderLayout());

        try {
            // 创建一个标签，显示"正在加载图表..."
            JLabel loadingLabel = new JLabel("正在加载流程图...", SwingConstants.CENTER);
            loadingLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 14));
            panel.add(loadingLabel, BorderLayout.CENTER);

            // 解析JSON数据
            FlowGraphData graphData = FlowGraphData.fromJson(jsonData);

            // 在后台线程中渲染图像
            SwingWorker<mxGraphComponent, Void> worker = new SwingWorker<>() {
                @Override
                protected mxGraphComponent doInBackground() {
                    try {
                        // 创建图形组件
                        mxGraph graph = new mxGraph();
                        Object parent = graph.getDefaultParent();

                        // 配置图形样式
                        configureGraphStyles(graph);

                        // 开始更新图形
                        graph.getModel().beginUpdate();

                        try {
                            // 创建节点和边
                            Map<String, Object> vertexMap = new HashMap<>();
                            buildGraphFromJsonData(graph, parent, graphData, vertexMap);
                        } finally {
                            // 结束更新
                            graph.getModel().endUpdate();
                        }

                        // 创建图形组件
                        mxGraphComponent graphComponent = new mxGraphComponent(graph);
                        graphComponent.setConnectable(false);
                        graphComponent.getViewport().setOpaque(true);
                        graphComponent.getViewport().setBackground(Color.WHITE);

                        // 添加双击事件处理
                        graphComponent.getGraphControl().addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent e) {
                                if (e.getClickCount() == 2) {
                                    mxCell cell = (mxCell) graphComponent.getCellAt(e.getX(), e.getY());
                                    if (cell != null && cell.isVertex()) {
                                        // 查找对应的节点
                                        for (FlowGraphData.Node node : graphData.getNodes()) {
                                            if (node.getLabel().equals(cell.getValue().toString())) {
                                                navigateToCode(project, node);
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        });

                        // 应用布局
                        mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
                        layout.setInterRankCellSpacing(NODE_SPACING);
                        layout.setIntraCellSpacing(NODE_SPACING);
                        layout.execute(parent);

                        // 自动调整大小以适应内容
                        mxRectangle bounds = graph.getGraphBounds();
                        graphComponent.getViewport().setViewSize(new Dimension(
                                (int) bounds.getWidth() + 50,
                                (int) bounds.getHeight() + 50));

                        return graphComponent;
                    } catch (Exception e) {
                        LOG.error("Failed to create flow diagram from JSON", e);
                        return null;
                    }
                }

                @Override
                protected void done() {
                    try {
                        mxGraphComponent graphComponent = get();
                        if (graphComponent != null) {
                            // 替换加载标签
                            panel.remove(loadingLabel);
                            panel.add(graphComponent, BorderLayout.CENTER);
                            panel.revalidate();
                            panel.repaint();
                        } else {
                            // 显示错误消息
                            loadingLabel.setText("无法渲染流程图。请检查JSON数据是否有效。");
                            loadingLabel.setForeground(Color.RED);
                        }
                    } catch (Exception e) {
                        LOG.error("Failed to display flow diagram", e);
                        loadingLabel.setText("渲染流程图时出错: " + e.getMessage());
                        loadingLabel.setForeground(Color.RED);
                    }
                }
            };

            worker.execute();

        } catch (Exception e) {
            LOG.error("Failed to create flow diagram panel", e);
            JLabel errorLabel = new JLabel("创建流程图面板时出错: " + e.getMessage(), SwingConstants.CENTER);
            errorLabel.setForeground(Color.RED);
            panel.add(errorLabel, BorderLayout.CENTER);
        }

        return panel;
    }

    /**
     * 从JSON数据构建图形
     */
    private static void buildGraphFromJsonData(mxGraph graph, Object parent, FlowGraphData graphData,
                                              Map<String, Object> vertexMap) {
        // 添加所有节点
        for (FlowGraphData.Node node : graphData.getNodes()) {
            String nodeId = node.getId();
            String label = node.getLabel();
            String type = node.getType();

            // 确定节点样式
            String style = determineNodeStyleFromType(type);

            // 创建节点
            Object vertex = graph.insertVertex(parent, nodeId, label, 0, 0, NODE_WIDTH, NODE_HEIGHT, style);
            vertexMap.put(nodeId, vertex);

            // 设置工具提示
            String tooltip = createTooltipFromNode(node);
            ((mxCell) vertex).setAttribute("tooltip",tooltip);
        }

        // 添加所有边
        for (FlowGraphData.Edge edge : graphData.getEdges()) {
            String sourceId = edge.getSource();
            String targetId = edge.getTarget();
            String label = edge.getLabel();

            // 获取源节点和目标节点
            Object sourceVertex = vertexMap.get(sourceId);
            Object targetVertex = vertexMap.get(targetId);

            if (sourceVertex != null && targetVertex != null) {
                // 检查是否是回环（自环）
                if (sourceId.equals(targetId)) {
                    // 对于回环，使用特殊的样式
                    graph.insertEdge(parent, null, label != null ? label : "", sourceVertex, targetVertex, "selfLoop");
                } else {
                    // 插入普通边
                    graph.insertEdge(parent, null, label != null ? label : "", sourceVertex, targetVertex, "edge");
                }
            }
        }
    }

    /**
     * 根据节点类型确定样式
     */
    private static String determineNodeStyleFromType(String type) {
        if (type == null) {
            return "default";
        }

        switch (type.toLowerCase()) {
            case "method":
                return "service";
            case "condition":
                return "controller";
            case "loop":
                return "dao";
            default:
                return "default";
        }
    }

    /**
     * 从节点创建工具提示
     */
    private static String createTooltipFromNode(FlowGraphData.Node node) {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("<html>");

        if (node.getClassName() != null) {
            tooltip.append("<b>类:</b> ").append(node.getClassName()).append("<br>");
        }

        if (node.getMethodName() != null) {
            tooltip.append("<b>方法:</b> ").append(node.getMethodName()).append("<br>");
        }

        if (node.getDescription() != null && !node.getDescription().isEmpty()) {
            tooltip.append("<b>描述:</b> ").append(node.getDescription()).append("<br>");
        }

        if (node.getFilePath() != null) {
            tooltip.append("<b>文件:</b> ").append(node.getFilePath()).append("<br>");
        }

        if (node.getLineNumber() > 0) {
            tooltip.append("<b>行号:</b> ").append(node.getLineNumber()).append("<br>");
        }

        tooltip.append("</html>");
        return tooltip.toString();
    }

    /**
     * 导航到节点对应的代码
     */
    private static void navigateToCode(Project project, FlowGraphData.Node node) {
        if (node.getFilePath() == null || node.getFilePath().isEmpty()) {
            return;
        }

        try {
            File file = new File(node.getFilePath());
            if (!file.exists()) {
                return;
            }

            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
            if (virtualFile != null) {
                int line = Math.max(0, node.getLineNumber() - 1); // 转换为0-based行号
                OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, line, 0);
                FileEditorManager.getInstance(project).openEditor(descriptor, true);
            }
        } catch (Exception e) {
            LOG.error("Failed to navigate to code", e);
        }
    }
}
