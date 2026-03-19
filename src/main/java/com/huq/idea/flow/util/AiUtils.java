package com.huq.idea.flow.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.huq.idea.flow.config.config.IdeaSettings;
import com.intellij.openapi.diagnostic.Logger;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;
/**
 * 多AI模型调用工具类
 * @author huqiang
 * @since 2024/7/20 14:36
 */
public class AiUtils {

    public static final Logger log = Logger.getInstance(AiUtils.class);
    public static ConnectionPool connectionPool = new ConnectionPool(10, 5, TimeUnit.MINUTES);
    private static OkHttpClient client;

    // AI模型提供商枚举
    public enum AiProvider {
        ANTHROPIC("https://api.anthropic.com/v1/messages", "claude-3-sonnet-20240229"),
        BAIDU("https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions", "ernie-bot"),
        ALIBABA("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation", "qwen-turbo"),
        CUSTOM("Custom", "Custom");

        private final String apiUrl;
        private final String defaultModel;

        AiProvider(String apiUrl, String defaultModel) {
            this.apiUrl = apiUrl;
            this.defaultModel = defaultModel;
        }

        public String getApiUrl() { return apiUrl; }
        public String getDefaultModel() { return defaultModel; }
    }

    // AI请求配置类
    public static class AiConfig {
        private AiProvider provider;
        private String apiKey;
        private String model;
        private double temperature = 1.0;
        private int maxTokens = 8000;
        private String systemMessage = "你是一个专业的PlantUML流程图生成专家，你精通PlantUML语法，精通UML类图、顺序图、组件图、用例图、状态图等各种UML图的表示。";
        private String customApiUrl;
        private String providerName;

        public AiConfig(AiProvider provider, String apiKey) {
            this.provider = provider;
            this.apiKey = apiKey;
            this.model = provider.getDefaultModel();
            this.customApiUrl = provider.getApiUrl();
            this.providerName = provider.name();
        }

        public AiConfig(IdeaSettings.CustomAiProviderConfig customConfig, String specificModel) {
            this.provider = AiProvider.CUSTOM;
            this.apiKey = customConfig.getApiKey();
            this.model = specificModel != null && !specificModel.isEmpty() ? specificModel : customConfig.getModels().split(",")[0];
            this.customApiUrl = customConfig.getApiUrl();
            this.providerName = customConfig.getName();
        }

        // Getters and Setters
        public AiProvider getProvider() { return provider; }
        public String getApiKey() { return apiKey; }
        public String getModel() { return model; }
        public AiConfig setModel(String model) { this.model = model; return this; }
        public double getTemperature() { return temperature; }
        public AiConfig setTemperature(double temperature) { this.temperature = temperature; return this; }
        public int getMaxTokens() { return maxTokens; }
        public AiConfig setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
        public String getSystemMessage() { return systemMessage; }
        public AiConfig setSystemMessage(String systemMessage) { this.systemMessage = systemMessage; return this; }
        public String getApiUrl() { return customApiUrl; }
        public String getProviderName() { return providerName; }
    }

    // AI响应结果类
    public static class AiResponse {
        private boolean success;
        private String content;
        private String errorMessage;
        private long responseTime;
        private Object usage;

        public AiResponse(boolean success, String content, String errorMessage, long responseTime, Object usage) {
            this.success = success;
            this.content = content;
            this.errorMessage = errorMessage;
            this.responseTime = responseTime;
            this.usage = usage;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getContent() { return content; }
        public String getErrorMessage() { return errorMessage; }
        public long getResponseTime() { return responseTime; }
        public Object getUsage() { return usage; }
    }

    public static OkHttpClient getOkHttpClient() {
        if (client == null) {
            synchronized (OkHttpClient.class) {
                if (client != null) {
                    return client;
                }
                client = new OkHttpClient().newBuilder()
                        .connectTimeout(3, TimeUnit.MINUTES)
                        .readTimeout(3, TimeUnit.MINUTES)
                        .writeTimeout(3, TimeUnit.MINUTES)
                        .connectionPool(connectionPool)
                        .proxy(Proxy.NO_PROXY)
                        .build();
            }
        }
        return client;
    }

    /**
     * 获取可用的自定义提供商配置
     */
    public static java.util.List<IdeaSettings.CustomAiProviderConfig> getCustomProviders() {
        return IdeaSettings.getInstance().getState().getCustomAiProviders();
    }
    
    /**
     * 统一的AI调用接口
     */
    public static AiResponse callAi(String prompt, AiConfig config) {
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            log.error(config.getProviderName() + " API key is not configured");
            return new AiResponse(false, null, "API key not configured", 0, null);
        }

        long startTime = System.currentTimeMillis();

        try {
            switch (config.getProvider()) {
                case CUSTOM:
                    return callOpenAiCompatible(prompt, config, startTime);
                case ANTHROPIC:
                    return callAnthropic(prompt, config, startTime);
                case BAIDU:
                    return callBaidu(prompt, config, startTime);
                case ALIBABA:
                    return callAlibaba(prompt, config, startTime);
                default:
                    return new AiResponse(false, null, "Unsupported provider: " + config.getProviderName(),
                            System.currentTimeMillis() - startTime, null);
            }
        } catch (Exception e) {
            log.error("AI call failed", e);
            return new AiResponse(false, null, e.getMessage(), System.currentTimeMillis() - startTime, null);
        }
    }

    /**
     * OpenAI兼容格式调用（DeepSeek、OpenAI、Moonshot、智谱等）
     */
    private static AiResponse callOpenAiCompatible(String prompt, AiConfig config, long startTime) throws IOException {
        JsonObject requestJson = new JsonObject();
        JsonArray messages = new JsonArray();

        // 系统消息
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", config.getSystemMessage());
        messages.add(systemMessage);

        // 用户消息
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        requestJson.add("messages", messages);
        requestJson.addProperty("model", config.getModel());
        requestJson.addProperty("temperature", config.getTemperature());
        requestJson.addProperty("max_tokens", config.getMaxTokens());
        requestJson.addProperty("stream", false);

        log.info("Request to " + config.getProviderName() + ": " + requestJson.toString());

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, requestJson.toString());

        Request request = new Request.Builder()
                .url(config.getApiUrl())
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .build();

        Response response = getOkHttpClient().newCall(request).execute();
        String responseBody = response.body().string();
        log.info("Response from " + config.getProviderName() + ": " + responseBody);

        if (!response.isSuccessful()) {
            return new AiResponse(false, null, "HTTP " + response.code() + ": " + responseBody,
                    System.currentTimeMillis() - startTime, null);
        }

        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

        // 解析使用量信息
        Object usage = null;
        if (jsonObject.has("usage")) {
            usage = jsonObject.getAsJsonObject("usage");
        }

        // 解析内容
        String content = jsonObject.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .get("message").getAsJsonObject()
                .get("content").getAsString();

        long responseTime = System.currentTimeMillis() - startTime;
        log.info(config.getProviderName() + " 消耗 token: " + (usage != null ? usage.toString() : "N/A") +
                ", 耗时：" + responseTime + " ms");

        return new AiResponse(true, content, null, responseTime, usage);
    }

    /**
     * Anthropic Claude调用格式
     */
    private static AiResponse callAnthropic(String prompt, AiConfig config, long startTime) throws IOException {
        JsonObject requestJson = new JsonObject();
        JsonArray messages = new JsonArray();

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        requestJson.add("messages", messages);
        requestJson.addProperty("model", config.getModel());
        requestJson.addProperty("max_tokens", config.getMaxTokens());
        requestJson.addProperty("system", config.getSystemMessage());

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, requestJson.toString());

        Request request = new Request.Builder()
                .url(config.getApiUrl())
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", config.getApiKey())
                .addHeader("anthropic-version", "2023-06-01")
                .build();

        Response response = getOkHttpClient().newCall(request).execute();
        String responseBody = response.body().string();

        if (!response.isSuccessful()) {
            return new AiResponse(false, null, "HTTP " + response.code() + ": " + responseBody,
                    System.currentTimeMillis() - startTime, null);
        }

        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

        String content = jsonObject.getAsJsonArray("content")
                .get(0).getAsJsonObject()
                .get("text").getAsString();

        return new AiResponse(true, content, null, System.currentTimeMillis() - startTime,
                jsonObject.has("usage") ? jsonObject.get("usage") : null);
    }

    /**
     * 百度文心一言调用格式
     */
    private static AiResponse callBaidu(String prompt, AiConfig config, long startTime) throws IOException {
        // 百度需要先获取access_token，这里简化处理
        JsonObject requestJson = new JsonObject();
        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", config.getSystemMessage());
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        requestJson.add("messages", messages);
        requestJson.addProperty("temperature", config.getTemperature());
        requestJson.addProperty("max_output_tokens", config.getMaxTokens());

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, requestJson.toString());

        // 注意：百度API需要在URL中添加access_token参数
        String urlWithToken = config.getApiUrl() + "?access_token=" + config.getApiKey();

        Request request = new Request.Builder()
                .url(urlWithToken)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();

        Response response = getOkHttpClient().newCall(request).execute();
        String responseBody = response.body().string();

        if (!response.isSuccessful()) {
            return new AiResponse(false, null, "HTTP " + response.code() + ": " + responseBody,
                    System.currentTimeMillis() - startTime, null);
        }

        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

        String content = jsonObject.get("result").getAsString();

        return new AiResponse(true, content, null, System.currentTimeMillis() - startTime,
                jsonObject.has("usage") ? jsonObject.get("usage") : null);
    }

    /**
     * 阿里通义千问调用格式
     */
    private static AiResponse callAlibaba(String prompt, AiConfig config, long startTime) throws IOException {
        JsonObject requestJson = new JsonObject();
        JsonObject input = new JsonObject();
        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", config.getSystemMessage());
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        input.add("messages", messages);
        requestJson.add("input", input);
        requestJson.addProperty("model", config.getModel());

        JsonObject parameters = new JsonObject();
        parameters.addProperty("temperature", config.getTemperature());
        parameters.addProperty("max_tokens", config.getMaxTokens());
        requestJson.add("parameters", parameters);

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, requestJson.toString());

        Request request = new Request.Builder()
                .url(config.getApiUrl())
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .build();

        Response response = getOkHttpClient().newCall(request).execute();
        String responseBody = response.body().string();

        if (!response.isSuccessful()) {
            return new AiResponse(false, null, "HTTP " + response.code() + ": " + responseBody,
                    System.currentTimeMillis() - startTime, null);
        }

        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

        String content = jsonObject.getAsJsonObject("output")
                .get("text").getAsString();

        return new AiResponse(true, content, null, System.currentTimeMillis() - startTime,
                jsonObject.has("usage") ? jsonObject.get("usage") : null);
    }

    /**
     * 便捷方法，保持向后兼容
     * 优先使用新的自定义模型配置
     */
    @Deprecated
    public static String okRequest(String prompt) {
        java.util.List<IdeaSettings.CustomAiProviderConfig> customProviders = getCustomProviders();
        if (!customProviders.isEmpty()) {
            IdeaSettings.CustomAiProviderConfig provider = customProviders.get(0);
            AiConfig config = new AiConfig(provider, null);
            AiResponse response = callAi(prompt, config);
            return response.isSuccess() ? response.getContent() : null;
        }
        
        log.warn("No API key configured for any AI provider");
        return null;
    }
}
