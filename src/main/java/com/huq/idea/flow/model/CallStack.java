package com.huq.idea.flow.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * @author huqiang
 * @since 2024/7/14 14:49
 */
public class CallStack {

    private CallStack parent;
    private final List<CallStack> children = new ArrayList<>();
    private MethodDescription methodDescription;
    // 方法调用的元数据（可扩展）
    private final Map<String, Object> metaData = new LinkedHashMap<>();
    // 记录方法调用深度
    private int depth;
    // 标记递归调用
    private boolean recursive;

    private int currentOffset;

    public CallStack() {}

    public CallStack(MethodDescription method) {
        this.methodDescription = method;
    }

    public CallStack(MethodDescription method, CallStack parent) {
        this.methodDescription = method;
        this.parent = parent;
        if (parent != null) {
            this.depth = parent.depth + 1;
        } else {
            this.depth = 0;
        }
    }

    public int getCurrentOffset() {
        return this.currentOffset;
    }

    public void setCurrentOffset(int currentOffset) {
        this.currentOffset = currentOffset;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public Map<String, Object> getMetaData() {
        return metaData;
    }

    public CallStack getParent() {
        return parent;
    }

    public void setParent(CallStack parent) {
        this.parent = parent;
    }

    public List<CallStack> getChildren() {
        return children;
    }

    public void addChild(CallStack child) {
        children.add(child);
    }

    public MethodDescription getMethodDescription() {
        return methodDescription;
    }

    public void setMethodDescription(MethodDescription methodDescription) {
        this.methodDescription = methodDescription;
    }

    /**
     * 判断当前调用栈是否递归调用了指定方法
     */
    public boolean isRecursive(MethodDescription method) {
        CallStack current = this;
        while (current != null) {
            if (current.methodDescription != null && current.methodDescription.equals(method))
                return true;
            current = current.parent;
        }
        return false;
    }

    /**
     * 新增方法调用节点
     */
    public CallStack methodCall(MethodDescription method) {
        CallStack callStack = new CallStack(method, this);
        if (isRecursive(method)) {
            callStack.setRecursive(true);
        }
        addChild(callStack);
        return callStack;
    }

    /**
     * 生成PlantUML格式的时序图文本，包含标题和样式
     */
    public String generateUml() {
        StringBuilder uml = new StringBuilder();
        uml.append("@startuml\n");

        // 添加标题
        if (methodDescription != null) {
            uml.append("title 方法调用链: ")
               .append(methodDescription.getClassName())
               .append(".")
               .append(methodDescription.getName())
               .append("\n\n");
        }

        // 添加样式设置
        uml.append("skinparam sequenceArrowThickness 2\n");
        uml.append("skinparam sequenceParticipantBorderThickness 1\n");
        uml.append("skinparam sequenceLifeLineBorderColor gray\n");
        uml.append("skinparam sequenceLifeLineBackgroundColor white\n");
        uml.append("skinparam sequenceParticipantBackgroundColor #FEFECE\n");
        uml.append("skinparam sequenceParticipantFontStyle bold\n");
        uml.append("skinparam noteFontSize 12\n");
        uml.append("skinparam noteBorderThickness 1\n");
        uml.append("skinparam noteBackgroundColor #FFFFCC\n\n");

        // 收集所有参与者
        Set<String> participants = new HashSet<>();
        collectParticipants(participants);

        // 添加参与者声明
        for (String participant : participants) {
            // 为不同类型的参与者设置不同的样式
            if (participant.contains("Service") || participant.contains("Manager")) {
                uml.append("participant \"").append(participant).append("\" as ").append(participant)
                   .append(" #LightBlue\n");
            } else if (participant.contains("Controller") || participant.contains("Api")) {
                uml.append("participant \"").append(participant).append("\" as ").append(participant)
                   .append(" #LightGreen\n");
            } else if (participant.contains("Repository") || participant.contains("Dao")) {
                uml.append("participant \"").append(participant).append("\" as ").append(participant)
                   .append(" #LightYellow\n");
            } else if (participant.contains("Impl")) {
                uml.append("participant \"").append(participant).append("\" as ").append(participant)
                   .append(" #LightGray\n");
            } else {
                uml.append("participant \"").append(participant).append("\" as ").append(participant).append("\n");
            }
        }
        uml.append("\n");

        // 添加消息序列
        appendMessages(uml, 0);

        // 添加图例
        uml.append("\nlegend right\n");
        uml.append("  |= 类型 |= 颜色 |\n");
        uml.append("  | 服务层 | #LightBlue |\n");
        uml.append("  | 控制层/API | #LightGreen |\n");
        uml.append("  | 数据访问层 | #LightYellow |\n");
        uml.append("  | 实现类 | #LightGray |\n");
        uml.append("endlegend\n");

        uml.append("@enduml");
        return uml.toString();
    }

    /**
     * 递归收集所有涉及的类作为参与者，并添加初始调用者
     */
    private void collectParticipants(Set<String> participants) {
        // 添加一个Actor作为初始调用者（如果这是顶层调用栈）
        if (parent == null) {
            participants.add("Actor");
        }

        if (methodDescription != null) {
            String className = methodDescription.getClassName();
            participants.add(className);

            // 收集调用者（如果有）
            String caller = (String) methodDescription.getAttr("caller", "");
            if (!caller.isEmpty() && !participants.contains(caller)) {
                participants.add(caller);
            }

            // 递归收集子节点的参与者
            for (CallStack child : children) {
                child.collectParticipants(participants);
            }
        }
    }

    /**
     * 递归生成消息序列，并添加逻辑注释说明
     */
    private void appendMessages(StringBuilder uml, int indent) {
        if (methodDescription != null) {
            String caller = (String) methodDescription.getAttr("caller", "");
            String target = methodDescription.getClassName();
            String methodName = methodDescription.getName();
            String parameters = (String) methodDescription.getAttr("parameters", "");
            String returnType = methodDescription.getReturnType();
            String statement = (String) methodDescription.getAttr("statement", "");
            String docComment = methodDescription.getDocComment() != null ? methodDescription.getDocComment().getText() : "";
            boolean isImplementation = "true".equals(methodDescription.getAttr("implementation"));
            boolean isExternal = "true".equals(methodDescription.getAttr("external"));

            // 构建缩进
            String indentStr = " ".repeat(indent * 4);

            // 添加消息行
            if (!caller.isEmpty()) {
                // 有明确的调用者
                uml.append(indentStr)
                   .append(caller)
                   .append(" -> ")
                   .append(target)
                   .append(": ")
                   .append(methodName)
                   .append("(")
                   .append(parameters)
                   .append(")")
                   .append(returnType != null && !returnType.equals("void") ? " : " + returnType : "")
                   .append("\n");
            } else if (parent == null) {
                // 顶层调用，从Actor开始
                uml.append(indentStr)
                   .append("Actor")
                   .append(" -> ")
                   .append(target)
                   .append(": ")
                   .append(methodName)
                   .append("(")
                   .append(parameters)
                   .append(")")
                   .append(returnType != null && !returnType.equals("void") ? " : " + returnType : "")
                   .append("\n");
            } else {
                // 其他情况，可能是内部调用
                uml.append(indentStr)
                   .append(target)
                   .append(": ")
                   .append(methodName)
                   .append("(")
                   .append(parameters)
                   .append(")")
                   .append(returnType != null && !returnType.equals("void") ? " : " + returnType : "")
                   .append("\n");
            }

            // 添加方法注释（如果有）
            if (docComment != null && !docComment.trim().isEmpty()) {
                String cleanedComment = cleanComment(docComment);
                if (!cleanedComment.isEmpty()) {
                    uml.append(indentStr)
                       .append("note right\n")
                       .append(indentStr)
                       .append("  ")
                       .append(cleanedComment)
                       .append("\n")
                       .append(indentStr)
                       .append("end note\n");
                }
            }

            // 添加实现类标记
            if (isImplementation) {
                uml.append(indentStr)
                   .append("note right of ")
                   .append(target)
                   .append(" #LightGreen: 实现类\n");
            }

            // 添加外部API标记
            if (isExternal) {
                uml.append(indentStr)
                   .append("note right of ")
                   .append(target)
                   .append(" #LightBlue: 外部API\n");
            }

            // 添加方法体语句（如果有）
            if (statement != null && !statement.trim().isEmpty() && statement.length() < 100) {
                uml.append(indentStr)
                   .append("note right\n")
                   .append(indentStr)
                   .append("  ")
                   .append("执行: ")
                   .append(statement.replace("\n", "\\n"))
                   .append("\n")
                   .append(indentStr)
                   .append("end note\n");
            }

            // 如果是递归调用，添加标记
            if (recursive) {
                uml.append(indentStr)
                   .append("note right of ")
                   .append(target)
                   .append(" #Pink: 递归调用\n");
            }

            // 激活当前对象
            uml.append(indentStr).append("activate ").append(target).append("\n");

            // 递归处理子节点
            for (CallStack child : children) {
                child.appendMessages(uml, indent + 1);
            }

            // 停用当前对象
            uml.append(indentStr).append("deactivate ").append(target).append("\n");

            // 如果有返回值，添加返回箭头
            if (returnType != null && !returnType.equals("void") && !caller.isEmpty()) {
                uml.append(indentStr)
                   .append(target)
                   .append(" --> ")
                   .append(caller)
                   .append(": ")
                   .append(returnType)
                   .append("\n");
            }
        }
    }

    /**
     * 清理JavaDoc注释，提取主要描述
     */
    private String cleanComment(String docComment) {
        if (docComment == null || docComment.isEmpty()) {
            return "";
        }

        // 移除JavaDoc标记
        String cleaned = docComment.replaceAll("/\\*\\*|\\*/|\\*", "").trim();

        // 移除@标签及其内容
        cleaned = cleaned.replaceAll("@[a-zA-Z]+[^@]*", "").trim();

        // 限制长度
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 97) + "...";
        }

        return cleaned;
    }
}
