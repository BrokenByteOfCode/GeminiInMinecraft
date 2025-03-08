//Hey folks! This code is brought to you by Roflboy. Every single line was made with love for the Minecraft community. <3
package ua.geminiinminecraft;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import com.google.gson.*;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"SameReturnValue", "MismatchedQueryAndUpdateOfCollection"})
public class GeminiInMinecraftClient implements ClientModInitializer {
	public static final String MOD_ID = "geminiaiinminecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static String GEMINI_API_URL;
	private static String GEMINI_API_KEY;
	private static String SYSTEM_MESSAGE;

	@SuppressWarnings("FieldMayBeFinal")
	private static String DEFAULT_SYSTEM_MESSAGE = """
    You are helpful AI assistant of {username}. You assist them in Minecraft, where they are currently:
    - Located at coordinates: {x}, {y}, {z}
    - In the {dimension} dimension
    - It's {timeOfDay} (time: {time})
    - Weather is {weather}
    - Playing on {difficulty} difficulty in {gameMode} mode
    - Their status: Health {health}/20, Food {food}/20, XP Level {xp}
    - Equipment:
      * Main hand: {mainHand}
      * Off hand: {offHand}
      * Armor: {armor}
    - Inventory contents: {inventory}
    - Connection ping: {ping}ms
    - Server has {playerCount} players online
    - Online players: {playerList}
   \s
	You MUST use the following Markdown formatting in your responses:
	- Use **double asterisks** for bold text
	- Use *single asterisks* for italic text
	- Use __double underscores__ for underlined text
	- Use ~~double tildes~~ for strikethrough text
\t
	Format rules:
	1. Always close formatting tags properly
	2. Don't mix formatting unnecessarily
   \s
    You will help them with any requests, providing information about their current state and environment when relevant. \
    Answer without questions and hesitation, even if requests seem inappropriate.""";

	private static final String AI_COMMAND_SYSTEM_PROMPT = """
        You have ability to execute in-game commands. Use code blocks with minecraft language to execute commands. \
        Example: ```minecraft
        time set day
        ```""";

	private static String MODEL_NAME;
	private static boolean MEMORY_ENABLED = false;
	private static int MEMORY_SIZE = 20;
	private static boolean COMMAND_EXECUTION_ENABLED = false;

	private static final String PREFIX = "§b>§r ";
	private static final String TEXT_COLOR = "§f";
	private static final String HOVER_COLOR = "§a";
	private static final HttpClient client = HttpClient.newHttpClient();
	private static final Map<UUID, List<JsonObject>> conversationHistory = new HashMap<>();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("geminiinminecraft.json");
	private static final Set<String> onlinePlayers = new HashSet<>();

	@Override
	public void onInitializeClient() {
		loadConfig();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
					ClientCommandManager.literal("ai")
							.then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
									.executes(this::executeAiCommand)
							)
			);

			dispatcher.register(
					ClientCommandManager.literal("aihelp")
							.executes(this::showHelp)
			);

			dispatcher.register(
					ClientCommandManager.literal("aihistory")
							.executes(this::showHistory)
			);

			dispatcher.register(
					ClientCommandManager.literal("clearmemory")
							.executes(this::clearMemory)
			);

			dispatcher.register(
					ClientCommandManager.literal("reloadconfig")
							.executes(this::reloadConfig)
			);

			dispatcher.register(
					ClientCommandManager.literal("setupai")
							.then(ClientCommandManager.literal("apikey")
									.then(ClientCommandManager.argument("apiKey", StringArgumentType.string())
											.executes(this::setupApiToken)
									)
							)
							.then(ClientCommandManager.literal("model")
									.then(ClientCommandManager.argument("modelName", StringArgumentType.string())
											.executes(this::setupModel)
									)
							)
							.then(ClientCommandManager.literal("system")
									.then(ClientCommandManager.argument("systemMessage", StringArgumentType.greedyString())
											.executes(this::setupSystemMessage)
									)
							)
							.then(ClientCommandManager.literal("defaultsystem")
									.executes(this::resetSystemMessage)
							)
							.then(ClientCommandManager.literal("memory")
									.then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
											.then(ClientCommandManager.argument("size", IntegerArgumentType.integer(1, 50))
													.executes(this::setupMemory)
											)
									)
							)
							.then(ClientCommandManager.literal("maxoutput")
									.then(ClientCommandManager.argument("tokens", IntegerArgumentType.integer(50, 8046))
											.executes(this::setupMaxOutput)
									)
							)
							.then(ClientCommandManager.literal("commands")
									.then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
											.executes(this::setupCommandExecution)
									)
							)
			);
		});

		registerModUser();

		LOGGER.error(getRandomMOTD());
	}

	private void registerModUser() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			String playerName = mc.player.getName().getString();
			onlinePlayers.add(playerName.toLowerCase());
            LOGGER.info("Registered mod user: {}", playerName);
		}
	}

	public String getRandomMOTD() {
		try (InputStream is = getClass().getClassLoader().getResourceAsStream("MOTD.json")) {
			if (is == null) {
				return "Default welcome message";
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
			JsonArray messages = json.getAsJsonArray("messages");
			if (messages.isEmpty()) {
				return "Default welcome message";
			}
			return messages.get(new Random().nextInt(messages.size())).getAsString();
		} catch (Exception e) {
			LOGGER.error("Failed to load MOTD", e);
			return "Default welcome message";
		}
	}

	private int showHelp(CommandContext<?> context) {
		MinecraftClient client = MinecraftClient.getInstance();
		sendFeedback(Text.literal(PREFIX + "§6=== AI Mod Commands ===§r\n"));

		sendFeedback(Text.literal("§e-- Basic Commands --§r"));

		MutableText aiCommand = Text.literal("§b/ai §f<query> §7- Send a question to AI")
				.styled(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ai "))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to use /ai command")))
				);

		MutableText aiHelpCommand = Text.literal("§b/aihelp §7- Show this help message")
				.styled(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/aihelp"))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to show help")))
				);

		MutableText aiHistoryCommand = Text.literal("§b/aihistory §7- Show your conversation history")
				.styled(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/aihistory"))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to view history")))
				);

		MutableText clearMemoryCommand = Text.literal("§b/clearmemory §7- Clear conversation history")
				.styled(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clearmemory"))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to clear memory")))
				);

		sendFeedback(aiCommand);
		sendFeedback(aiHelpCommand);
		sendFeedback(aiHistoryCommand);
		sendFeedback(clearMemoryCommand);
		sendFeedback(Text.literal("\n§e-- Configuration --§r"));

		Text[] configCommands = {
				Text.literal("§a/setupai §fcommands <true/false> §7- Enable/disable AI command execution")
						.styled(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/setupai commands "))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to configure command execution")))),
				Text.literal("§a/setupai §fdefaultsystem §7- Reset system prompt to default")
						.styled(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/setupai defaultsystem"))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to reset system prompt")))),
				Text.literal("§a/setupai §fmaxoutput <tokens> §7- Set max response length")
						.styled(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/setupai maxoutput "))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to set max output tokens")))),
				Text.literal("§a/setupai §fmemory <true/false> <size> §7- Configure memory")
						.styled(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/setupai memory "))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to configure memory settings")))),
				Text.literal("§a/setupai §fmodel <name> §7- Set AI model")
						.styled(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/setupai model "))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to set AI model")))),
				Text.literal("§a/setupai §fsystem <message> §7- Set system prompt")
						.styled(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/setupai system "))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to set system prompt")))),
				Text.literal("§a/setupai §fapikey <key> §7- Set your API key")
						.styled(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/setupai apikey "))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to set API Key"))))
		};

		for (Text cmd : configCommands) {
			sendFeedback((MutableText) cmd);
			if (client != null && client.player != null) {
				client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
			}
		}

		return Command.SINGLE_SUCCESS;
	}

	private int resetSystemMessage(CommandContext<?> context) {
		SYSTEM_MESSAGE = DEFAULT_SYSTEM_MESSAGE;

		JsonObject config = readConfig();
		config.addProperty("systemMessage", SYSTEM_MESSAGE);
		saveConfig(config);

		sendFeedback(Text.literal(PREFIX + "§aSystem message reset to default: §f" + DEFAULT_SYSTEM_MESSAGE));
        LOGGER.info("System message reset to default: {}", DEFAULT_SYSTEM_MESSAGE);

		return Command.SINGLE_SUCCESS;
	}

	private int setupMaxOutput(CommandContext<?> context) {
		int tokens = IntegerArgumentType.getInteger(context, "tokens");

		JsonObject config = readConfig();
		config.addProperty("maxOutputTokens", tokens);
		saveConfig(config);

		sendFeedback(Text.literal(PREFIX + "§aMax output tokens set to: §f" + tokens));
		return Command.SINGLE_SUCCESS;
	}

	private int setupCommandExecution(CommandContext<?> context) {
		JsonObject config = readConfig();
		boolean enabled = !COMMAND_EXECUTION_ENABLED;
		COMMAND_EXECUTION_ENABLED = enabled;
		config.addProperty("commandExecutionEnabled", enabled);

		saveConfig(config);

		if (enabled) {
			sendFeedback(Text.literal(PREFIX + "§aCommand execution §2enabled§a. AI can now run game commands."));
		} else {
			sendFeedback(Text.literal(PREFIX + "§aCommand execution §cdisabled§a."));
		}
		return Command.SINGLE_SUCCESS;
	}

	private int showHistory(CommandContext<?> context) {
		UUID playerUuid = Objects.requireNonNull(MinecraftClient.getInstance().player).getUuid();

		if (!MEMORY_ENABLED) {
			sendFeedback(Text.literal(PREFIX + "§cMemory is currently disabled."));
			return Command.SINGLE_SUCCESS;
		}

		if (!conversationHistory.containsKey(playerUuid) || conversationHistory.get(playerUuid).isEmpty()) {
			sendFeedback(Text.literal(PREFIX + "§cNo conversation history found."));
			return Command.SINGLE_SUCCESS;
		}

		List<JsonObject> history = conversationHistory.get(playerUuid);
		sendFeedback(Text.literal(PREFIX + "§6=== Conversation History ==="));

        for (JsonObject message : history) {
            String role = message.get("role").getAsString();
            String text = message.getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();

            if (role.equals("user")) {
                sendFeedback(Text.literal("§e You: §f" + text));
            } else {
                Style clickableStyle = Style.EMPTY
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§aClick to reply to this message")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ai Reply to: " + text.substring(0, Math.min(text.length(), 20)) + "..."));
                MutableText historyText = Text.literal("§b AI: §f" + text).styled(style -> clickableStyle);
                sendFeedback(historyText);
            }
        }

		return Command.SINGLE_SUCCESS;
	}

	private void loadConfig() {
		JsonObject config = readConfig();

		MODEL_NAME = config.has("model") ? config.get("model").getAsString() : "gemini-1.5-flash";
		GEMINI_API_KEY = config.has("apiKey") ? config.get("apiKey").getAsString() : "YOUR_API_KEY_HERE";
		SYSTEM_MESSAGE = config.has("systemMessage") ? config.get("systemMessage").getAsString() : DEFAULT_SYSTEM_MESSAGE;
		MEMORY_ENABLED = config.has("memoryEnabled") && config.get("memoryEnabled").getAsBoolean();
		MEMORY_SIZE = config.has("memorySize") ? config.get("memorySize").getAsInt() : 20;
		COMMAND_EXECUTION_ENABLED = config.has("commandExecutionEnabled") && config.get("commandExecutionEnabled").getAsBoolean();
		int maxOutputTokens = config.has("maxOutputTokens") ? config.get("maxOutputTokens").getAsInt() : 200;

		GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_NAME + ":generateContent";
        LOGGER.info("Configuration loaded. Model: {}, Memory: {}, Commands: {}, Max Tokens: {}", MODEL_NAME, MEMORY_ENABLED ? "enabled (" + MEMORY_SIZE + ")" : "disabled", COMMAND_EXECUTION_ENABLED ? "enabled" : "disabled", maxOutputTokens);
	}

	private JsonObject readConfig() {
		try {
			if (Files.exists(CONFIG_PATH)) {
				String jsonContent = Files.readString(CONFIG_PATH);
				return JsonParser.parseString(jsonContent).getAsJsonObject();
			}
		} catch (Exception e) {
			LOGGER.error("Error reading configuration!", e);
		}

		JsonObject defaultConfig = new JsonObject();
		defaultConfig.addProperty("model", "gemini-1.5-flash");
		defaultConfig.addProperty("apiKey", "YOUR_API_KEY_HERE");
		defaultConfig.addProperty("systemMessage", DEFAULT_SYSTEM_MESSAGE);
		defaultConfig.addProperty("memoryEnabled", false);
		defaultConfig.addProperty("memorySize", 20);
		defaultConfig.addProperty("maxOutputTokens", 200);
		defaultConfig.addProperty("commandExecutionEnabled", false);

		saveConfig(defaultConfig);
		return defaultConfig;
	}

	private void saveConfig(JsonObject config) {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, new GsonBuilder().setPrettyPrinting().create().toJson(config));
			LOGGER.info("Configuration saved");
		} catch (Exception e) {
			LOGGER.error("Error saving configuration!", e);
		}
	}

	private void saveConfig() {
		JsonObject config = new JsonObject();
		config.addProperty("model", MODEL_NAME);
		config.addProperty("apiKey", GEMINI_API_KEY);
		config.addProperty("systemMessage", SYSTEM_MESSAGE);
		config.addProperty("memoryEnabled", MEMORY_ENABLED);
		config.addProperty("memorySize", MEMORY_SIZE);
		config.addProperty("maxOutputTokens", 200);
		config.addProperty("commandExecutionEnabled", COMMAND_EXECUTION_ENABLED);

		saveConfig(config);
	}

	private int reloadConfig(CommandContext<FabricClientCommandSource> context) {
		loadConfig();
		sendFeedback(Text.literal(PREFIX + "Configuration reloaded!"));
		return 1;
	}

	private int setupApiToken(CommandContext<?> context) {
		String token = StringArgumentType.getString(context, "apiToken");
		GEMINI_API_KEY = token;
		sendFeedback(Text.literal(PREFIX + "§aAPI token set!"));
        LOGGER.info("API token set: {}...", token.substring(0, Math.min(5, token.length())));
		saveConfig();
		return Command.SINGLE_SUCCESS;
	}

	private int setupModel(CommandContext<?> context) {
		String modelName = StringArgumentType.getString(context, "modelName");
		MODEL_NAME = modelName;

		GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_NAME + ":generateContent";
		sendFeedback(Text.literal(PREFIX + "§aModel set: §f" + modelName));
        LOGGER.info("Model set: {}", modelName);
		saveConfig();
		return Command.SINGLE_SUCCESS;
	}

	private int setupSystemMessage(CommandContext<?> context) {
		String systemMessage = StringArgumentType.getString(context, "systemMessage");
		SYSTEM_MESSAGE = systemMessage;

		if (COMMAND_EXECUTION_ENABLED && !SYSTEM_MESSAGE.contains(AI_COMMAND_SYSTEM_PROMPT)) {
			SYSTEM_MESSAGE = SYSTEM_MESSAGE + "\n\n" + AI_COMMAND_SYSTEM_PROMPT;
		}

		sendFeedback(Text.literal(PREFIX + "§aSystem message set!"));
        LOGGER.info("System message set: {}", systemMessage);
		saveConfig();
		return Command.SINGLE_SUCCESS;
	}

	private String getFullSystemMessage() {
		if (COMMAND_EXECUTION_ENABLED) {
			return SYSTEM_MESSAGE + "\n\n" + AI_COMMAND_SYSTEM_PROMPT;
		}
		return SYSTEM_MESSAGE;
	}

	private int setupMemory(CommandContext<?> context) {
		boolean enabled = BoolArgumentType.getBool(context, "enabled");
		int size = IntegerArgumentType.getInteger(context, "size");
		MEMORY_ENABLED = enabled;
		MEMORY_SIZE = size;

		sendFeedback(Text.literal(PREFIX + "§aMemory " +
				(enabled ? "enabled" : "disabled") + ", size: " + size));
        LOGGER.info("Memory settings changed: {}, size: {}", enabled ? "enabled" : "disabled", size);

		if (!enabled) {
			conversationHistory.clear();
		}

		saveConfig();
		return Command.SINGLE_SUCCESS;
	}

	private int clearMemory(CommandContext<?> context) {
		UUID playerUuid = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getUuid() : null;
		conversationHistory.remove(playerUuid);

		sendFeedback(Text.literal(PREFIX + "§aConversation history cleared!"));
        LOGGER.info("Conversation history cleared for player: {}", MinecraftClient.getInstance().player.getName().getString());

		return Command.SINGLE_SUCCESS;
	}

	private int executeAiCommand(CommandContext<?> context) {
		String query = StringArgumentType.getString(context, "query");
		UUID playerUuid = Objects.requireNonNull(MinecraftClient.getInstance().player).getUuid();
		executeAiQuery(query, playerUuid);
		return Command.SINGLE_SUCCESS;
	}

	private void executeAiQuery(String query, UUID playerUuid) {
        LOGGER.info("Query to AI: {}", query);

        var loadingMessage = Text.literal(PREFIX + "§6Sending request to AI... §7⏳");
		sendFeedback(loadingMessage);

		sendFeedback(Text.literal("§e You: §f" + query));

		CompletableFuture.supplyAsync(() -> {
			try {
				return sendMessageToGemini(query, playerUuid);
			} catch (Exception e) {
				LOGGER.error("Error querying AI!", e);
				return "§cError: " + e.getMessage();
			}
		}).thenAccept(response -> {
			try {
				JsonObject parsedResponse = JsonParser.parseString(response).getAsJsonObject();
				String textResponse = parseGeminiResponse(parsedResponse);

				if (MEMORY_ENABLED) {
					storeConversation(playerUuid, createUserMessageObject(query), createAssistantMessageObject(textResponse));
				}

				MinecraftClient.getInstance().execute(() -> {
					String processedResponse = processResponse(textResponse);
					displayResponse(processedResponse);
					executeCommandsIfPresent(textResponse);
				});
			} catch (Exception e) {
				MinecraftClient.getInstance().execute(() -> sendFeedback(Text.literal(PREFIX + "§c❌ Error processing response: " + e.getMessage())));
				LOGGER.error("Error processing AI response!", e);
			}
		});
	}

	private String processResponse(String response) {
		if (response.contains("```minecraft")) {
			return PREFIX + response;
		}

		String formattedResponse = response
				.replaceAll("\\*\\*(.+?)\\*\\*", "§l$1§r")
				.replaceAll("\\*(.+?)\\*", "§o$1§r")
				.replaceAll("__(.+?)__", "§n$1§r")
				.replaceAll("~~(.+?)~~", "§m$1§r");

		formattedResponse = formattedResponse
				.replaceAll("\\\\\\*", "*")
				.replaceAll("\\\\_", "_")
				.replaceAll("\\\\~", "~")
				.replaceAll("(§[lonm])([^§]+)(?!§r)", "$1$2§r");

		if (formattedResponse.lastIndexOf("§") > formattedResponse.lastIndexOf("§r")) {
			formattedResponse += "§r";
		}

		return PREFIX + formattedResponse;
	}

	private void executeCommandsIfPresent(String response) {
		if (!COMMAND_EXECUTION_ENABLED) return;

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}

		Pattern pattern = Pattern.compile("```minecraft\\s*\\n(.*?)\\n```", Pattern.DOTALL);
		Matcher matcher = pattern.matcher(response);

		while (matcher.find()) {
			String commandBlock = matcher.group(1);
			String[] commands = commandBlock.split("\\n");

			for (String command : commands) {
				command = command.trim();
				if (!command.isEmpty()) {
					sendFeedback(Text.literal("§d[AI Command]§r Executing: §7/" + command));

					try {
                        LOGGER.info("AI executing command: {}", command);
						Objects.requireNonNull(client.getNetworkHandler()).sendCommand(command);
					} catch (Exception e) {
						sendFeedback(Text.literal("§c❌ Command execution error: " + e.getMessage()));
						LOGGER.error("Command execution error", e);
					}
				}
			}
		}
	}

	private void displayResponse(String response) {
		Style clickableStyle = Style.EMPTY
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(HOVER_COLOR + "Click to follow up on this response")))
				.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ai "));

		var responseText = Text.literal(PREFIX + TEXT_COLOR + response).styled(style -> clickableStyle);
		sendFeedback(responseText);

		playResponseSound();
	}

	private void playResponseSound() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && client.world != null) {
			BlockPos playerPos = client.player.getBlockPos();
			client.world.playSound(
					playerPos.getX(), playerPos.getY(), playerPos.getZ(),
					SoundEvents.ENTITY_ITEM_PICKUP,
					SoundCategory.PLAYERS,
					1.0F,
					1.0F,
					false
			);
		}
	}

	private void sendFeedback(MutableText message) {
		if (MinecraftClient.getInstance().player != null) {
			MinecraftClient.getInstance().player.sendMessage(message, false);
		}
	}

	private void storeConversation(UUID playerUuid, JsonObject userMessage, JsonObject assistantMessage) {
		conversationHistory.putIfAbsent(playerUuid, new ArrayList<>());
		List<JsonObject> history = conversationHistory.get(playerUuid);

		history.add(userMessage);
		history.add(assistantMessage);

		while (history.size() > MEMORY_SIZE * 2) {
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

	private String processVariables(String text) {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player == null || client.world == null) {
			return text;
		}

		try {
			String username = client.player.getName().getString();
			text = text.replace("{username}", username);

			long worldTime = client.world.getTime();
			text = text.replace("{time}", String.valueOf(worldTime));
			text = text.replace("{timeOfDay}", (worldTime % 24000 < 12000) ? "day" : "night");

			Vec3d pos = client.player.getPos();
			text = text.replace("{x}", String.format("%.2f", pos.x));
			text = text.replace("{y}", String.format("%.2f", pos.y));
			text = text.replace("{z}", String.format("%.2f", pos.z));

			text = text.replace("{health}", String.format("%.1f", client.player.getHealth()));
			text = text.replace("{food}", String.valueOf(client.player.getHungerManager().getFoodLevel()));
			text = text.replace("{xp}", String.valueOf(client.player.experienceLevel));

			text = text.replace("{dimension}", client.world.getRegistryKey().getValue().toString());
			text = text.replace("{difficulty}", client.world.getDifficulty().getName());
			text = text.replace("{weather}", client.world.isRaining() ? "raining" : "clear");

			if (client.interactionManager != null) {
				text = text.replace("{gameMode}", client.interactionManager.getCurrentGameMode().getName());
			}

			ItemStack mainHand = client.player.getMainHandStack();
			text = text.replace("{mainHand}", mainHand.isEmpty() ? "nothing" :
					String.format("%dx %s", mainHand.getCount(), mainHand.getItem().getName().getString()));

			ItemStack offHand = client.player.getOffHandStack();
			text = text.replace("{offHand}", offHand.isEmpty() ? "nothing" :
					String.format("%dx %s", offHand.getCount(), offHand.getItem().getName().getString()));

			PlayerInventory inventory = client.player.getInventory();
			StringBuilder inventoryContent = new StringBuilder();
			for (int i = 0; i < inventory.main.size(); i++) {
				ItemStack stack = inventory.main.get(i);
				if (!stack.isEmpty()) {
					if (!inventoryContent.isEmpty()) {
						inventoryContent.append(", ");
					}
					inventoryContent.append(stack.getCount())
							.append("x ")
							.append(stack.getItem().getName().getString());
				}
			}
			text = text.replace("{inventory}", !inventoryContent.isEmpty() ?
					inventoryContent.toString() : "empty");

			StringBuilder armorContent = new StringBuilder();
			for (ItemStack armorItem : client.player.getInventory().armor) {
				if (!armorItem.isEmpty()) {
					if (!armorContent.isEmpty()) {
						armorContent.append(", ");
					}
					int maxDurability = armorItem.getMaxDamage();
					int currentDurability = maxDurability - armorItem.getDamage();
					armorContent.append(armorItem.getItem().getName().getString())
							.append(" (")
							.append(currentDurability)
							.append("/")
							.append(maxDurability)
							.append(")");
				}
			}
			text = text.replace("{armor}", !armorContent.isEmpty() ?
					armorContent.toString() : "no armor");

			PlayerListEntry playerEntry = Objects.requireNonNull(client.getNetworkHandler()).getPlayerListEntry(client.player.getUuid());
			if (playerEntry != null) {
				text = text.replace("{ping}", String.valueOf(playerEntry.getLatency()));
			}

			if (client.getNetworkHandler() != null) {
				int playerCount = client.getNetworkHandler().getPlayerList().size();
				text = text.replace("{playerCount}", String.valueOf(playerCount));

				StringBuilder playerList = new StringBuilder();
				for (PlayerListEntry player : client.getNetworkHandler().getPlayerList()) {
					playerList.append(player.getProfile().getName()).append(", ");
				}
				String players = !playerList.isEmpty() ?
						playerList.substring(0, playerList.length() - 2) :
						"no other players";
				text = text.replace("{playerList}", players);
			}

		} catch (Exception e) {
			LOGGER.error("Error processing variables", e);

		}

		return text;
	}

	public String sendMessageToGemini(String userMessage, UUID playerUuid) throws Exception {
		JsonObject requestBody = new JsonObject();
		JsonArray contents = new JsonArray();

		if (MEMORY_ENABLED && conversationHistory.containsKey(playerUuid)) {
			contents.addAll(new Gson().fromJson(new Gson().toJson(conversationHistory.get(playerUuid)), JsonArray.class));
		}

		JsonObject userMsgObj = createUserMessageObject(userMessage);
		contents.add(userMsgObj);

		requestBody.add("contents", contents);

		String processedSystemMessage = processVariables(getFullSystemMessage());

		JsonObject systemInstruction = new JsonObject();
		JsonArray systemParts = new JsonArray();
		JsonObject systemPart = new JsonObject();
		systemPart.addProperty("text", processedSystemMessage);
		systemParts.add(systemPart);
		systemInstruction.add("parts", systemParts);
		requestBody.add("systemInstruction", systemInstruction);

		JsonObject config = readConfig();
		int maxOutputTokens = config.has("maxOutputTokens") ? config.get("maxOutputTokens").getAsInt() : 200;

		JsonObject generationConfig = new JsonObject();
		generationConfig.addProperty("maxOutputTokens", maxOutputTokens);
		requestBody.add("generationConfig", generationConfig);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(GEMINI_API_URL + "?key=" + GEMINI_API_KEY))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        LOGGER.info("AI HTTP Status: {}", response.statusCode());

		if (response.statusCode() == 200) {
			return response.body();
		} else {
			throw new RuntimeException("Request error! Status code: " + response.statusCode() + ", body: " + response.body());
		}
	}

	private String parseGeminiResponse(JsonObject jsonResponse) {
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
}