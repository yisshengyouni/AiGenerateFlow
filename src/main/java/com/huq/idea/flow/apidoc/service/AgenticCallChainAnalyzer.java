package com.huq.idea.flow.apidoc.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
        Set<String> requestedMethods = new HashSet<>();
        StringBuilder collectedCode = new StringBuilder();

        collectedCode.append("// === 入口方法代码 ===\n");
        collectedCode.append(entryMethodCode).append("\n\n");

        String currentPrompt = String.format(IdeaSettings.getInstance().getState().getCallChainAnalysisPrompt(), collectedCode.toString());

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

            List<String> neededMethods = extractJsonMethodRequests(aiOutput);

            if (neededMethods.isEmpty() || iteration == MAX_ITERATIONS) {
                statusCallback.accept("分析完成！");
                return aiOutput;
            }

            // We have requests for more methods
            statusCallback.accept("AI 请求查看额外的 " + neededMethods.size() + " 个方法实现，正在查找...");

            StringBuilder newMethodsCode = new StringBuilder();
            for (String methodSignature : neededMethods) {
                if (!requestedMethods.contains(methodSignature)) {
                    requestedMethods.add(methodSignature);
                    String sourceCode = PsiMethodResolver.resolveMethodSource(project, methodSignature);
                    if (sourceCode != null) {
                        newMethodsCode.append("// === 追加方法: ").append(methodSignature).append(" ===\n");
                        newMethodsCode.append(sourceCode).append("\n\n");
                    } else {
                        newMethodsCode.append("// === 追加方法: ").append(methodSignature).append(" (未找到源码或非项目内代码) ===\n\n");
                    }
                }
            }

            // Re-build prompt for next iteration
            collectedCode.append(newMethodsCode);
            currentPrompt = "这是你上一轮请求查看的方法代码，请结合上下文继续深度分析。如果你还需要更多代码，请继续以相同的 JSON 数组格式返回方法签名。如果不需要了，请直接输出最终的 Markdown 分析报告。\n\n" +
                            "补充代码：\n" + newMethodsCode.toString() + "\n\n" +
                            "之前的所有代码上下文：\n" + collectedCode.toString();
        }

        return "达到最大分析轮次限制。";
    }

    private List<String> extractJsonMethodRequests(String aiOutput) {
        List<String> methods = new ArrayList<>();
        // Try to find a JSON array in the text (often enclosed in ```json ... ``` or just raw text)
        Pattern pattern = Pattern.compile("\\[\\s*\"([^\"]+)\"\\s*(?:,\\s*\"([^\"]+)\"\\s*)*\\]");
        Matcher matcher = pattern.matcher(aiOutput);

        if (matcher.find()) {
            String jsonStr = matcher.group(0);
            try {
                JsonArray jsonArray = new Gson().fromJson(jsonStr, JsonArray.class);
                for (JsonElement element : jsonArray) {
                    methods.add(element.getAsString());
                }
            } catch (Exception e) {
                LOG.warn("Failed to parse JSON method requests from AI output: " + jsonStr, e);
            }
        }
        return methods;
    }
}
