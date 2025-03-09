package ua.geminiinminecraft;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

public class CommandHelper {
    private static final String PREFIX = "§b>§r ";

    public static int showHelp(CommandContext<?> context) {
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
                Text.literal("§a/setupai §fmodel <n> §7- Set AI model")
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
        }

        return 1;
    }

    private static void sendFeedback(MutableText message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
    }
}