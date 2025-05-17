package com.huq.idea.flow.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.huq.idea.config.IdeaSettings;
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
 * @author huqiang
 * @since 2024/7/20 14:36
 */
public class AiUtils {

    public static final Logger log = Logger.getInstance(AiUtils.class);

    public static ConnectionPool connectionPool = new ConnectionPool(10, 5, TimeUnit.MINUTES);

    private static OkHttpClient client;

    public static OkHttpClient getOkHttpClient() {
        if (client == null) {
            synchronized (OkHttpClient.class) {
                if (client != null) {
                    return client;
                }
                client = new OkHttpClient().newBuilder()
                        .connectTimeout(3, TimeUnit.MINUTES) // 连接超时时间
                        .readTimeout(3, TimeUnit.MINUTES)    // 读取超时时间
                        .writeTimeout(3, TimeUnit.MINUTES)   // 写入超时时间
                        .connectionPool(connectionPool)
                        .proxy(Proxy.NO_PROXY)
                        .build();
            }
        }
        return client;
    }

    public static String okRequest(String prompt) {
        // Get API key from settings
        String apiKey = IdeaSettings.getInstance().getState().getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("DeepSeek API key is not configured in settings");
            return null;
        }

        MediaType mediaType = MediaType.parse("application/json");
        OkHttpClient okHttpClient = getOkHttpClient();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);

        log.info("request: " + message);

        RequestBody body = RequestBody.create(mediaType, "{\n  \"messages\": [\n    {\n      " +
                "\"content\": \"你是一个编程开发助手，擅长各类开发问题处理。\",\n      \"role\": \"system\"\n    },\n   " +
                message.toString()+"],\n  \"model\": \"deepseek-coder\",\n  \"frequency_penalty\": 0,\n  " +
                "\"max_tokens\": 8000,\n  \"presence_penalty\": 0,\n  \"stop\": null,\n  \"stream\": false," +
                "\n  \"temperature\": 1.5,\n  \"top_p\": 1,\n  \"logprobs\": false,\n  \"top_logprobs\": null\n" +
                "}");
        Request request = new Request.Builder()
                .url("https://api.deepseek.com/chat/completions")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
        try {
            long startTime = System.currentTimeMillis();
            Response response = okHttpClient.newCall(request).execute();
            String responseBody = response.body().string();
            log.info("response: " + responseBody);

            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            log.info("消耗 token: " + jsonObject.getAsJsonObject("usage").toString() + ", 耗时：" + (System.currentTimeMillis() - startTime)+" ms");
            return jsonObject.getAsJsonArray("choices").get(0).getAsJsonObject().get("message").getAsJsonObject().get("content").getAsString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
