package com.huq.idea.flow.config.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author huqiang
 * @since 2024/8/1 19:27
 */
@State(name = "IdeaSettings",
        storages = @Storage("ideaSettings.xml"))
public class IdeaSettings implements PersistentStateComponent<IdeaSettings.State> {

    private State state = new State();


    public static final String DEFAULT_BUILD_METHOD_PROMPT = "引导提示词：\n" +
            "创建一个 `methodName` 方法，并根据每个属性的注释和名称，填充符合实际业务场景的示例值。确保所有属性都被赋值。\n" +
            "输出结果中只保留 `methodName` 方法的代码 。\n";


    public static final String DEFAULT_BUILD_FLOW_PROMPT = "" +
            "请分析以下Java代码并生成一个高质量的PlantUML流程图，遵循专业的软件工程可视化最佳实践。\n" +
            "\n" +
            "## 输出要求\n" +
            "\n" +
            "1. 生成完整的**PlantUML活动图代码**，确保语法正确，可直接在PlantUML渲染器中使用\n" +
            "2. 流程图需要**突出显示关键业务路径**\n" +
            "3. 为流程图添加清晰的**分层结构**，将相关功能组织到有意义的分区中\n" +
            "4. 在图中添加**业务关键点的说明性注释**，解释重要的业务规则和技术实现\n" +
            "5. 使用**颜色编码区分**不同类型的处理路径（正常流程、验证失败、系统异常等）\n" +
            "6. 对于复杂的条件判断，提供**清晰的分支标签**说明判断条件\n" +
            "7. **只输出可直接渲染的PlantUML代码** \n" +
            "\n" +
            "## 语法规范（必须遵守）\n" +
            "\n" +
            "plantuml\n" +
            "@startuml\n" +
            "### 正确定义条件语句：\n" +
            "if (条件?) then (是)\n" +
            "  -[#连接器颜色]-> \n" +
            " #节点背景色 :活动节点; \n" +
            "elseif (条件?) then (是)\n" +
            "  -[#连接器颜色]-> \n" +
            " #节点背景色 :活动节点; \n" +
            "else (否)\n" +
            " #节点背景色 :活动节点; \n" +
            "endif\n" +
            "### 正确使用颜色：\n" +
            "partition { 内容 }  #分区颜色\n" +
            "#节点背景色 :活动节点; \n" +
            "@enduml\n" +
            "\n" +
            "### 错误预防清单\n" +
            "❗ 禁止在if/while等控制语句上直接加颜色\n" +
            "❗ 所有符号必须为英文半角\n" +
            "❗ 确保每个start对应stop\n" +
            "❗ 分区必须包含完整逻辑块\n" +
            "❗ 不要显式声明连接，使用自动布局\n" +
            "\n" +
            "## 样式指南\n" +
//            "1.成功路径：#LightGreen + 绿色边框\n" +
//            "2.异常路径：#LightGray + 红色箭头\n" +
//            "3.验证步骤：置于黄色分区#FFFFD0\n" +
//            "4.技术注释：右对齐灰色注释框" +
            "   - 成功路径：使用浅绿色背景(#d4f7d4)和绿色边框(#82d282)\n" +
            "   - 异常路径：使用浅红色背景(#ffdddd)和红色边框(#ff9999)\n" +
            "   - 验证步骤：使用浅黄色背景(#ffffd0)（可选）\n" +
            "   - 分区背景：使用浅蓝色背景(#F0F8FF)和蓝色边框(#6495ED)\n" +
            "\n" +
            "## 代码分析指南\n" +
            "1. 识别主要的函数和它们的调用关系\n" +
            "2. 找出条件分支和异常处理逻辑\n" +
            "3. 注意分布式锁等关键资源的获取和释放\n" +
            "4. 特别关注业务验证步骤和异常处理\n" +
            "5. 理解主要的数据流动和转换\n" +
            "\n" +
            "请基于上述指南，生成一个清晰、美观且专业的PlantUML流程图，展示以下Java代码的执行逻辑。\n" +
            "```java\n" +
            "%s\n" +
            "```";

    public static final String DEFAULT_BUILD_FLOW_JSON_PROMPT = "你是一位代码分析专家。请分析以下Java代码，并生成一个详细的流程图JSON数据。\n" +
            "JSON数据应该包含节点和边的信息，用于可视化代码的执行流程。\n" +
            "节点应该包含方法的信息，如类名、方法名、描述等。\n" +
            "边应该表示方法之间的调用关系。\n" +
            "请确保JSON格式正确，并且包含足够的信息以便可视化。\n" +
            "请确保返回数据只保留JSON格式数据，不要有其他多余内容,对json数据进行压缩处理。\n" +
            "JSON格式如下：\n" +
            "{\n" +
            "  \"nodes\": [\n" +
            "    {\n" +
            "      \"id\": \"唯一标识符\",\n" +
            "      \"label\": \"显示名称\",\n" +
            "      \"type\": \"节点类型（如method, condition, loop等）\",\n" +
            "      \"className\": \"类名\",\n" +
            "      \"methodName\": \"方法名\",\n" +
            "      \"description\": \"节点描述\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"edges\": [\n" +
            "    {\n" +
            "      \"source\": \"源节点ID\",\n" +
            "      \"target\": \"目标节点ID\",\n" +
            "      \"label\": \"边标签\",\n" +
            "      \"type\": \"边类型（如call, return, condition等）\"\n" +
            "    }\n" +
            "  ]\n" +
            "}\n\n" +
            "代码：\n%s";
    public static final String DEFAULT_UML_SEQUENCE_PROMPT = "你是一位UML序列图专家。分析以下方法调用链，并生成一个详细的PlantUML序列图。\n" +
            "图表应该展示所有方法调用，包括接口和实现。\n" +
            "包含所有相关细节，如方法参数和返回类型。\n" +
            "为每个重要步骤添加中文注释，解释其业务逻辑。\n" +
            "只返回PlantUML代码，不要有其他内容。\n\n" +
            "方法调用链：\n%s";

    public static IdeaSettings getInstance() {
        return ApplicationManager.getApplication().getService(IdeaSettings.class);
    }

    @Override
    public @Nullable IdeaSettings.State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public static class State {
        private String plantumlPathVal;
        private String apiKey;
        private String buildMethodPrompt = DEFAULT_BUILD_METHOD_PROMPT;
        private String buildFlowPrompt = DEFAULT_BUILD_FLOW_PROMPT;
        private String buildFlowJsonPrompt = DEFAULT_BUILD_FLOW_JSON_PROMPT;
        private String umlSequencePrompt = DEFAULT_UML_SEQUENCE_PROMPT;
        private List<String> relevantClassPatterns = Arrays.asList(
                "*Impl", "*Service", "*Adapter", "*Api", "*Repository",
                "*Mapper", "*Manager", "*Controller"
        );

        private List<String> excludedClassPatterns = Arrays.asList(
                "*Util", "*Utils", "*Helper"
        );

        public List<String> getExcludedClassPatterns() {
            return this.excludedClassPatterns;
        }

        public void setExcludedClassPatterns(List<String> excludedClassPatterns) {
            this.excludedClassPatterns = excludedClassPatterns;
        }

        public List<String> getRelevantClassPatterns() {
            return this.relevantClassPatterns;
        }

        public void setRelevantClassPatterns(List<String> relevantClassPatterns) {
            this.relevantClassPatterns = relevantClassPatterns;
        }

        public String getPlantumlPathVal() {
            return this.plantumlPathVal;
        }

        public void setPlantumlPathVal(String plantumlPathVal) {
            this.plantumlPathVal = plantumlPathVal;
        }

        public String getBuildMethodPrompt() {
            return this.buildMethodPrompt;
        }

        public void setBuildMethodPrompt(String buildMethodPrompt) {
            this.buildMethodPrompt = buildMethodPrompt;
        }

        public String getBuildFlowPrompt() {
            return this.buildFlowPrompt;
        }

        public void setBuildFlowPrompt(String buildFlowPrompt) {
            this.buildFlowPrompt = buildFlowPrompt;
        }

        public String getBuildFlowJsonPrompt() {
            return this.buildFlowJsonPrompt;
        }

        public void setBuildFlowJsonPrompt(String buildFlowJsonPrompt) {
            this.buildFlowJsonPrompt = buildFlowJsonPrompt;
        }

        public String getUmlSequencePrompt() {
            return this.umlSequencePrompt;
        }

        public void setUmlSequencePrompt(String umlSequencePrompt) {
            this.umlSequencePrompt = umlSequencePrompt;
        }

        public String getApiKey() {
            return this.apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
