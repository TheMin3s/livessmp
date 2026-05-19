package com.schecks.lifesmp.events;

import com.schecks.lifesmp.LifeItems;
import com.schecks.lifesmp.LifeLog;
import com.schecks.lifesmp.LifeUtil;
import com.schecks.lifesmp.LivesData;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public final class DeathHandler {
    private DeathHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer victim)) return;
            ServerLevel level = victim.level();
            MinecraftServer server = level.getServer();
            if (server == null) return;
            LivesData data = LivesData.get(server);

            int victimLives = data.addLives(victim.getUUID(), -1);
            LifeLog.info("[lifesmp] {} died (lives now {})", victim.getGameProfile().name(), victimLives);

            ServerPlayer killer = resolveKiller(source);
            if (killer != null && killer != victim) {
                int killerLives = data.getLives(killer.getUUID());
                if (killerLives < LivesData.MAX_LIVES) {
                    int newCount = data.addLives(killer.getUUID(), 1);
                    LifeLog.info("[lifesmp] {} killed {} (+1 life, now {})",
                        killer.getGameProfile().name(), victim.getGameProfile().name(), newCount);
                    killer.sendSystemMessage(
                        Component.literal("You gained a life from killing ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                            .append(Component.literal(victim.getGameProfile().name())
                                .setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)))
                            .append(Component.literal(" (now " + newCount + ").")
                                .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
                    );
                    LifeUtil.refreshTabName(server, killer);
                } else {
                    LifeLog.info("[lifesmp] {} killed {} but is at max lives — Life Shard dropped",
                        killer.getGameProfile().name(), victim.getGameProfile().name());
                    ItemStack shard = LifeItems.createLifeShard(1);
                    ItemEntity drop = new ItemEntity(
                        level,
                        victim.getX(), victim.getY(), victim.getZ(),
                        shard
                    );
                    drop.setDefaultPickUpDelay();
                    level.addFreshEntity(drop);
                    killer.sendSystemMessage(
                        Component.literal("You're already at max lives — a Life Shard dropped at your victim.")
                            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFE17B)))
                    );
                }
            }

            LifeUtil.refreshTabName(server, victim);

            if (victimLives <= 0) {
                final NameAndId target = new NameAndId(victim.getUUID(), victim.getGameProfile().name());
                LifeLog.info("[lifesmp] {} ran out of lives — banning", target.name());
                server.execute(() -> {
                    server.getPlayerList().broadcastSystemMessage(
                        Component.literal(target.name() + " has run out of lives.")
                            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF4C4C))),
                        false
                    );
                    LifeUtil.banForOutOfLives(server, target);
                });
            }
        });
    }

    private static ServerPlayer resolveKiller(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer sp) return sp;
        Entity direct = source.getDirectEntity();
        if (direct instanceof ServerPlayer sp) return sp;
        return null;
    }
}
