package ua.geminiinminecraft;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.util.HashSet;
import java.util.Set;

public class PlayerDetector {
    private static final Set<String> detectedPlayers = new HashSet<>();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getNetworkHandler() != null && client.player != null && client.player.age % 100 == 0) {
                checkForPlayers(client);
            }
        });
    }

    private static void checkForPlayers(MinecraftClient client) {
        Set<String> currentPlayers = new HashSet<>();

        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().getName();
            if (name != null) {
                currentPlayers.add(name.toLowerCase());

                if (!detectedPlayers.contains(name.toLowerCase())) {
                    detectedPlayers.add(name.toLowerCase());
                    GeminiInMinecraftClient.addOnlinePlayer(name);
                }
            }
        }
    }

    public static void reset() {
        detectedPlayers.clear();
    }
}