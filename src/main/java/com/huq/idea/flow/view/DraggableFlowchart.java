package com.huq.idea.flow.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class DraggableFlowchart extends JPanel {
    private Rectangle startRect;
    private Rectangle processRect;
    private Rectangle endRect;
    private Point dragOffset;
    private Rectangle draggedRect;
    private final int GRID_SIZE = 20; // 网格大小

    public DraggableFlowchart() {
        startRect = new Rectangle(50, 50, 100, 50);
        processRect = new Rectangle(50, 150, 100, 50);
        endRect = new Rectangle(50, 250, 100, 50);

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (startRect.contains(e.getPoint())) {
                    draggedRect = startRect;
                    dragOffset = e.getPoint();
                } else if (processRect.contains(e.getPoint())) {
                    draggedRect = processRect;
                    dragOffset = e.getPoint();
                } else if (endRect.contains(e.getPoint())) {
                    draggedRect = endRect;
                    dragOffset = e.getPoint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggedRect != null) {
                    int dx = e.getX() - dragOffset.x;
                    int dy = e.getY() - dragOffset.y;
                    draggedRect.setLocation(draggedRect.x + dx, draggedRect.y + dy);
                    dragOffset = e.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                draggedRect = null;
            }
        };

        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // 设置抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 绘制网格线
        drawGrid(g2d);

        // 绘制流程图形状
        drawRectangle(g2d, startRect, "开始");
        drawRectangle(g2d, processRect, "处理");
        drawRectangle(g2d, endRect, "结束");

        // 绘制箭头
        drawArrow(g2d, startRect.x + startRect.width / 2, startRect.y + startRect.height,
                processRect.x + processRect.width / 2, processRect.y);
        drawArrow(g2d, processRect.x + processRect.width / 2, processRect.y + processRect.height,
                endRect.x + endRect.width / 2, endRect.y);
    }

    private void drawGrid(Graphics2D g2d) {
        g2d.setColor(Color.LIGHT_GRAY);
        for (int i = 0; i < getWidth(); i += GRID_SIZE) {
            g2d.drawLine(i, 0, i, getHeight());
            g2d.drawLine(0, i, getWidth(), i);
        }
    }

    private void drawRectangle(Graphics2D g2d, Rectangle rect, String text) {
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 15, 15);
        g2d.setColor(Color.BLACK);
        g2d.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 15, 15);
        g2d.drawString(text, rect.x + 10, rect.y + 30);
    }

    private void drawArrow(Graphics2D g2d, int x1, int y1, int x2, int y2) {
        g2d.setColor(Color.BLACK);
        g2d.drawLine(x1, y1, x2, y2);

        // 绘制箭头
        int arrowSize = 10;
        double angle = Math.atan2(y2 - y1, x2 - x1);
        g2d.fillPolygon(new int[]{
                        x2,
                        x2 - (int) (arrowSize * Math.cos(angle - Math.PI / 6)),
                        x2 - (int) (arrowSize * Math.cos(angle + Math.PI / 6))
                },
                new int[]{
                        y2,
                        y2 - (int) (arrowSize * Math.sin(angle - Math.PI / 6)),
                        y2 - (int) (arrowSize * Math.sin(angle + Math.PI / 6))
                },
                3
        );
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("可拖拽流程图示例");
        DraggableFlowchart panel = new DraggableFlowchart();

        frame.add(panel);
        frame.setSize(400, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}