package com.huq.idea.flow.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程图数据模型，用于与AI生成的JSON数据进行交互
 *
 * @author huqiang
 * @since 2024/8/10
 */
public class FlowGraphData {

    @SerializedName("nodes")
    private List<Node> nodes = new ArrayList<>();

    @SerializedName("edges")
    private List<Edge> edges = new ArrayList<>();

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }

    /**
     * 从JSON字符串解析流程图数据
     */
    public static FlowGraphData fromJson(String json) {
        try {
            Gson gson = new Gson();
            return gson.fromJson(json, FlowGraphData.class);
        } catch (Exception e) {
            e.printStackTrace();
            // 如果解析失败，返回一个空的数据对象
            return new FlowGraphData();
        }
    }

    /**
     * 节点数据模型
     */
    public static class Node {
        @SerializedName("id")
        private String id;

        @SerializedName("label")
        private String label;

        @SerializedName("type")
        private String type;

        @SerializedName("className")
        private String className;

        @SerializedName("methodName")
        private String methodName;

        @SerializedName("description")
        private String description;

        @SerializedName("code")
        private String code;

        @SerializedName("lineNumber")
        private int lineNumber;

        @SerializedName("filePath")
        private String filePath;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
    }

    /**
     * 边数据模型
     */
    public static class Edge {
        @SerializedName("source")
        private String source;

        @SerializedName("target")
        private String target;

        @SerializedName("label")
        private String label;

        @SerializedName("type")
        private String type;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
