package com.schecks.lifesmp.mixin;

import com.schecks.lifesmp.LifeItems;
import com.schecks.lifesmp.LivesData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts Life Shard crafts toward the lifetime limit.
 *
 * onTake is declared on ResultSlot itself, so this mixin targets ResultSlot
 * directly. The complementary "block when at limit" check lives in SlotMixin
 * because mayPickup is only declared on the Slot superclass.
 */
@Mixin(ResultSlot.class)
public abstract class CraftingResultSlotMixin {
    @Inject(method = "onTake", at = @At("HEAD"))
    private void lifesmp$countLifeShardCraft(Player playerEntity, ItemStack stack, CallbackInfo ci) {
        if (!(playerEntity instanceof ServerPlayer sp)) return;
        if (sp.level().getServer() == null) return;
        if (!LifeItems.isLifeShard(stack)) return;
        LivesData data = LivesData.get(sp.level().getServer());
        int remaining = data.remainingCrafts(sp.getUUID());
        int take = Math.min(stack.getCount(), remaining);
        if (take > 0) {
            data.addCrafted(sp.getUUID(), take);
        }
        if (data.remainingCrafts(sp.getUUID()) == 0) {
            sp.sendSystemMessage(
                Component.literal("You have crafted the maximum number of Life Shards.")
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFE17B))),
                true
            );
        }
    }
}
