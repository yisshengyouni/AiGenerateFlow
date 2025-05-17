package com.huq.idea.flow.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.mxgraph.swing.handler.mxRubberband;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxEvent;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxEventSource;
import com.mxgraph.view.mxGraph;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;

/**
 * 增强的JGraphX面板，支持缩放、平移和导出功能
 *
 * @author huqiang
 * @since 2024/8/10
 */
public class JGraphXPanel extends JPanel {
    private static final Logger LOG = Logger.getInstance(JGraphXPanel.class);
    
    private final mxGraphComponent graphComponent;
    private final JToolBar toolBar;
    private double zoomFactor = 1.0;
    
    /**
     * 创建一个增强的JGraphX面板
     *
     * @param graphComponent JGraphX图形组件
     */
    public JGraphXPanel(mxGraphComponent graphComponent) {
        super(new BorderLayout());
        this.graphComponent = graphComponent;
        
        // 创建工具栏
        this.toolBar = createToolBar();
        
        // 启用橡皮筋选择
        new mxRubberband(graphComponent);
        
        // 添加鼠标滚轮缩放
        graphComponent.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.isControlDown()) {
                    if (e.getWheelRotation() < 0) {
                        zoomIn();
                    } else {
                        zoomOut();
                    }
                    e.consume();
                }
            }
        });
        
        // 添加组件到面板
        add(toolBar, BorderLayout.NORTH);
        add(graphComponent, BorderLayout.CENTER);
        
        // 添加状态栏
        JPanel statusBar = createStatusBar();
        add(statusBar, BorderLayout.SOUTH);
    }
    
    /**
     * 创建工具栏
     */
    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        
        // 缩放按钮
        JButton zoomInButton = new JButton("放大");
        zoomInButton.addActionListener(e -> zoomIn());
        toolBar.add(zoomInButton);
        
        JButton zoomOutButton = new JButton("缩小");
        zoomOutButton.addActionListener(e -> zoomOut());
        toolBar.add(zoomOutButton);
        
        JButton zoomActualButton = new JButton("实际大小");
        zoomActualButton.addActionListener(e -> zoomActual());
        toolBar.add(zoomActualButton);
        
        JButton zoomFitButton = new JButton("适应窗口");
        zoomFitButton.addActionListener(e -> zoomToFit());
        toolBar.add(zoomFitButton);
        
        toolBar.addSeparator();
        
        // 导出按钮
        JButton exportButton = new JButton("导出为PNG");
        exportButton.addActionListener(this::exportToPng);
        toolBar.add(exportButton);
        
        return toolBar;
    }
    
    /**
     * 创建状态栏
     */
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        
        JLabel statusLabel = new JLabel("就绪");
        statusBar.add(statusLabel, BorderLayout.WEST);
        
        JLabel zoomLabel = new JLabel("缩放: 100%");
        statusBar.add(zoomLabel, BorderLayout.EAST);
        
        // 监听缩放变化
        graphComponent.getGraph().getView().addListener(mxEvent.SCALE, new mxEventSource.mxIEventListener() {
            @Override
            public void invoke(Object sender, mxEventObject evt) {
                double scale = graphComponent.getGraph().getView().getScale();
                zoomFactor = scale;
                zoomLabel.setText(String.format("缩放: %.0f%%", scale * 100));
            }
        });
        
        return statusBar;
    }
    
    /**
     * 放大图形
     */
    public void zoomIn() {
        graphComponent.zoomIn();
    }
    
    /**
     * 缩小图形
     */
    public void zoomOut() {
        graphComponent.zoomOut();
    }
    
    /**
     * 恢复到实际大小
     */
    public void zoomActual() {
        graphComponent.zoomActual();
    }
    
    /**
     * 缩放以适应窗口
     */
    public void zoomToFit() {
        mxGraph graph = graphComponent.getGraph();
        Rectangle bounds = graphComponent.getViewport().getViewRect();
        
        double scaleX = bounds.getWidth() / graph.getGraphBounds().getWidth();
        double scaleY = bounds.getHeight() / graph.getGraphBounds().getHeight();
        
        // 使用较小的缩放因子，确保整个图形可见
        double scale = Math.min(scaleX, scaleY) * 0.9;
        
        graphComponent.zoom(scale);
    }
    
    /**
     * 导出为PNG图像
     */
    private void exportToPng(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("导出为PNG");
        fileChooser.setSelectedFile(new java.io.File("flow_diagram.png"));
        
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            java.io.File fileToSave = fileChooser.getSelectedFile();
            
            try {
                // 获取图形的边界
                mxGraph graph = graphComponent.getGraph();
                Rectangle bounds = graph.getGraphBounds().getRectangle();
                // 创建缓冲图像
                Dimension size = graph.getGraphBounds().getRectangle().getSize();
                BufferedImage image = new BufferedImage(
                        (int) size.getWidth() + 10,
                        (int) size.getHeight() + 10,
                        BufferedImage.TYPE_INT_RGB);

                // 添加边距
                int padding = 20;
                int width = Math.max(1, (int) bounds.getWidth() + (2 * padding));
                int height = Math.max(1, (int) bounds.getHeight() + (2 * padding));


                // 创建图形上下文
                Graphics2D g2 = image.createGraphics();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_QUALITY);

                    // 填充白色背景
                    g2.setColor(Color.WHITE);
                    g2.fillRect(0, 0, width, height);

                    // 平移图形以适应边距
                    g2.translate(padding - bounds.x, padding - bounds.y);

                    // 使用正确的方法渲染图形
                    graphComponent.getGraphControl().paint(g2);

                } finally {
                    g2.dispose();
                }
                
                // 保存图像
                javax.imageio.ImageIO.write(image, "png", fileToSave);
                
                JOptionPane.showMessageDialog(this,
                        "图形已成功导出到: " + fileToSave.getAbsolutePath(),
                        "导出成功",
                        JOptionPane.INFORMATION_MESSAGE);
                
            } catch (Exception ex) {
                LOG.error("Failed to export diagram to PNG", ex);
                JOptionPane.showMessageDialog(this,
                        "导出失败: " + ex.getMessage(),
                        "导出错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * 获取图形组件
     */
    public mxGraphComponent getGraphComponent() {
        return graphComponent;
    }
}
