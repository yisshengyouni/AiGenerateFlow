package com.huq.idea.flow.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CollapsibleFlowchart extends JPanel {
    private boolean isCollapsed = false;

    public CollapsibleFlowchart() {
        JButton toggleButton = new JButton("折叠/展开");
        toggleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                isCollapsed = !isCollapsed;
                revalidate();
                repaint();
            }
        });

        setLayout(new BorderLayout());
        add(toggleButton, BorderLayout.NORTH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // 设置抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 绘制流程图形状
        if (!isCollapsed) {
            drawRectangle(g2d, "开始", 50, 50);
            drawRectangle(g2d, "处理", 50, 150);
            drawRectangle(g2d, "结束", 50, 250);

            // 绘制箭头
            drawArrow(g2d, 100, 50, 100, 150); // 从 "开始" 到 "处理"
            drawArrow(g2d, 100, 150, 100, 250); // 从 "处理" 到 "结束"
        } else {
            drawRectangle(g2d, "流程图已折叠", 50, 50);
        }
    }

    private void drawRectangle(Graphics2D g2d, String text, int x, int y) {
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRoundRect(x, y, 100, 50, 15, 15);
        g2d.setColor(Color.BLACK);
        g2d.drawRoundRect(x, y, 100, 50, 15, 15);
        g2d.drawString(text, x + 10, y + 30);
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
        JFrame frame = new JFrame("可折叠流程图示例");
        CollapsibleFlowchart panel = new CollapsibleFlowchart();

        frame.add(panel);
        frame.setSize(300, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}