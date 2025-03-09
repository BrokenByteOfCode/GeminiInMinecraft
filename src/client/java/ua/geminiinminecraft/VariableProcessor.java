package ua.geminiinminecraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;

import java.util.Objects;

public class VariableProcessor {
    private static final Logger LOGGER = GeminiInMinecraftClient.LOGGER;

    public static String processVariables(String text) {
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
}