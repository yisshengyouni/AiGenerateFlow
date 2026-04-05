package com.huq.idea.flow.config.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            " :活动节点;<<#节点背景色>> \n" +
            "elseif (条件?) then (是)\n" +
            "  -[#连接器颜色]-> \n" +
            " :活动节点;<<#节点背景色>> \n" +
            "else (否)\n" +
            " :活动节点;<<#节点背景色>> \n" +
            "endif\n" +
            "### 正确使用颜色：\n" +
            "partition { 内容 }  #分区颜色\n" +
            ":活动节点; <<#节点背景色>> \n" +
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
    public static final String DEFAULT_UML_SEQUENCE_PROMPT = "你是一个UML时序图生成专家。请基于下面提供的Java方法调用链相关的代码，生成一个PlantUML格式的UML时序图。\\n\\n请遵循以下规则：\\n1. \n" +
            "      严格使用PlantUML语法。\\n2. 以 `@startuml` 开始，以 `@enduml` 结束。\\n3. 将每个参与交互的类或组件识别为参与者(participant)。\\n4. 准确地表示方法调用和返回关系，使用 `->` 表示调用，使用 \n" +
            "      `-->` 表示返回。\\n5. 如果代码中有循环或条件判断，请使用 `loop`、`alt`、`opt` 等UML片段来表示。\\n6. 在调用箭头上清晰地标出方法名和参数。\\n7. \n" +
            "      不要包含与代码无关的注释或解释。\\n\\n下面是需要分析的代码：\\n%s";

    public static final String DEFAULT_CLASS_DIAGRAM_PROMPT = "你是一个UML类图生成专家。请基于下面提供的Java类相关的代码，生成一个PlantUML格式的UML类图。\n" +
            "请遵循以下规则：\n" +
            "1. 严格使用PlantUML语法。\n" +
            "2. 以 `@startuml` 开始，以 `@enduml` 结束。\n" +
            "3. 准确表示类之间的关系（继承、实现、组合、聚合、关联、依赖）。\n" +
            "4. 包含类的主要属性和方法（可以省略Getter/Setter等无逻辑的方法）。\n" +
            "5. 不要包含与代码无关的注释或解释。\n\n" +
            "下面是需要分析的代码：\n%s";

    public static final String DEFAULT_STATE_DIAGRAM_PROMPT = "你是一个UML状态图生成专家。请基于下面提供的Java类相关的代码，生成一个PlantUML格式的UML状态图。\n" +
            "请遵循以下规则：\n" +
            "1. 严格使用PlantUML语法。\n" +
            "2. 以 `@startuml` 开始，以 `@enduml` 结束。\n" +
            "3. 识别出类中可能的状态（如枚举值、特定的状态字段等）以及引起状态改变的动作。\n" +
            "4. 准确表示状态之间的转换，使用 `[*]` 表示初始或结束状态，使用 `-->` 表示转换，并在箭头上标注触发转换的事件/方法。\n" +
            "5. 不要包含与代码无关的注释或解释。\n\n" +
            "下面是需要分析的代码：\n%s";

    public static final String DEFAULT_EXPLAIN_CODE_PROMPT = "你是一个高级Java开发专家和架构师。请分析下面提供的Java代码，并用自然语言给出一个详细、清晰的代码解释。\n" +
            "请遵循以下规则：\n" +
            "1. 首先用一两句话总结这个代码（方法或类）的主要目的和功能。\n" +
            "2. 详细说明代码的执行逻辑，按照步骤解释主要处理流程。\n" +
            "3. 指出代码中重要的边界条件、异常处理、或者潜在的风险点。\n" +
            "4. 如果有设计模式的使用或性能上的考量，请简单说明。\n" +
            "5. 语言需要专业、准确，并且排版清晰（可以使用Markdown格式，比如加粗、列表等）。\n\n" +
            "下面是需要分析的代码：\n%s";

    public static final String DEFAULT_REVIEW_CODE_PROMPT = "你是一个高级Java开发专家和代码审查员。请对下面提供的Java代码进行深度审查（Code Review），并给出优化和重构建议。\n" +
            "请遵循以下规则：\n" +
            "1. 审查代码的规范性、可读性、可维护性和性能。\n" +
            "2. 指出潜在的 Bug、内存泄漏、并发安全问题或设计缺陷。\n" +
            "3. 如果代码中存在可以优化的算法或逻辑，请给出改进建议。\n" +
            "4. 如果代码可以重构得更优雅（例如应用设计模式、简化条件判断、提取方法等），请给出具体的重构思路和代码示例。\n" +
            "5. 回复使用Markdown格式排版，清晰易读。\n\n" +
            "下面是需要审查的代码：\n%s";

    public static final String DEFAULT_GENERATE_TEST_PROMPT = "你是一个高级Java开发专家和测试工程师。请基于下面提供的Java代码，生成JUnit 5的单元测试代码。\n" +
            "请遵循以下规则：\n" +
            "1. 生成的代码必须是完整的Java类，包含必要的import语句。\n" +
            "2. 针对目标类的主要逻辑、边界条件和异常情况设计测试用例。\n" +
            "3. 使用Mockito等Mock工具来模拟依赖（如果有）。\n" +
            "4. 遵循Given-When-Then模式，或者使用清晰的注释标注测试步骤。\n" +
            "5. 不要提供额外的解释，只输出代码即可（如果必须解释，也请放在代码块外部）。\n\n" +
            "下面是需要生成测试的代码：\n%s";

    public static final String DEFAULT_GENERATE_JAVADOC_PROMPT = "你是一个高级Java开发专家和文档工程师。请基于下面提供的Java代码，生成规范的JavaDoc注释。\n" +
            "请遵循以下规则：\n" +
            "1. 包含对方法功能的清晰描述。\n" +
            "2. 包含 @param 注释说明每个参数。\n" +
            "3. 包含 @return 注释说明返回值（如果是void则不需要）。\n" +
            "4. 包含 @throws 注释说明可能抛出的异常。\n" +
            "5. 包含适当的 @author 和 @since 标签（如果适用）。\n" +
            "6. 只输出带有JavaDoc注释的方法代码片段。\n\n" +
            "下面是需要生成JavaDoc的代码：\n%s";

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

    public static class CustomAiProviderConfig {
        private String name;
        private String apiUrl;
        private String apiKey;
        private String models; // Comma separated list of models

        public CustomAiProviderConfig() {
        }

        public CustomAiProviderConfig(String name, String apiUrl, String apiKey, String models) {
            this.name = name;
            this.apiUrl = apiUrl;
            this.apiKey = apiKey;
            this.models = models;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModels() {
            return models;
        }

        public void setModels(String models) {
            this.models = models;
        }
    }

    public static class PromptConfig {
        private String name;
        private String prompt;

        public PromptConfig() {
        }

        public PromptConfig(String name, String prompt) {
            this.name = name;
            this.prompt = prompt;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }
    }

    public static class State {
        private String plantumlPathVal;
        // 多AI模型API密钥配置
        private Map<String, String> aiApiKeys = new HashMap<>();
        // 自定义 OpenAI 兼容模型配置
        private List<CustomAiProviderConfig> customAiProviders;
        private String buildMethodPrompt = DEFAULT_BUILD_METHOD_PROMPT;
        private String buildFlowPrompt = DEFAULT_BUILD_FLOW_PROMPT;
        private List<PromptConfig> flowPrompts;
        private List<PromptConfig> classPrompts;
        private List<PromptConfig> sequencePrompts;
        private List<PromptConfig> statePrompts;
        private List<PromptConfig> explainPrompts;
        private List<PromptConfig> reviewPrompts;
        private List<PromptConfig> generateTestPrompts;
        private List<PromptConfig> javaDocPrompts;

        private String buildFlowJsonPrompt = DEFAULT_BUILD_FLOW_JSON_PROMPT;
        private String umlSequencePrompt = DEFAULT_UML_SEQUENCE_PROMPT;
        private String classDiagramPrompt = DEFAULT_CLASS_DIAGRAM_PROMPT;
        private String stateDiagramPrompt = DEFAULT_STATE_DIAGRAM_PROMPT;
        private String explainCodePrompt = DEFAULT_EXPLAIN_CODE_PROMPT;
        private String reviewCodePrompt = DEFAULT_REVIEW_CODE_PROMPT;
        private String generateTestPrompt = DEFAULT_GENERATE_TEST_PROMPT;
        private String javaDocPrompt = DEFAULT_GENERATE_JAVADOC_PROMPT;
        private List<String> relevantClassPatterns = Arrays.asList(
                "*Impl", "*Service", "*Adapter", "*Api", "*Repository",
                "*Mapper", "*Manager", "*Controller"
        );

        private List<String> excludedClassPatterns = Arrays.asList(
                "*Util", "*Utils", "*Helper"
        );

        private List<String> classRelevantClassPatterns = Arrays.asList(
                "*"
        );

        private List<String> classExcludedClassPatterns = Arrays.asList(
                "*Util", "*Utils", "*Helper", "java.*", "javax.*"
        );

        private int classDiagramDepth = 2;
        private boolean includeLibrarySources = false;

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

        public List<String> getClassRelevantClassPatterns() {
            return this.classRelevantClassPatterns;
        }

        public void setClassRelevantClassPatterns(List<String> classRelevantClassPatterns) {
            this.classRelevantClassPatterns = classRelevantClassPatterns;
        }

        public List<String> getClassExcludedClassPatterns() {
            return this.classExcludedClassPatterns;
        }

        public void setClassExcludedClassPatterns(List<String> classExcludedClassPatterns) {
            this.classExcludedClassPatterns = classExcludedClassPatterns;
        }

        public int getClassDiagramDepth() {
            return classDiagramDepth;
        }

        public void setClassDiagramDepth(int classDiagramDepth) {
            this.classDiagramDepth = classDiagramDepth;
        }

        public boolean isIncludeLibrarySources() {
            return includeLibrarySources;
        }

        public void setIncludeLibrarySources(boolean includeLibrarySources) {
            this.includeLibrarySources = includeLibrarySources;
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

        public List<PromptConfig> getFlowPrompts() {
            if (flowPrompts == null || flowPrompts.isEmpty()) {
                flowPrompts = new java.util.ArrayList<>();
                if (buildFlowPrompt != null && !buildFlowPrompt.isEmpty()) {
                    flowPrompts.add(new PromptConfig("Default", buildFlowPrompt));
                } else {
                    flowPrompts.add(new PromptConfig("Default", DEFAULT_BUILD_FLOW_PROMPT));
                }
            }
            return flowPrompts;
        }

        public void setFlowPrompts(List<PromptConfig> flowPrompts) {
            this.flowPrompts = flowPrompts;
        }

        public List<PromptConfig> getClassPrompts() {
            if (classPrompts == null || classPrompts.isEmpty()) {
                classPrompts = new java.util.ArrayList<>();
                if (classDiagramPrompt != null && !classDiagramPrompt.isEmpty()) {
                    classPrompts.add(new PromptConfig("Default", classDiagramPrompt));
                } else {
                    classPrompts.add(new PromptConfig("Default", DEFAULT_CLASS_DIAGRAM_PROMPT));
                }
            }
            return classPrompts;
        }

        public void setClassPrompts(List<PromptConfig> classPrompts) {
            this.classPrompts = classPrompts;
        }

        public List<PromptConfig> getSequencePrompts() {
            if (sequencePrompts == null || sequencePrompts.isEmpty()) {
                sequencePrompts = new java.util.ArrayList<>();
                if (umlSequencePrompt != null && !umlSequencePrompt.isEmpty()) {
                    sequencePrompts.add(new PromptConfig("Default", umlSequencePrompt));
                } else {
                    sequencePrompts.add(new PromptConfig("Default", DEFAULT_UML_SEQUENCE_PROMPT));
                }
            }
            return sequencePrompts;
        }

        public void setSequencePrompts(List<PromptConfig> sequencePrompts) {
            this.sequencePrompts = sequencePrompts;
        }

        public List<PromptConfig> getStatePrompts() {
            if (statePrompts == null || statePrompts.isEmpty()) {
                statePrompts = new java.util.ArrayList<>();
                if (stateDiagramPrompt != null && !stateDiagramPrompt.isEmpty()) {
                    statePrompts.add(new PromptConfig("Default", stateDiagramPrompt));
                } else {
                    statePrompts.add(new PromptConfig("Default", DEFAULT_STATE_DIAGRAM_PROMPT));
                }
            }
            return statePrompts;
        }

        public void setStatePrompts(List<PromptConfig> statePrompts) {
            this.statePrompts = statePrompts;
        }

        public List<PromptConfig> getExplainPrompts() {
            if (explainPrompts == null || explainPrompts.isEmpty()) {
                explainPrompts = new java.util.ArrayList<>();
                explainPrompts.add(new PromptConfig("Default", explainCodePrompt != null ? explainCodePrompt : DEFAULT_EXPLAIN_CODE_PROMPT));
            }
            return explainPrompts;
        }

        public void setExplainPrompts(List<PromptConfig> explainPrompts) {
            this.explainPrompts = explainPrompts;
        }

        public List<PromptConfig> getReviewPrompts() {
            if (reviewPrompts == null || reviewPrompts.isEmpty()) {
                reviewPrompts = new java.util.ArrayList<>();
                reviewPrompts.add(new PromptConfig("Default", reviewCodePrompt != null ? reviewCodePrompt : DEFAULT_REVIEW_CODE_PROMPT));
            }
            return reviewPrompts;
        }

        public void setReviewPrompts(List<PromptConfig> reviewPrompts) {
            this.reviewPrompts = reviewPrompts;
        }

        public List<PromptConfig> getGenerateTestPrompts() {
            if (generateTestPrompts == null || generateTestPrompts.isEmpty()) {
                generateTestPrompts = new java.util.ArrayList<>();
                generateTestPrompts.add(new PromptConfig("Default", generateTestPrompt != null ? generateTestPrompt : DEFAULT_GENERATE_TEST_PROMPT));
            }
            return generateTestPrompts;
        }

        public void setGenerateTestPrompts(List<PromptConfig> generateTestPrompts) {
            this.generateTestPrompts = generateTestPrompts;
        }

        public List<PromptConfig> getJavaDocPrompts() {
            if (javaDocPrompts == null || javaDocPrompts.isEmpty()) {
                javaDocPrompts = new java.util.ArrayList<>();
                javaDocPrompts.add(new PromptConfig("Default", javaDocPrompt != null ? javaDocPrompt : DEFAULT_GENERATE_JAVADOC_PROMPT));
            }
            return javaDocPrompts;
        }

        public void setJavaDocPrompts(List<PromptConfig> javaDocPrompts) {
            this.javaDocPrompts = javaDocPrompts;
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

        public String getClassDiagramPrompt() {
            return this.classDiagramPrompt;
        }

        public void setClassDiagramPrompt(String classDiagramPrompt) {
            this.classDiagramPrompt = classDiagramPrompt;
        }

        public String getStateDiagramPrompt() {
            return this.stateDiagramPrompt;
        }

        public void setStateDiagramPrompt(String stateDiagramPrompt) {
            this.stateDiagramPrompt = stateDiagramPrompt;
        }

        public String getExplainCodePrompt() {
            return this.explainCodePrompt;
        }

        public void setExplainCodePrompt(String explainCodePrompt) {
            this.explainCodePrompt = explainCodePrompt;
        }

        public String getReviewCodePrompt() {
            return reviewCodePrompt;
        }

        public void setReviewCodePrompt(String reviewCodePrompt) {
            this.reviewCodePrompt = reviewCodePrompt;
        }

        public String getGenerateTestPrompt() {
            return generateTestPrompt;
        }

        public void setGenerateTestPrompt(String generateTestPrompt) {
            this.generateTestPrompt = generateTestPrompt;
        }

        public String getJavaDocPrompt() {
            return javaDocPrompt;
        }

        public void setJavaDocPrompt(String javaDocPrompt) {
            this.javaDocPrompt = javaDocPrompt;
        }

        /**
         * 获取指定AI提供商的API密钥
         * @param provider AI提供商名称（如：DEEPSEEK, OPENAI, ANTHROPIC等）
         * @return API密钥，如果未配置则返回null
         */
        public String getAiApiKey(String provider) {
            if (provider == null) {
                return null;
            }
            return aiApiKeys.get(provider.toUpperCase());
        }

        /**
         * 设置所有AI提供商的API密钥配置
         * @param aiApiKeys API密钥配置Map
         */
        public void setAiApiKeys(Map<String, String> aiApiKeys) {
            this.aiApiKeys = aiApiKeys != null ? new HashMap<>(aiApiKeys) : new HashMap<>();
        }

        /**
         * 检查指定AI提供商是否已配置API密钥
         * @param provider AI提供商名称
         * @return 是否已配置
         */
        public boolean hasAiApiKey(String provider) {
            String key = getAiApiKey(provider);
            return key != null && !key.trim().isEmpty();
        }

        public List<CustomAiProviderConfig> getCustomAiProviders() {
            if (customAiProviders == null) {
                customAiProviders = new java.util.ArrayList<>();
            }
            return customAiProviders;
        }

        public void setCustomAiProviders(List<CustomAiProviderConfig> customAiProviders) {
            this.customAiProviders = customAiProviders;
        }
    }
}
