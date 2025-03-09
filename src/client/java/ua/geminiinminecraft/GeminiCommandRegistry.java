package ua.geminiinminecraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.ArgumentTypes;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import com.mojang.brigadier.arguments.StringArgumentType;

public class GeminiCommandRegistry {

    private final GeminiInMinecraftClient client;

    public GeminiCommandRegistry(GeminiInMinecraftClient client) {
        this.client = client;
    }

    public void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register(this::registerAllCommands);
    }

    private void registerAllCommands(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                     CommandRegistryAccess registryAccess) {

        dispatcher.register(
                literal("ai")
                        .then(argument("query", StringArgumentType.greedyString())
                                .executes(client::executeAiCommand)
                        )
        );

        dispatcher.register(
                literal("aihelp")
                        .executes(client::showHelp)
        );

        dispatcher.register(
                literal("aihistory")
                        .executes(client::showHistory)
        );

        dispatcher.register(
                literal("clearmemory")
                        .executes(client::clearMemory)
        );

        dispatcher.register(
                literal("reloadconfig")
                        .executes(client::reloadConfig)
        );

        dispatcher.register(
                literal("sayinchat")
                        .then(argument("message", StringArgumentType.greedyString())
                                .executes(client::executeSayInChat)
                        )
        );

        dispatcher.register(
                literal("setupai")
                        .then(literal("apikey")
                                .then(argument("apiKey", StringArgumentType.string())
                                        .executes(client::setupApiToken)
                                )
                        )
                        .then(literal("model")
                                .then(argument("modelName", StringArgumentType.string())
                                        .executes(client::setupModel)
                                )
                        )
                        .then(literal("system")
                                .then(argument("systemMessage", StringArgumentType.greedyString())
                                        .executes(client::setupSystemMessage)
                                )
                        )
                        .then(literal("defaultsystem")
                                .executes(client::resetSystemMessage)
                        )
                        .then(literal("memory")
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .then(argument("size", IntegerArgumentType.integer(1, 50))
                                                .executes(client::setupMemory)
                                        )
                                )
                        )
                        .then(literal("maxoutput")
                                .then(argument("tokens", IntegerArgumentType.integer(50, 8046))
                                        .executes(client::setupMaxOutput)
                                )
                        )
                        .then(literal("commands")
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(client::setupCommandExecution)
                                )
                        )
        );
    }
}