package com.huq.idea.flow.view;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author huqiang
 * @since 2024/10/15 14:56
 */
public class FlowNode {
    private String name;
    private Rectangle bounds;
    private java.util.List<FlowNode> children;
    private boolean collapsed;

    public FlowNode(String name, int x, int y) {
        this.name = name;
        this.bounds = new Rectangle(x, y, 120, 50);
        this.children = new ArrayList<>();
        this.collapsed = false; // 默认折叠状态
    }

    public void addChild(FlowNode child) {
        children.add(child);
    }

    public List<FlowNode> getChildren() {
        return children;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void toggleCollapse() {
        collapsed = !collapsed;
    }

    public Rectangle getBounds() {
        return bounds;
    }

    public void move(int dx, int dy) {
        bounds.setLocation(bounds.x + dx, bounds.y + dy);
        if (!collapsed) {
            for (FlowNode child : children) {
                child.move(dx, dy);
            }
        }
    }

    public void draw(Graphics2D g2d) {
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 15, 15);
        g2d.setColor(Color.BLACK);
        g2d.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 15, 15);
        g2d.drawString(name, bounds.x + 10, bounds.y + 30);

        // 绘制子节点
        if (!collapsed) {
            for (FlowNode child : children) {
                child.draw(g2d);
            }
        }
    }
}
