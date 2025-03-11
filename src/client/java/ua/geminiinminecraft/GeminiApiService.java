package ua.geminiinminecraft;

import com.google.gson.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class GeminiApiService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiInMinecraftClient.MOD_ID);
    private static final HttpClient client = HttpClient.newHttpClient();

    private String apiUrl;
    private String apiKey;
    private String systemMessage;
    private String aiCommandSystemPrompt;
    private boolean commandExecutionEnabled;
    private int maxOutputTokens;

    private final Map<UUID, List<JsonObject>> conversationHistory;
    private final boolean memoryEnabled;
    private final int memorySize;

    public GeminiApiService(String modelName, String apiKey, String systemMessage, String aiCommandSystemPrompt,
                            boolean commandExecutionEnabled, int maxOutputTokens,
                            Map<UUID, List<JsonObject>> conversationHistory, boolean memoryEnabled, int memorySize) {
        this.apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent";
        this.apiKey = apiKey;
        this.systemMessage = systemMessage;
        this.aiCommandSystemPrompt = aiCommandSystemPrompt;
        this.commandExecutionEnabled = commandExecutionEnabled;
        this.maxOutputTokens = maxOutputTokens;
        this.conversationHistory = conversationHistory;
        this.memoryEnabled = memoryEnabled;
        this.memorySize = memorySize;
    }

    public void updateApiUrl(String modelName) {
        this.apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent";
    }

    public void updateApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void updateSystemMessage(String systemMessage) {
        this.systemMessage = systemMessage;
    }

    public void updateMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public void setCommandExecutionEnabled(boolean enabled) {
        this.commandExecutionEnabled = enabled;
    }

    public void sendQuery(String query, UUID playerUuid, Consumer<String> responseCallback, Consumer<Exception> errorCallback) {
        LOGGER.info("Query to AI: {}", query);

        CompletableFuture.supplyAsync(() -> {
            try {
                return sendMessageToGemini(query, playerUuid);
            } catch (Exception e) {
                LOGGER.error("Error querying AI!", e);
                errorCallback.accept(e);
                return null;
            }
        }).thenAccept(response -> {
            if (response != null) {
                try {
                    JsonObject parsedResponse = JsonParser.parseString(response).getAsJsonObject();
                    String textResponse = parseGeminiResponse(parsedResponse);

                    if (memoryEnabled) {
                        storeConversation(playerUuid, createUserMessageObject(query), createAssistantMessageObject(textResponse));
                    }

                    responseCallback.accept(textResponse);
                } catch (Exception e) {
                    LOGGER.error("Error processing AI response!", e);
                    errorCallback.accept(e);
                }
            }
        });
    }

    private String sendMessageToGemini(String userMessage, UUID playerUuid) throws Exception {
        JsonObject requestBody = buildRequestBody(userMessage, playerUuid);
        HttpResponse<String> response = sendRequest(requestBody);

        return handleResponse(response);
    }

    private JsonObject buildRequestBody(String userMessage, UUID playerUuid) {
        JsonObject requestBody = new JsonObject();

        requestBody.add("contents", buildContentsArray(userMessage, playerUuid));
        requestBody.add("systemInstruction", buildSystemInstruction());
        requestBody.add("generationConfig", buildGenerationConfig());

        return requestBody;
    }

    private JsonArray buildContentsArray(String userMessage, UUID playerUuid) {
        JsonArray contents = new JsonArray();

        if (memoryEnabled && conversationHistory.containsKey(playerUuid)) {
            contents.addAll(new Gson().fromJson(new Gson().toJson(conversationHistory.get(playerUuid)), JsonArray.class));
        }

        JsonObject userMsgObj = createUserMessageObject(userMessage);
        contents.add(userMsgObj);

        return contents;
    }

    private JsonObject buildSystemInstruction() {
        String processedSystemMessage = VariableProcessor.processVariables(getFullSystemMessage());

        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemPart = new JsonObject();
        systemPart.addProperty("text", processedSystemMessage);
        systemParts.add(systemPart);
        systemInstruction.add("parts", systemParts);

        return systemInstruction;
    }

    private String getFullSystemMessage() {
        if (commandExecutionEnabled) {
            return systemMessage + "\n\n" + aiCommandSystemPrompt;
        }
        return systemMessage;
    }

    private JsonObject buildGenerationConfig() {
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", maxOutputTokens);
        return generationConfig;
    }

    private HttpResponse<String> sendRequest(JsonObject requestBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        LOGGER.info("AI HTTP Status: {}", response.statusCode());

        return response;
    }

    private String handleResponse(HttpResponse<String> response) {
        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("Request error! Status code: " + response.statusCode() + ", body: " + response.body());
        }
    }

    public String parseGeminiResponse(JsonObject jsonResponse) {
        try {
            return jsonResponse
                    .getAsJsonArray("candidates")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0)
                    .getAsJsonObject()
                    .get("text")
                    .getAsString();
        } catch (Exception e) {
            LOGGER.error("Error parsing JSON response: {}", jsonResponse, e);
            return "Failed to get response from AI. Check logs for details.";
        }
    }

    private void storeConversation(UUID playerUuid, JsonObject userMessage, JsonObject assistantMessage) {
        conversationHistory.putIfAbsent(playerUuid, new java.util.ArrayList<>());
        List<JsonObject> history = conversationHistory.get(playerUuid);

        history.add(userMessage);
        history.add(assistantMessage);

        while (history.size() > memorySize * 2) {
            history.removeFirst();
            history.removeFirst();
        }
    }

    private @NotNull JsonObject createUserMessageObject(String message) {
        JsonObject msgObj = new JsonObject();
        msgObj.addProperty("role", "user");

        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", message);
        parts.add(part);

        msgObj.add("parts", parts);
        return msgObj;
    }

    private @NotNull JsonObject createAssistantMessageObject(String message) {
        JsonObject msgObj = new JsonObject();
        msgObj.addProperty("role", "model");

        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", message);
        parts.add(part);

        msgObj.add("parts", parts);
        return msgObj;
    }
}