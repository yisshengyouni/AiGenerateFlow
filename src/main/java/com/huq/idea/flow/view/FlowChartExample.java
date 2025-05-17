package com.huq.idea.flow.view;

import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxIGraphModel;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxEvent;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxEventSource;
import com.mxgraph.view.mxGraph;
import org.jgrapht.Graphs;

import javax.swing.*;

public class FlowChartExample extends JFrame {
    public FlowChartExample() {
        mxGraph graph = new mxGraph() {
            @Override
            public boolean isCellFoldable(Object cell, boolean collapse) {
                return cell instanceof mxCell && getModel().isVertex(cell);
            }
        };

        Object parent = graph.getDefaultParent();

        graph.getModel().beginUpdate();
        try {
            // 创建父流程
            Object parentProcess = graph.insertVertex(parent, null, "Parent Process", 20, 20, 120, 60, "shape=ellipse");

            // 创建子流程
            Object subProcess = graph.insertVertex(parent, null, "Sub Process", 20, 120, 120, 60, "shape=ellipse");

            // 创建子流程的子节点
            Object subStep1 = graph.insertVertex(subProcess, null, "Sub Step 1", 40, 40, 80, 30);
            Object subStep2 = graph.insertVertex(subProcess, null, "Sub Step 2", 40, 90, 80, 30);

            // 创建父流程的子节点
            Object step1 = graph.insertVertex(parentProcess, null, "Step 1", 40, 40, 80, 30);
            Object step2 = graph.insertVertex(parentProcess, null, "Step 2", 40, 90, 80, 30);

            // 创建边
            graph.insertEdge(parent, null, "Edge 1", step1, step2);
            graph.insertEdge(parent, null, "Edge 2", step2, subProcess);
            graph.insertEdge(subProcess, null, "Edge 3", subStep1, subStep2);

            final mxIGraphModel model = graph.getModel();
            model.setCollapsed(subProcess, true);

            // 设置折叠/展开功能
            graph.addListener(mxEvent.FOLD_CELLS, new mxEventSource.mxIEventListener() {
                @Override
                public void invoke(Object sender, mxEventObject evt) {
                    Object[] cells = (Object[]) evt.getProperty("cells");
                    boolean collapse = (boolean) evt.getProperty("collapse");
                    for (Object cell : cells) {
                        if (collapse) {
                            System.out.println("Collapsing: " + graph.getLabel(cell));
                        } else {
                            System.out.println("Expanding: " + graph.getLabel(cell));
                        }
                    }
                }
            });
        } finally {
            graph.getModel().endUpdate();
        }

        mxGraphComponent graphComponent = new mxGraphComponent(graph);
        getContentPane().add(graphComponent);
    }

    public static void main(String[] args) {
        // 创建并显示 JFrame
        FlowChartExample frame = new FlowChartExample();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);
        frame.setVisible(true);
    }
}