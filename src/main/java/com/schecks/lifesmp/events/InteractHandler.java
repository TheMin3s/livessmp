package com.schecks.lifesmp.events;

import com.schecks.lifesmp.LifeConfig;
import com.schecks.lifesmp.LifeItems;
import com.schecks.lifesmp.LifeLog;
import com.schecks.lifesmp.LifeUtil;
import com.schecks.lifesmp.LivesData;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Right-click handling for LifeSMP items. Currently: right-clicking a Life
 * Shard deposits one life (consumes one shard), as a shortcut for
 * /life deposit. The command still works.
 */
public final class InteractHandler {
    private InteractHandler() {}

    public static void register() {
        UseItemCallback.EVENT.register(InteractHandler::onUseItem);
    }

    private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        ItemStack held = player.getItemInHand(hand);
        if (!LifeItems.isLifeShard(held)) return InteractionResult.PASS;

        MinecraftServer server = sp.level().getServer();
        if (server == null) return InteractionResult.PASS;

        LivesData data = LivesData.get(server);
        int current = data.getLives(sp.getUUID());
        int max = LifeConfig.get().maxLives;
        if (current >= max) {
            sp.sendSystemMessage(Component.literal("You are already at max lives (" + max + ").")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)), true);
            return InteractionResult.FAIL;
        }

        data.addLives(sp.getUUID(), 1);
        // Remove via the canonical Inventory.removeItem on the hand's slot —
        // shrink() on the held reference wasn't reliably propagating to the
        // client's inventory view.
        net.minecraft.world.entity.player.Inventory inv = sp.getInventory();
        int slot = (hand == InteractionHand.MAIN_HAND)
            ? inv.getSelectedSlot()
            : inv.getContainerSize() - 1;   // offhand is the last slot
        if (slot >= 0 && slot < inv.getContainerSize()) {
            inv.removeItem(slot, 1);
        }
        inv.setChanged();
        sp.containerMenu.broadcastFullState();
        LifeUtil.refreshTabName(server, sp);
        int now = data.getLives(sp.getUUID());
        LifeLog.info("[lifesmp] {} deposited 1 life by right-click (now {})",
            sp.getGameProfile().name(), now);
        sp.sendSystemMessage(Component.literal("Deposited 1 life — you now have " + now + ".")
            .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)), true);
        return InteractionResult.SUCCESS_SERVER;
    }
}
