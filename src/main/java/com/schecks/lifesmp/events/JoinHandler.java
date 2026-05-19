package com.schecks.lifesmp.events;

import com.schecks.lifesmp.LifeLog;
import com.schecks.lifesmp.LifeUtil;
import com.schecks.lifesmp.LivesData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
        });
    }

    private static void initialiseAndAnnounce(MinecraftServer server, ServerPlayer player) {
        LivesData data = LivesData.get(server);
        data.updateName(player.getUUID(), player.getGameProfile().name());
        boolean first = !data.getOrCreate(player.getUUID()).initialised;
        if (first) {
            data.setLives(player.getUUID(), LivesData.DEFAULT_LIVES);
            data.markInitialised(player.getUUID());
            LifeLog.info("[lifesmp] {} joined for the first time — initialised at {} lives",
                player.getGameProfile().name(), LivesData.DEFAULT_LIVES);
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
