package com.schecks.lifesmp;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public final class LifeUtil {
    private LifeUtil() {}

    /**
     * Resolves a player name to a NameAndId record.
     *
     * Order of lookup:
     *  1. Online ServerPlayer via PlayerList.getPlayerByName
     *  2. Cached UUID + last known name in LivesData (covers banned/offline players we've seen)
     *
     * Returns null if the player has never joined this server.
     */
    public static NameAndId resolveNameAndId(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return new NameAndId(online.getUUID(), online.getGameProfile().name());
        }
        UUID cached = LivesData.get(server).findByName(name);
        if (cached != null) {
            String cachedName = LivesData.get(server).getOrCreate(cached).lastKnownName;
            return new NameAndId(cached, cachedName.isEmpty() ? name : cachedName);
        }
        return null;
    }

    public static boolean isBanned(MinecraftServer server, NameAndId nameAndId) {
        return server.getPlayerList().getBans().isBanned(nameAndId);
    }

    public static void banForOutOfLives(MinecraftServer server, NameAndId nameAndId) {
        PlayerList pl = server.getPlayerList();
        UserBanList bans = pl.getBans();
        if (!bans.isBanned(nameAndId)) {
            UserBanListEntry entry = new UserBanListEntry(
                nameAndId,
                null,
                "lifesmp",
                null,
                "You ran out of lives."
            );
            bans.add(entry);
        }
        ServerPlayer online = pl.getPlayer(nameAndId.id());
        if (online != null) {
            online.connection.disconnect(Component.literal("You ran out of lives."));
        }
    }

    public static void unban(MinecraftServer server, NameAndId nameAndId) {
        UserBanList bans = server.getPlayerList().getBans();
        UserBanListEntry entry = bans.get(nameAndId);
        if (entry != null) bans.remove(nameAndId);
    }

    public static void refreshTabName(MinecraftServer server, ServerPlayer player) {
        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
            List.of(player)
        );
        server.getPlayerList().broadcastAll(packet);
    }

    public static void refreshAllTabs(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            refreshTabName(server, p);
        }
    }
}
