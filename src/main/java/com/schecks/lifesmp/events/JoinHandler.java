package com.schecks.lifesmp.events;

import com.schecks.lifesmp.LifeConfig;
import com.schecks.lifesmp.LifeLog;
import com.schecks.lifesmp.LifeUtil;
import com.schecks.lifesmp.LivesData;
import com.schecks.lifesmp.LivesNet;
import com.schecks.lifesmp.MaskConfig;
import com.schecks.lifesmp.ServerVersionPayload;
import com.schecks.lifesmp.UpdateChecker;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class JoinHandler {
    private JoinHandler() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            initialiseAndAnnounce(server, player);
            LifeUtil.refreshAllTabs(server);
            // Push the joining player's own lives HUD / action-bar fallback.
            LivesNet.notifyLivesChanged(server, player);
            // Modded clients self-sync; vanilla clients get a chat warning.
            if (ServerPlayNetworking.canSend(player, ServerVersionPayload.TYPE)) {
                ServerPlayNetworking.send(player,
                    new ServerVersionPayload(UpdateChecker.currentVersion()));
            } else {
                sendVanillaClientWarning(player);
            }
            LifeUtil.applySpawnImmunity(player);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
            LifeUtil.applySpawnImmunity(newPlayer));
        // Damage cancellation during the window is enforced by
        // ServerPlayerHurtMixin — Fabric's ALLOW_DAMAGE only fires on
        // LivingEntity.hurtServer, which ServerPlayer overrides past.
        // Clean up the per-player timestamp map on disconnect.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            LifeUtil.clearSpawnImmunity(handler.getPlayer().getUUID()));
    }

    /** Three chat lines nudging a vanilla client to install the LifeSMP mod. */
    private static void sendVanillaClientWarning(ServerPlayer player) {
        String url = "https://github.com/" + LifeConfig.get().updateRepo + "/releases";
        player.sendSystemMessage(
            Component.literal("[LifeSMP] ").setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD))
                .append(Component.literal("This server uses LifeSMP, but your client doesn't have the mod.")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))));
        player.sendSystemMessage(
            Component.literal("Install it from ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                .append(Component.literal(url).setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA))));
        player.sendSystemMessage(
            Component.literal("You may not be able to join in the future without it.")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
    }

    private static void initialiseAndAnnounce(MinecraftServer server, ServerPlayer player) {
        LivesData data = LivesData.get(server);
        data.updateName(player.getUUID(), player.getGameProfile().name());
        // If any active mask was targeting this player's real name, drop it —
        // the real account always wins, so no impersonation at join time.
        MaskConfig.onPlayerJoined(player.getUUID(), player.getGameProfile().name());
        boolean first = !data.getOrCreate(player.getUUID()).initialised;
        if (first) {
            int startLives = LifeConfig.get().defaultLives;
            data.setLives(player.getUUID(), startLives);
            data.markInitialised(player.getUUID());
            LifeLog.info("[lifesmp] {} joined for the first time — initialised at {} lives",
                player.getGameProfile().name(), startLives);
        } else {
            LifeLog.info("[lifesmp] {} rejoined ({} lives)",
                player.getGameProfile().name(), data.getLives(player.getUUID()));
        }
        int lives = data.getLives(player.getUUID());
        player.sendSystemMessage(
            Component.literal("You have ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                .append(Component.literal(lives + " lives").setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
                .append(Component.literal(" remaining.").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
        );
    }
}
