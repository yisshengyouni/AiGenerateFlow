package com.huq.idea.flow.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class MultiProcessFlowchart extends JPanel {
    private List<FlowNode> mainProcesses;
    private FlowNode draggedNode;
    private Point dragOffset;

    public MultiProcessFlowchart(List<FlowNode> mainProcesses) {
        this.mainProcesses = mainProcesses;
//        createFlowchart();

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                for (FlowNode node : mainProcesses) {
                    if (node.getBounds().contains(e.getPoint())) {
                        if (SwingUtilities.isLeftMouseButton(e)) {
                            // 切换折叠/展开状态
                            node.toggleCollapse();
                            repaint();
                        } else {
                            // 开始拖拽
                            draggedNode = node;
                            dragOffset = e.getPoint();
                        }
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggedNode != null) {
                    int dx = e.getX() - dragOffset.x;
                    int dy = e.getY() - dragOffset.y;
                    draggedNode.move(dx, dy);
                    dragOffset = e.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                draggedNode = null;
            }
        };

        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    private void createFlowchart() {
        // 创建多个主流程
        FlowNode mainProcess1 = new FlowNode("创建订单流程", 50, 50);
        mainProcesses.add(mainProcess1);
        createSubProcesses(mainProcess1, new String[]{"子流程1-1", "子流程1-2", "子流程1-3", "子流程1-4"});

        FlowNode mainProcess2 = new FlowNode("主流程2", 400, 50);
        mainProcesses.add(mainProcess2);
        createSubProcesses(mainProcess2, new String[]{"子流程2-1", "子流程2-2"});
    }

    private void createSubProcesses(FlowNode parent, String[] subProcessNames) {
        int startY = parent.getBounds().y;
        int startX = parent.getBounds().x + 200;
        for (int i = 0; i < subProcessNames.length; i++) {
            FlowNode step = new FlowNode(subProcessNames[i], startX, startY);
            parent.addChild(step);
            startY += 60;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // 设置抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 绘制主流程和连接线
        int startX = 100;
        int startY = 100;
        for (FlowNode node : mainProcesses) {
            node.getBounds().x = startX;
            node.getBounds().y = startY;
            node.draw(g2d);
            startY += 80;
            drawConnection(g2d, node);
        }
    }


    // 采用递归的方式，绘制两个流程之间的连接线
    private void drawConnection(Graphics2D g2d, FlowNode parent) {
        g2d.setColor(Color.BLACK);
        // 绘制带箭头的连线
        g2d.setStroke(new BasicStroke(2));


        if (!parent.hasChildren() || parent.isCollapsed()) {
            return;
        }
        int startX = parent.getBounds().x + parent.getBounds().width;
        int startY = parent.getBounds().y + parent.getBounds().height / 2;
        FlowNode lastChild = null;
        for (FlowNode child : parent.getChildren()) {
            if (lastChild == null) {
                child.getBounds().x = parent.getBounds().x + 150;
                child.getBounds().y = parent.getBounds().y;

                g2d.drawLine(startX, startY,
                        child.getBounds().x,
                        child.getBounds().y + child.getBounds().height / 2);
            } else {
                startX = lastChild.getBounds().x + child.getBounds().width/2;
                startY = lastChild.getBounds().y + lastChild.getBounds().height;

                child.getBounds().x = lastChild.getBounds().x;
                child.getBounds().y = lastChild.getBounds().y + 80;

                g2d.drawLine(startX, startY,
                        child.getBounds().x + child.getBounds().width/2,
                        child.getBounds().y);
            }
            lastChild = child;
            drawConnection(g2d, child);
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("多流程展示示例");
        MultiProcessFlowchart panel = new MultiProcessFlowchart(new ArrayList<>());

        frame.add(panel);
        frame.setSize(500, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

}