package com.huq.idea.flow.util;

import com.huq.idea.flow.model.FlowGraphData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.model.mxGeometry;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxRectangle;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxPerimeter;
import com.mxgraph.view.mxStylesheet;
import org.jgrapht.Graph;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 使用JGraphT实现的流程图渲染器
 *
 * @author huqiang
 * @since 2024/8/10
 */
public class JGraphTRenderer {
    private static final Logger LOG = Logger.getInstance(JGraphTRenderer.class);

    /**
     * 创建一个流程图面板
     *
     * @param jsonData JSON格式的流程图数据
     * @param project  当前项目
     * @return 包含流程图的JPanel
     */
    public static JComponent createFlowDiagramComponent(String jsonData, Project project) {
        FlowGraphData graphData = FlowGraphData.fromJson(jsonData);
        return new FlowGraphPanel(graphData, project);
    }

    /**
     * 流程图面板，使用JGraphT和自定义绘制实现
     */
    private static class FlowGraphPanel extends JPanel {
        private final FlowGraphData graphData;
        private final Project project;
        private final Graph<FlowGraphData.Node, DefaultEdge> graph;
        private final Map<String, FlowGraphData.Node> nodeMap = new HashMap<>();
        private final Map<FlowGraphData.Node, Rectangle2D> nodePositions = new HashMap<>();

        private double scale = 1.0;
        private Point2D offset = new Point2D.Double(0, 0);
        private Point dragStart = null;
        private final mxGraphComponent graphComponent;
        private final JScrollPane scrollPane;

        public FlowGraphPanel(FlowGraphData graphData, Project project) {
            this.graphData = graphData;
            this.project = project;
            this.graph = buildGraph(graphData);


            // 设置面板布局为BorderLayout
            setLayout(new BorderLayout());

            // 使用JGraphX适配器
            JGraphXAdapter<FlowGraphData.Node, DefaultEdge> graphAdapter = new JGraphXAdapter<>(graph);
            mxGraph mxGraph = graphAdapter.getView().getGraph();

            // 创建样式
            registerGraphStyles(mxGraph);
            // 应用样式到节点和边

            applyStyles(mxGraph, graphAdapter, graphData);

            // 使用分层布局
            mxHierarchicalLayout layout = new mxHierarchicalLayout(mxGraph);
            layout.setInterRankCellSpacing(80.0);
            layout.setInterHierarchySpacing(60.0);
            layout.setParallelEdgeSpacing(15.0);
            layout.setDisableEdgeStyle(false);
            layout.execute(mxGraph.getDefaultParent());

            // 自动调整视图大小
            mxRectangle bounds = mxGraph.getGraphBounds();
            double width = bounds.getWidth() + 50;
            double height = bounds.getHeight() + 50;
            mxGraph.getModel().setGeometry(mxGraph.getDefaultParent(),
                    new mxGeometry(0, 0, width, height));

            // 创建图形组件
            graphComponent = new mxGraphComponent(mxGraph);
            graphComponent.setConnectable(false);
            graphComponent.getGraph().setAllowDanglingEdges(false);
            graphComponent.setBackground(Color.WHITE);
            graphComponent.setGridVisible(true);
            graphComponent.getViewport().setBackground(Color.WHITE);
            // 启用自动调整大小
            graphComponent.setAutoExtend(true);
            graphComponent.setAutoScroll(true);
            // 设置缩放策略
//            graphComponent.getZoomHandler().setEnabled(true);
            graphComponent.setCenterZoom(true);


            // 创建滚动面板并添加图形组件
            scrollPane = new JScrollPane(graphComponent);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

            // 设置滚动面板的首选大小
            scrollPane.setPreferredSize(new Dimension(800, 600));
            // 添加滚动面板到主面板
            add(scrollPane, BorderLayout.CENTER);

            // 设置滚动速率
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            scrollPane.getHorizontalScrollBar().setUnitIncrement(16);

            // 添加鼠标事件处理
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragStart = e.getPoint();

                    // 检查是否双击了节点
                    if (e.getClickCount() == 2) {
                        handleDoubleClick(e.getPoint());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragStart = null;
                }
            });

            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragStart != null) {
                        double dx = (e.getX() - dragStart.x) / scale;
                        double dy = (e.getY() - dragStart.y) / scale;
                        offset = new Point2D.Double(offset.getX() + dx, offset.getY() + dy);
                        dragStart = e.getPoint();
                        repaint();
                    }
                }
            });

            addMouseWheelListener(new MouseAdapter() {
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    // 缩放处理
                    double oldScale = scale;
                    if (e.getWheelRotation() < 0) {
                        scale *= 1.1;
                    } else {
                        scale /= 1.1;
                    }

                    // 确保缩放点保持不变
                    Point2D mousePoint = e.getPoint();
                    double zoomX = mousePoint.getX() / oldScale - offset.getX();
                    double zoomY = mousePoint.getY() / oldScale - offset.getY();
                    offset = new Point2D.Double(
                            mousePoint.getX() / scale - zoomX,
                            mousePoint.getY() / scale - zoomY
                    );

                    repaint();
                }
            });

        }

        /**
         * 处理双击事件，跳转到相关代码
         */
        private void handleDoubleClick(Point point) {
            // 转换点坐标到图形坐标系
            Point2D graphPoint = new Point2D.Double(
                    (point.x / scale) - offset.getX(),
                    (point.y / scale) - offset.getY()
            );

            // 查找被点击的节点
            for (Map.Entry<FlowGraphData.Node, Rectangle2D> entry : nodePositions.entrySet()) {
                if (entry.getValue().contains(graphPoint)) {
                    FlowGraphData.Node node = entry.getKey();
                    navigateToCode(node);
                    break;
                }
            }
        }

        /**
         * 导航到节点对应的代码
         */
        private void navigateToCode(FlowGraphData.Node node) {
            if (node.getFilePath() == null || node.getFilePath().isEmpty()) {
                return;
            }

            try {
                File file = new File(node.getFilePath());
                if (!file.exists()) {
                    LOG.error("File not found: " + node.getFilePath());
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

        /**
         * 构建JGraphT图形
         */
        private Graph<FlowGraphData.Node, DefaultEdge> buildGraph(FlowGraphData graphData) {
            Graph<FlowGraphData.Node, DefaultEdge> graph = GraphTypeBuilder
                    .<FlowGraphData.Node, DefaultEdge>directed()
                    .allowingMultipleEdges(true)
                    .allowingSelfLoops(true)
                    .edgeClass(DefaultEdge.class)
                    .weighted(false)
                    .buildGraph();

            // 添加节点
            for (FlowGraphData.Node node : graphData.getNodes()) {
                graph.addVertex(node);
                nodeMap.put(node.getId(), node);
            }

            // 添加边
            for (FlowGraphData.Edge edge : graphData.getEdges()) {
                FlowGraphData.Node source = nodeMap.get(edge.getSource());
                FlowGraphData.Node target = nodeMap.get(edge.getTarget());
                if (source != null && target != null) {
                    graph.addEdge(source, target);
                }
            }

            return graph;
        }


        /**
         * 注册JGraphX时序图和流程图所需的所有节点和连线样式
         *
         * @param graph mxGraph实例
         */
        public static void registerGraphStyles(mxGraph graph) {
            mxStylesheet stylesheet = graph.getStylesheet();

            // ================= 流程图节点样式 =================

            // 流程（Process）- 圆角矩形
            Map<String, Object> processStyle = new HashMap<>();
            processStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
            processStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter);
            processStyle.put(mxConstants.STYLE_ROUNDED, true);
            processStyle.put(mxConstants.STYLE_FILLCOLOR, "#FFFFFF");
            processStyle.put(mxConstants.STYLE_STROKECOLOR, "#6482B9");
            processStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            processStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            stylesheet.putCellStyle("process", processStyle);

            // 判断条件（Decision）- 菱形
            Map<String, Object> decisionStyle = new HashMap<>();
            decisionStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RHOMBUS);
            decisionStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RhombusPerimeter);
            decisionStyle.put(mxConstants.STYLE_FILLCOLOR, "#FFFFCC");
            decisionStyle.put(mxConstants.STYLE_STROKECOLOR, "#6482B9");
            decisionStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            decisionStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            stylesheet.putCellStyle("decision", decisionStyle);
            stylesheet.putCellStyle("condition", decisionStyle); // 别名

            // 开始/结束（Start/End）- 椭圆
            Map<String, Object> terminatorStyle = new HashMap<>();
            terminatorStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE);
            terminatorStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.EllipsePerimeter);
            terminatorStyle.put(mxConstants.STYLE_FILLCOLOR, "#E6FFCC");
            terminatorStyle.put(mxConstants.STYLE_STROKECOLOR, "#6482B9");
            terminatorStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            terminatorStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            stylesheet.putCellStyle("terminator", terminatorStyle);

            // 数据（Data）- 平行四边形
            Map<String, Object> dataStyle = new HashMap<>();
//        dataStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_PARALLELOGRAM);
            dataStyle.put(mxConstants.STYLE_SHAPE, "parallelogram");
            dataStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RhombusPerimeter);
            dataStyle.put(mxConstants.STYLE_FILLCOLOR, "#DAE8FC");
            dataStyle.put(mxConstants.STYLE_STROKECOLOR, "#6482B9");
            dataStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            dataStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            stylesheet.putCellStyle("data", dataStyle);

            // 预定义流程（Predefined Process）- 双边矩形
            Map<String, Object> predefinedProcessStyle = new HashMap<>();
            predefinedProcessStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
            predefinedProcessStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter);
            predefinedProcessStyle.put(mxConstants.STYLE_FILLCOLOR, "#F5F5F5");
            predefinedProcessStyle.put(mxConstants.STYLE_STROKECOLOR, "#6482B9");
            predefinedProcessStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            predefinedProcessStyle.put(mxConstants.STYLE_STROKEWIDTH, 3);
            stylesheet.putCellStyle("predefinedProcess", predefinedProcessStyle);

            // 文档（Document）- 文档形状
            Map<String, Object> documentStyle = new HashMap<>();
            documentStyle.put(mxConstants.STYLE_SHAPE, "document");
            documentStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter);
            documentStyle.put(mxConstants.STYLE_FILLCOLOR, "#FFF2CC");
            documentStyle.put(mxConstants.STYLE_STROKECOLOR, "#D6B656");
            documentStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            documentStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            stylesheet.putCellStyle("document", documentStyle);

            // 多文档（Multiple Document）
            Map<String, Object> multiDocumentStyle = new HashMap<>();
            multiDocumentStyle.put(mxConstants.STYLE_SHAPE, "mxgraph.flowchart.multi-document");
            multiDocumentStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter);
            multiDocumentStyle.put(mxConstants.STYLE_FILLCOLOR, "#FFF2CC");
            multiDocumentStyle.put(mxConstants.STYLE_STROKECOLOR, "#D6B656");
            multiDocumentStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            multiDocumentStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            stylesheet.putCellStyle("multiDocument", multiDocumentStyle);

            // 手动输入（Manual Input）- 梯形
            Map<String, Object> manualInputStyle = new HashMap<>();
            manualInputStyle.put(mxConstants.STYLE_SHAPE, "mxgraph.flowchart.manual_input");
            manualInputStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RhombusPerimeter);
            manualInputStyle.put(mxConstants.STYLE_FILLCOLOR, "#F8CECC");
            manualInputStyle.put(mxConstants.STYLE_STROKECOLOR, "#B85450");
            manualInputStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            manualInputStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            stylesheet.putCellStyle("manualInput", manualInputStyle);

            // 准备（Preparation）- 六边形
            Map<String, Object> preparationStyle = new HashMap<>();
            preparationStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_HEXAGON);
            preparationStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.HexagonPerimeter);
            preparationStyle.put(mxConstants.STYLE_FILLCOLOR, "#E1D5E7");
            preparationStyle.put(mxConstants.STYLE_STROKECOLOR, "#9673A6");
            preparationStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            preparationStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            stylesheet.putCellStyle("preparation", preparationStyle);

            // 数据库（Database）
            Map<String, Object> databaseStyle = new HashMap<>();
            databaseStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_CYLINDER);
            databaseStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.EllipsePerimeter);
            databaseStyle.put(mxConstants.STYLE_FILLCOLOR, "#D5E8D4");
            databaseStyle.put(mxConstants.STYLE_STROKECOLOR, "#82B366");
            databaseStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            databaseStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            stylesheet.putCellStyle("database", databaseStyle);

            // 注释（Comment/Note）
            Map<String, Object> noteStyle = new HashMap<>();
            noteStyle.put(mxConstants.STYLE_SHAPE, "note");
            noteStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter);
            noteStyle.put(mxConstants.STYLE_FILLCOLOR, "#FFF4C3");
            noteStyle.put(mxConstants.STYLE_STROKECOLOR, "#D9A741");
            noteStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            noteStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            stylesheet.putCellStyle("note", noteStyle);
            stylesheet.putCellStyle("comment", noteStyle); // 别名

            // 云（Cloud）- 外部系统
            Map<String, Object> cloudStyle = new HashMap<>();
            cloudStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_CLOUD);
            cloudStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.EllipsePerimeter);
            cloudStyle.put(mxConstants.STYLE_FILLCOLOR, "#F5F5F5");
            cloudStyle.put(mxConstants.STYLE_STROKECOLOR, "#666666");
            cloudStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            cloudStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            stylesheet.putCellStyle("cloud", cloudStyle);

            // 延迟（Delay）
            Map<String, Object> delayStyle = new HashMap<>();
            delayStyle.put(mxConstants.STYLE_SHAPE, "mxgraph.flowchart.delay");
            delayStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.EllipsePerimeter);
            delayStyle.put(mxConstants.STYLE_FILLCOLOR, "#F8CECC");
            delayStyle.put(mxConstants.STYLE_STROKECOLOR, "#B85450");
            delayStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            delayStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            stylesheet.putCellStyle("delay", delayStyle);

            // 显示（Display）
            Map<String, Object> displayStyle = new HashMap<>();
            displayStyle.put(mxConstants.STYLE_SHAPE, "mxgraph.flowchart.display");
            displayStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.EllipsePerimeter);
            displayStyle.put(mxConstants.STYLE_FILLCOLOR, "#D5E8D4");
            displayStyle.put(mxConstants.STYLE_STROKECOLOR, "#82B366");
            displayStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            displayStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            stylesheet.putCellStyle("display", displayStyle);

            // 人物（Actor）
            Map<String, Object> actorStyle = new HashMap<>();
            actorStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ACTOR);
            actorStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.EllipsePerimeter);
            actorStyle.put(mxConstants.STYLE_FILLCOLOR, "#E1D5E7");
            actorStyle.put(mxConstants.STYLE_STROKECOLOR, "#9673A6");
            actorStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            actorStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            stylesheet.putCellStyle("actor", actorStyle);

            // 泳道（Swimlane）
            Map<String, Object> swimlaneStyle = new HashMap<>();
            swimlaneStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_SWIMLANE);
            swimlaneStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter);
            swimlaneStyle.put(mxConstants.STYLE_FILLCOLOR, "#F5F5F5");
            swimlaneStyle.put(mxConstants.STYLE_STROKECOLOR, "#999999");
            swimlaneStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            swimlaneStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            swimlaneStyle.put(mxConstants.STYLE_STARTSIZE, 30); // 标题高度
            swimlaneStyle.put(mxConstants.STYLE_FONTSIZE, 14);
            swimlaneStyle.put(mxConstants.STYLE_FONTSTYLE, 1); // 粗体
            stylesheet.putCellStyle("swimlane", swimlaneStyle);

            // ================= 时序图节点样式 =================

            // 对象（Object/Lifeline）- 时序图中的顶部对象
            Map<String, Object> objectStyle = new HashMap<>();
            objectStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
            objectStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter);
            objectStyle.put(mxConstants.STYLE_FILLCOLOR, "#E1D5E7");
            objectStyle.put(mxConstants.STYLE_STROKECOLOR, "#9673A6");
            objectStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            objectStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            objectStyle.put(mxConstants.STYLE_DASHED, false);
            stylesheet.putCellStyle("object", objectStyle);

            // 生命线（Lifeline）- 时序图中的虚线
            Map<String, Object> lifelineStyle = new HashMap<>();
            lifelineStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_LINE);
            lifelineStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter);
            lifelineStyle.put(mxConstants.STYLE_STROKECOLOR, "#666666");
            lifelineStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            lifelineStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            lifelineStyle.put(mxConstants.STYLE_DASHED, true);
            lifelineStyle.put(mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_TOP);
            stylesheet.putCellStyle("lifeline", lifelineStyle);

            // 激活（Activation）- 时序图中的执行块
            Map<String, Object> activationStyle = new HashMap<>();
            activationStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
            activationStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter);
            activationStyle.put(mxConstants.STYLE_FILLCOLOR, "#E1D5E7");
            activationStyle.put(mxConstants.STYLE_STROKECOLOR, "#9673A6");
            activationStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            activationStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            stylesheet.putCellStyle("activation", activationStyle);

            // 自调用（Self-call）- 激活块上的小框
            Map<String, Object> selfCallStyle = new HashMap<>();
            selfCallStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
            selfCallStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter);
            selfCallStyle.put(mxConstants.STYLE_FILLCOLOR, "#F8CECC");
            selfCallStyle.put(mxConstants.STYLE_STROKECOLOR, "#B85450");
            selfCallStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            selfCallStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            stylesheet.putCellStyle("selfCall", selfCallStyle);

            // 对象创建（Create）- 虚线上的对象
            Map<String, Object> createObjectStyle = new HashMap<>();
            createObjectStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
            createObjectStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter);
            createObjectStyle.put(mxConstants.STYLE_FILLCOLOR, "#D5E8D4");
            createObjectStyle.put(mxConstants.STYLE_STROKECOLOR, "#82B366");
            createObjectStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            createObjectStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            createObjectStyle.put(mxConstants.STYLE_DASHED, false);
            stylesheet.putCellStyle("createObject", createObjectStyle);

            // 破坏（Destroy）- X标记
            Map<String, Object> destroyStyle = new HashMap<>();
            destroyStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
            destroyStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter);
            destroyStyle.put(mxConstants.STYLE_FILLCOLOR, "#F8CECC");
            destroyStyle.put(mxConstants.STYLE_STROKECOLOR, "#B85450");
            destroyStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            destroyStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
            destroyStyle.put(mxConstants.STYLE_DASHED, false);
            stylesheet.putCellStyle("destroy", destroyStyle);

            // 注释框（Note）- 时序图中的注释
            Map<String, Object> sequenceNoteStyle = new HashMap<>();
            sequenceNoteStyle.put(mxConstants.STYLE_SHAPE, "note");
            sequenceNoteStyle.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter);
            sequenceNoteStyle.put(mxConstants.STYLE_FILLCOLOR, "#FFF4C3");
            sequenceNoteStyle.put(mxConstants.STYLE_STROKECOLOR, "#D9A741");
            sequenceNoteStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            sequenceNoteStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            stylesheet.putCellStyle("sequenceNote", sequenceNoteStyle);

            // ================= 连线样式 =================

            // 默认边样式（Default Edge）
            Map<String, Object> defaultEdgeStyle = new HashMap<>();
            defaultEdgeStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_CONNECTOR);
            defaultEdgeStyle.put(mxConstants.STYLE_STROKECOLOR, "#6482B9");
            defaultEdgeStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
            defaultEdgeStyle.put(mxConstants.STYLE_FONTCOLOR, "#446299");
            defaultEdgeStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            defaultEdgeStyle.put(mxConstants.STYLE_ROUNDED, true);
            stylesheet.putCellStyle("defaultEdge", defaultEdgeStyle);

            // 实线箭头（Solid Arrow）
            Map<String, Object> solidArrowStyle = new HashMap<>();
            solidArrowStyle.put(mxConstants.STYLE_STROKECOLOR, "#6482B9");
            solidArrowStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
            solidArrowStyle.put(mxConstants.STYLE_FONTCOLOR, "#446299");
            solidArrowStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            solidArrowStyle.put(mxConstants.STYLE_ROUNDED, true);
            stylesheet.putCellStyle("solidArrow", solidArrowStyle);

            // 虚线箭头（Dashed Arrow）
            Map<String, Object> dashedArrowStyle = new HashMap<>();
            dashedArrowStyle.put(mxConstants.STYLE_STROKECOLOR, "#6482B9");
            dashedArrowStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
            dashedArrowStyle.put(mxConstants.STYLE_FONTCOLOR, "#446299");
            dashedArrowStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            dashedArrowStyle.put(mxConstants.STYLE_DASHED, true);
            dashedArrowStyle.put(mxConstants.STYLE_DASH_PATTERN, "3 3");
            dashedArrowStyle.put(mxConstants.STYLE_ROUNDED, true);
            stylesheet.putCellStyle("dashedArrow", dashedArrowStyle);

            // 无箭头连线（No Arrow）
            Map<String, Object> noArrowStyle = new HashMap<>();
            noArrowStyle.put(mxConstants.STYLE_STROKECOLOR, "#6482B9");
            noArrowStyle.put(mxConstants.STYLE_FONTCOLOR, "#446299");
            noArrowStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            noArrowStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.NONE);
            noArrowStyle.put(mxConstants.STYLE_ROUNDED, true);
            stylesheet.putCellStyle("noArrow", noArrowStyle);

            // 双向箭头（Bidirectional Arrow）
            Map<String, Object> bidirectionalArrowStyle = new HashMap<>();
            bidirectionalArrowStyle.put(mxConstants.STYLE_STROKECOLOR, "#6482B9");
            bidirectionalArrowStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
            bidirectionalArrowStyle.put(mxConstants.STYLE_STARTARROW, mxConstants.ARROW_CLASSIC);
            bidirectionalArrowStyle.put(mxConstants.STYLE_FONTCOLOR, "#446299");
            bidirectionalArrowStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            bidirectionalArrowStyle.put(mxConstants.STYLE_ROUNDED, true);
            stylesheet.putCellStyle("bidirectionalArrow", bidirectionalArrowStyle);

            // 时序图调用箭头（Sequence Call）
            Map<String, Object> sequenceCallStyle = new HashMap<>();
            sequenceCallStyle.put(mxConstants.STYLE_STROKECOLOR, "#000000");
            sequenceCallStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_BLOCK);
            sequenceCallStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            sequenceCallStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            sequenceCallStyle.put(mxConstants.STYLE_ROUNDED, false);
            sequenceCallStyle.put(mxConstants.STYLE_DASHED, false);
            stylesheet.putCellStyle("sequenceCall", sequenceCallStyle);

            // 时序图返回箭头（Sequence Return）
            Map<String, Object> sequenceReturnStyle = new HashMap<>();
            sequenceReturnStyle.put(mxConstants.STYLE_STROKECOLOR, "#999999");
            sequenceReturnStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_OPEN);
            sequenceReturnStyle.put(mxConstants.STYLE_FONTCOLOR, "#999999");
            sequenceReturnStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            sequenceReturnStyle.put(mxConstants.STYLE_ROUNDED, false);
            sequenceReturnStyle.put(mxConstants.STYLE_DASHED, true);
            sequenceReturnStyle.put(mxConstants.STYLE_DASH_PATTERN, "2 2");
            stylesheet.putCellStyle("sequenceReturn", sequenceReturnStyle);

            // 时序图创建箭头（Sequence Create）
            Map<String, Object> sequenceCreateStyle = new HashMap<>();
            sequenceCreateStyle.put(mxConstants.STYLE_STROKECOLOR, "#82B366");
            sequenceCreateStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_BLOCK);
            sequenceCreateStyle.put(mxConstants.STYLE_FONTCOLOR, "#82B366");
            sequenceCreateStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            sequenceCreateStyle.put(mxConstants.STYLE_ROUNDED, false);
            sequenceCreateStyle.put(mxConstants.STYLE_DASHED, true);
            stylesheet.putCellStyle("sequenceCreate", sequenceCreateStyle);

            // 时序图销毁箭头（Sequence Destroy）
            Map<String, Object> sequenceDestroyStyle = new HashMap<>();
            sequenceDestroyStyle.put(mxConstants.STYLE_STROKECOLOR, "#B85450");
            sequenceDestroyStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_BLOCK);
            sequenceDestroyStyle.put(mxConstants.STYLE_FONTCOLOR, "#B85450");
            sequenceDestroyStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            sequenceDestroyStyle.put(mxConstants.STYLE_ROUNDED, false);
            sequenceDestroyStyle.put(mxConstants.STYLE_DASHED, false);
            stylesheet.putCellStyle("sequenceDestroy", sequenceDestroyStyle);

            // 自调用箭头（Self Call Arrow）
            Map<String, Object> selfCallArrowStyle = new HashMap<>();
            selfCallArrowStyle.put(mxConstants.STYLE_STROKECOLOR, "#000000");
            selfCallArrowStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_BLOCK);
            selfCallArrowStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
            selfCallArrowStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            selfCallArrowStyle.put(mxConstants.STYLE_ROUNDED, true);
            selfCallArrowStyle.put(mxConstants.STYLE_EDGE, mxConstants.EDGESTYLE_ENTITY_RELATION);
            stylesheet.putCellStyle("selfCallArrow", selfCallArrowStyle);

            // 条件流（Yes/No）- 流程图中的条件流
            Map<String, Object> conditionFlowStyle = new HashMap<>();
            conditionFlowStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_CONNECTOR);
            conditionFlowStyle.put(mxConstants.STYLE_STROKECOLOR, "#82B366");
            conditionFlowStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
            conditionFlowStyle.put(mxConstants.STYLE_FONTCOLOR, "#82B366");
            conditionFlowStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            conditionFlowStyle.put(mxConstants.STYLE_ROUNDED, true);
            conditionFlowStyle.put(mxConstants.STYLE_FONTSIZE, 12);
            conditionFlowStyle.put(mxConstants.STYLE_FONTSTYLE, 1); // 粗体
            stylesheet.putCellStyle("conditionFlow", conditionFlowStyle);

            // 异常流（Exception）- 流程图中的异常流
            Map<String, Object> exceptionFlowStyle = new HashMap<>();
            exceptionFlowStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_CONNECTOR);
            exceptionFlowStyle.put(mxConstants.STYLE_STROKECOLOR, "#B85450");
            exceptionFlowStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
            exceptionFlowStyle.put(mxConstants.STYLE_FONTCOLOR, "#B85450");
            exceptionFlowStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
            exceptionFlowStyle.put(mxConstants.STYLE_ROUNDED, true);
            exceptionFlowStyle.put(mxConstants.STYLE_DASHED, true);
            stylesheet.putCellStyle("exceptionFlow", exceptionFlowStyle);
        }

        private static void applyStyles(mxGraph graph, JGraphXAdapter<FlowGraphData.Node, DefaultEdge> graphAdapter,
                                        FlowGraphData graphData) {
            Object parent = graph.getDefaultParent();
            List<FlowGraphData.Node> nodes = graphData.getNodes();
            List<FlowGraphData.Edge> edges = graphData.getEdges();

            // 设置图形布局参数
            mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
            layout.setInterRankCellSpacing(80.0);  // 垂直间距
            layout.setInterHierarchySpacing(60.0); // 水平间距
            layout.setParallelEdgeSpacing(15.0);   // 平行边间距
            layout.setOrientation(SwingConstants.NORTH); // 方向：从上到下

            // 应用节点样式和几何形状
            for (FlowGraphData.Node node : nodes) {
                Object cell = graphAdapter.getVertexToCellMap().get(node);
                if (cell != null) {
                    // 设置节点几何形状
                    mxGeometry geometry = createNodeGeometry(node);
                    graph.getModel().setGeometry(cell, geometry);

                    // 应用节点样式
                    applyCellStyle(graph, cell, node);
                }
            }

            // 应用边的样式
            applyEdgeStyles(graph, graphAdapter, edges);

            // 执行布局
            layout.execute(graph.getDefaultParent());

            // 自动调整视图大小
            mxRectangle bounds = graph.getGraphBounds();
            double width = bounds.getWidth() + 50;
            double height = bounds.getHeight() + 50;
            graph.getModel().setGeometry(graph.getDefaultParent(),
                    new mxGeometry(0, 0, width, height));
        }

        private static mxGeometry createNodeGeometry(FlowGraphData.Node node) {
            mxGeometry geometry;
            if ("condition".equals(node.getType())) {
                geometry = new mxGeometry(0, 0, 160, 80);
            } else if ("start".equals(node.getType()) || "end".equals(node.getType())) {
                geometry = new mxGeometry(0, 0, 120, 50);
            } else {
                geometry = new mxGeometry(0, 0, 180, 60);
            }
            return geometry;
        }

        private static void applyCellStyle(mxGraph graph, Object cell, FlowGraphData.Node node) {
            StringBuilder style = new StringBuilder();

            // 基础样式
            style.append(node.getType())
                    .append(";rounded=1;shadow=1;glass=1;")
                    .append("fontSize=12;spacing=10;");

            // 根据节点类型设置特定样式
            switch (node.getType()) {
                case "start" -> style.append("fillColor=#dae8fc;strokeColor=#6c8ebf;");
                case "end" -> style.append("fillColor=#f8cecc;strokeColor=#b85450;");
                case "condition" -> style.append("fillColor=#fff2cc;strokeColor=#d6b656;shape=rhombus;");
                case "process" -> style.append("fillColor=#d5e8d4;strokeColor=#82b366;");
                default -> style.append("fillColor=#e1d5e7;strokeColor=#9673a6;");
            }

            graph.setCellStyle(style.toString(), new Object[]{cell});
        }

        private static void applyEdgeStyles(mxGraph graph, JGraphXAdapter<FlowGraphData.Node, DefaultEdge> graphAdapter,
                                            List<FlowGraphData.Edge> edges) {
            for (FlowGraphData.Edge edge : edges) {
                Object cell = graphAdapter.getEdgeToCellMap().get(edge);
                if (cell != null) {
                    StringBuilder style = new StringBuilder();

                    // 基础边样式
                    style.append("edgeStyle=orthogonalEdgeStyle;rounded=1;orthogonalLoop=1;")
                            .append("strokeWidth=1.5;fontSize=11;");

                    // 根据边类型设置特定样式
                    switch (edge.getType()) {
                        case "condition" -> {
                            style.append("strokeColor=#82B366;")
                                    .append("fontColor=#82B366;")
                                    .append("fontStyle=1;");
                        }
                        case "exception" -> {
                            style.append("strokeColor=#B85450;")
                                    .append("fontColor=#B85450;")
                                    .append("dashed=1;dashPattern=3 3;");
                        }
                        default -> {
                            style.append("strokeColor=#6482B9;")
                                    .append("fontColor=#446299;");
                        }
                    }

                    graph.setCellStyle(style.toString(), new Object[]{cell});
                }
            }
        }


        /**
         * 计算节点位置
         */
        private void layoutGraph() {

            // 简单的层次布局
            Map<FlowGraphData.Node, Integer> levels = new HashMap<>();
            Map<Integer, Integer> levelCounts = new HashMap<>();

            // 计算每个节点的层级
            for (FlowGraphData.Node node : graph.vertexSet()) {
                int level = calculateLevel(node);
                levels.put(node, level);
                levelCounts.put(level, levelCounts.getOrDefault(level, 0) + 1);
            }

            // 计算每个节点的位置
            int nodeWidth = 150;
            int nodeHeight = 50;
            int horizontalGap = 50;
            int verticalGap = 100;

            Map<Integer, Integer> currentPositions = new HashMap<>();

            for (FlowGraphData.Node node : graph.vertexSet()) {
                int level = levels.get(node);
                int count = levelCounts.get(level);
                int position = currentPositions.getOrDefault(level, 0);

                double x = (position * (nodeWidth + horizontalGap)) +
                        ((800 - count * (nodeWidth + horizontalGap)) / 2);
                double y = level * (nodeHeight + verticalGap) + 50;

                nodePositions.put(node, new Rectangle2D.Double(x, y, nodeWidth, nodeHeight));
                currentPositions.put(level, position + 1);
            }
        }

        /**
         * 计算节点的层级
         */
        private int calculateLevel(FlowGraphData.Node node) {
            return calculateLevel(node, new HashSet<>());
        }

        /**
         * 计算节点的层级（带访问记录）
         *
         * @param node    当前节点
         * @param visited 已访问的节点集合
         * @return 节点层级
         */
        private int calculateLevel(FlowGraphData.Node node, Set<FlowGraphData.Node> visited) {
            // 如果节点已被访问，说明存在循环，返回当前深度
            if (!visited.add(node)) {
                return visited.size() - 1;
            }

            // 如果没有入边，则为第一层
            if (graph.inDegreeOf(node) == 0) {
                visited.remove(node);
                return 0;
            }

            // 否则，取所有前驱节点的最大层级 + 1
            int maxLevel = 0;
            for (DefaultEdge edge : graph.incomingEdgesOf(node)) {
                FlowGraphData.Node source = graph.getEdgeSource(edge);
                int sourceLevel = calculateLevel(source, new HashSet<>(visited));
                maxLevel = Math.max(maxLevel, sourceLevel);
            }

            visited.remove(node);
            return maxLevel + 1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();

            // 启用抗锯齿
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 应用缩放和平移
            AffineTransform transform = new AffineTransform();
            transform.translate(offset.getX() * scale, offset.getY() * scale);
            transform.scale(scale, scale);
            g2.transform(transform);

            // 绘制边
            drawEdges(g2);

            // 绘制节点
            drawNodes(g2);

            g2.dispose();
        }

        /**
         * 绘制所有边
         */
        private void drawEdges(Graphics2D g2) {
            g2.setStroke(new BasicStroke(1.5f));

            for (DefaultEdge edge : graph.edgeSet()) {
                FlowGraphData.Node source = graph.getEdgeSource(edge);
                FlowGraphData.Node target = graph.getEdgeTarget(edge);

                Rectangle2D sourceRect = nodePositions.get(source);
                Rectangle2D targetRect = nodePositions.get(target);

                if (sourceRect != null && targetRect != null) {
                    // 检查是否是回环（自环）
                    if (source.equals(target)) {
                        drawSelfLoop(g2, source, sourceRect, findEdgeLabel(source, target));
                    } else {
                        // 计算连接点
                        Point2D sourcePoint = new Point2D.Double(
                                sourceRect.getCenterX(),
                                sourceRect.getMaxY()
                        );

                        Point2D targetPoint = new Point2D.Double(
                                targetRect.getCenterX(),
                                targetRect.getMinY()
                        );

                        // 绘制边
                        g2.setColor(Color.DARK_GRAY);
                        g2.drawLine(
                                (int) sourcePoint.getX(), (int) sourcePoint.getY(),
                                (int) targetPoint.getX(), (int) targetPoint.getY()
                        );

                        // 绘制箭头
                        drawArrow(g2, sourcePoint, targetPoint);

                        // 查找边标签
                        String label = findEdgeLabel(source, target);
                        if (label != null && !label.isEmpty()) {
                            Point2D midPoint = new Point2D.Double(
                                    (sourcePoint.getX() + targetPoint.getX()) / 2,
                                    (sourcePoint.getY() + targetPoint.getY()) / 2
                            );

                            g2.setColor(Color.BLACK);
                            g2.drawString(label, (float) midPoint.getX() + 5, (float) midPoint.getY() - 5);
                        }
                    }
                }
            }
        }

        /**
         * 绘制自环（回环）
         */
        private void drawSelfLoop(Graphics2D g2, FlowGraphData.Node node, Rectangle2D nodeRect, String label) {
            // 自环的大小和位置
            double loopWidth = nodeRect.getWidth() * 0.6;
            double loopHeight = nodeRect.getHeight() * 1.2;
            double loopX = nodeRect.getMaxX();
            double loopY = nodeRect.getMinY() - loopHeight * 0.5;

            // 绘制自环
            g2.setColor(Color.DARK_GRAY);
            g2.draw(new java.awt.geom.Arc2D.Double(
                    loopX, loopY, loopWidth, loopHeight,
                    0, 270, java.awt.geom.Arc2D.OPEN
            ));

            // 计算箭头位置
            Point2D arrowBase = new Point2D.Double(
                    loopX + loopWidth * 0.25,
                    loopY + loopHeight
            );
            Point2D arrowTip = new Point2D.Double(
                    loopX + loopWidth * 0.1,
                    loopY + loopHeight * 0.9
            );

            // 绘制箭头
            drawArrow(g2, arrowBase, arrowTip);

            // 绘制标签
            if (label != null && !label.isEmpty()) {
                g2.setColor(Color.BLACK);
                g2.drawString(label, (float) (loopX + loopWidth * 0.5), (float) (loopY + loopHeight * 0.3));
            }
        }

        /**
         * 查找边的标签
         */
        private String findEdgeLabel(FlowGraphData.Node source, FlowGraphData.Node target) {
            for (FlowGraphData.Edge edge : graphData.getEdges()) {
                if (edge.getSource().equals(source.getId()) && edge.getTarget().equals(target.getId())) {
                    return edge.getLabel();
                }
            }
            return null;
        }

        /**
         * 绘制箭头
         */
        private void drawArrow(Graphics2D g2, Point2D from, Point2D to) {
            double dx = to.getX() - from.getX();
            double dy = to.getY() - from.getY();
            double angle = Math.atan2(dy, dx);

            int arrowSize = 10;

            // 计算箭头位置
            int x1 = (int) (to.getX() - arrowSize * Math.cos(angle - Math.PI / 6));
            int y1 = (int) (to.getY() - arrowSize * Math.sin(angle - Math.PI / 6));
            int x2 = (int) (to.getX() - arrowSize * Math.cos(angle + Math.PI / 6));
            int y2 = (int) (to.getY() - arrowSize * Math.sin(angle + Math.PI / 6));

            // 绘制箭头
            g2.fillPolygon(
                    new int[]{(int) to.getX(), x1, x2},
                    new int[]{(int) to.getY(), y1, y2},
                    3
            );
        }

        /**
         * 绘制所有节点
         */
        private void drawNodes(Graphics2D g2) {
            for (FlowGraphData.Node node : graph.vertexSet()) {
                Rectangle2D bounds = nodePositions.get(node);
                if (bounds != null) {
                    // 根据节点类型选择颜色
                    Color fillColor = getNodeColor(node);
                    Shape shape = getNodeShape(node, bounds);

                    // 绘制节点背景
                    g2.setColor(fillColor);
                    g2.fill(shape);

                    // 绘制节点边框
                    g2.setColor(fillColor.darker());
                    g2.draw(shape);

                    // 绘制节点标签
                    g2.setColor(Color.BLACK);
                    drawCenteredString(g2, node.getDescription(), bounds);
                }
            }
        }

        private Shape getNodeShape(FlowGraphData.Node node, Rectangle2D bounds) {
            String type = node.getType();
            if (type == null)
                return new RoundRectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), 10, 10);

            switch (type.toLowerCase()) {
                case "condition":
                    // 菱形
                    int x = (int) bounds.getCenterX();
                    int y = (int) bounds.getCenterY();
                    int w = (int) bounds.getWidth() / 2;
                    int h = (int) bounds.getHeight() / 2;
                    return new Polygon(
                            new int[]{x, x + w, x, x - w},
                            new int[]{y - h, y, y + h, y},
                            4
                    );
                case "start":
                case "end":
                    // 实心圆或双层圆
                    return new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
                default:
                    // 默认为矩形或圆角矩形
                    return new RoundRectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), 10, 10);
            }
        }

        /**
         * 根据节点类型获取颜色
         */
        private Color getNodeColor(FlowGraphData.Node node) {
            String type = node.getType();
            if (type == null) {
                return new Color(240, 240, 240);
            }

            switch (type.toLowerCase()) {
                case "method":
                    return new Color(212, 230, 241); // 浅蓝色
                case "condition":
                    return new Color(213, 245, 227); // 浅绿色
                case "loop":
                    return new Color(252, 243, 207); // 浅黄色
                default:
                    return new Color(240, 240, 240); // 浅灰色
            }
        }

        /**
         * 绘制居中文本
         */
        private void drawCenteredString(Graphics2D g2, String text, Rectangle2D bounds) {
            if (text == null || text.isEmpty()) {
                return;
            }

            FontMetrics metrics = g2.getFontMetrics();
            int x = (int) (bounds.getX() + (bounds.getWidth() - metrics.stringWidth(text)) / 2);
            int y = (int) (bounds.getY() + ((bounds.getHeight() - metrics.getHeight()) / 2) + metrics.getAscent());

            g2.drawString(text, x, y);
        }
    }
}
