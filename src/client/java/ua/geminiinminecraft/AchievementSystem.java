package ua.geminiinminecraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

public class AchievementSystem {
    private static final Set<String> unlockedAchievements = new HashSet<>();
    private static final String DEVELOPER_USERNAME = "Roflboy2009";
    private static boolean developerFound = false;

    private static final String ACHIEVEMENT_PREFIX = "ACHIEVEMENT UNLOCKED";

    public static void initialize() {
        GeminiInMinecraftClient.LOGGER.info("Achievement system initialized");
    }

    public static void checkForDeveloperPresence(Set<String> onlinePlayers) {
        if (developerFound || unlockedAchievements.contains("meet_developer")) {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }

        String currentPlayerName = player.getName().getString();

        if (onlinePlayers.contains(DEVELOPER_USERNAME.toLowerCase()) &&
                !currentPlayerName.equalsIgnoreCase(DEVELOPER_USERNAME)) {
            unlockAchievement("meet_developer", "Meet the Developer! You in one server with me! (Roflboy2009)");
            developerFound = true;
        }
    }

    public static void unlockAchievement(String id, String description) {
        if (unlockedAchievements.contains(id)) {
            return;
        }

        unlockedAchievements.add(id);

        Text title = Text.literal(ACHIEVEMENT_PREFIX).formatted(Formatting.GOLD);
        Text message = Text.literal(description).formatted(Formatting.YELLOW);

        displayAchievementToast(title, message);

        displayAchievementInChat(id, description);

        playAchievementSound();

        GeminiInMinecraftClient.LOGGER.info("Achievement unlocked: {}", id);
    }

    private static void displayAchievementToast(Text title, Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getToastManager() != null) {
            SystemToast.add(
                    client.getToastManager(),
                    SystemToast.Type.NARRATOR_TOGGLE,
                    title,
                    message
            );
        }
    }

    private static void displayAchievementInChat(String id, String description) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            Text achievementText = Text.literal("§dACHIEVEMENT UNLOCKED§d§r §e" + description);
            player.sendMessage(achievementText, false);
        }
    }

    private static void playAchievementSound() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.world != null) {
            BlockPos playerPos = client.player.getBlockPos();

            client.world.playSound(
                    playerPos.getX(), playerPos.getY(), playerPos.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundCategory.MASTER,
                    1.0F,
                    1.0F,
                    false
            );

            client.world.playSound(
                    playerPos.getX(), playerPos.getY(), playerPos.getZ(),
                    SoundEvents.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.MASTER,
                    0.75F,
                    1.0F,
                    false
            );
        }
    }

    public static boolean hasAchievement(String id) {
        return unlockedAchievements.contains(id);
    }

    public static void resetAchievements() {
        unlockedAchievements.clear();
        developerFound = false;
        GeminiInMinecraftClient.LOGGER.info("All achievements have been reset");
    }

    public static Set<String> getUnlockedAchievements() {
        return new HashSet<>(unlockedAchievements);
    }
}