package com.huq.idea.flow.apidoc.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.huq.idea.flow.util.AiUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgenticCallChainAnalyzer {
    private static final Logger LOG = Logger.getInstance(AgenticCallChainAnalyzer.class);
    private final Project project;
    private final IdeaSettings.CustomAiProviderConfig provider;
    private final String model;
    private final Consumer<String> statusCallback;
    private final int MAX_ITERATIONS = 3;

    public AgenticCallChainAnalyzer(Project project, IdeaSettings.CustomAiProviderConfig provider, String model, Consumer<String> statusCallback) {
        this.project = project;
        this.provider = provider;
        this.model = model;
        this.statusCallback = statusCallback;
    }

    public String analyze(String entryMethodCode, ProgressIndicator indicator) {
        Set<String> requestedItems = new HashSet<>();
        StringBuilder collectedCode = new StringBuilder();

        collectedCode.append("// === 入口方法代码 ===\n");
        collectedCode.append(entryMethodCode).append("\n\n");

        String currentPrompt = IdeaSettings.getInstance().getState().getCallChainAnalysisPrompt().replace("%s", collectedCode.toString());

        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            if (indicator != null && indicator.isCanceled()) {
                return "分析已被取消。";
            }

            statusCallback.accept("第 " + iteration + " 轮分析中，正在调用 AI 模型...");

            AiUtils.AiConfig config = new AiUtils.AiConfig(provider, model);
            config.setSystemMessage("你是一个专业的Java开发和架构审查专家。");
            config.setTemperature(0.5);

            AiUtils.AiResponse response = AiUtils.callAi(currentPrompt, config);

            if (!response.isSuccess()) {
                return "分析失败 (" + iteration + "): " + response.getErrorMessage();
            }

            String aiOutput = response.getContent();

            AiRequest aiRequest = extractJsonRequests(aiOutput);

            if ((aiRequest.methods.isEmpty() && aiRequest.classes.isEmpty()) || iteration == MAX_ITERATIONS) {
                statusCallback.accept("分析完成！");
                return aiOutput;
            }

            // We have requests for more code
            statusCallback.accept("AI 请求查看额外的 " + aiRequest.methods.size() + " 个方法和 " + aiRequest.classes.size() + " 个类结构，正在查找...");

            StringBuilder newCodeContext = new StringBuilder();

            // Process method requests
            for (String methodSignature : aiRequest.methods) {
                if (!requestedItems.contains(methodSignature)) {
                    requestedItems.add(methodSignature);
                    String sourceCode = PsiMethodResolver.resolveSource(project, methodSignature);
                    if (sourceCode != null) {
                        newCodeContext.append("// === 追加方法源码: ").append(methodSignature).append(" ===\n");
                        newCodeContext.append(sourceCode).append("\n\n");
                    } else {
                        newCodeContext.append("// === 追加方法源码: ").append(methodSignature).append(" (未找到源码或接口无实现) ===\n\n");
                    }
                }
            }

            // Process class requests
            for (String className : aiRequest.classes) {
                if (!requestedItems.contains(className)) {
                    requestedItems.add(className);
                    String classStructure = PsiMethodResolver.resolveClassSource(project, className);
                    if (classStructure != null) {
                        newCodeContext.append("// === 追加类结构: ").append(className).append(" ===\n");
                        newCodeContext.append(classStructure).append("\n\n");
                    } else {
                        newCodeContext.append("// === 追加类结构: ").append(className).append(" (未找到该类) ===\n\n");
                    }
                }
            }

            // Re-build prompt for next iteration
            collectedCode.append(newCodeContext);
            currentPrompt = "这是你上一轮请求查看的方法源码和类结构，请结合上下文继续深度分析。如果你还需要更多代码或类结构，请继续以相同的 JSON 对象格式返回。如果不需要了，请直接输出最终的 Markdown 分析报告。\n\n" +
                            "补充代码：\n" + newCodeContext.toString() + "\n\n" +
                            "之前的所有代码上下文：\n" + collectedCode.toString();
        }

        return "达到最大分析轮次限制。";
    }

    private static class AiRequest {
        List<String> methods = new ArrayList<>();
        List<String> classes = new ArrayList<>();
    }

    private AiRequest extractJsonRequests(String aiOutput) {
        AiRequest request = new AiRequest();
        try {
            // Find the JSON object block, it might be wrapped in markdown like ```json ... ```
            int startIndex = aiOutput.indexOf('{');
            int endIndex = aiOutput.lastIndexOf('}');

            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                String jsonStr = aiOutput.substring(startIndex, endIndex + 1);
                JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject();

                if (jsonObject.has("methods")) {
                    JsonArray methodsArray = jsonObject.getAsJsonArray("methods");
                    for (JsonElement element : methodsArray) {
                        request.methods.add(element.getAsString());
                    }
                }
                if (jsonObject.has("classes")) {
                    JsonArray classesArray = jsonObject.getAsJsonArray("classes");
                    for (JsonElement element : classesArray) {
                        request.classes.add(element.getAsString());
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse JSON requests from AI output.", e);
        }
        return request;
    }
}
