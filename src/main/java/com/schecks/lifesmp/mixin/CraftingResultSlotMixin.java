package com.schecks.lifesmp.mixin;

import com.schecks.lifesmp.LifeItems;
import com.schecks.lifesmp.LivesData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResultSlot.class)
public abstract class CraftingResultSlotMixin {
    @Shadow @Final private Player player;

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void lifesmp$blockLifeShardWhenAtLimit(Player playerEntity, CallbackInfoReturnable<Boolean> cir) {
        if (!(playerEntity instanceof ServerPlayer sp)) return;
        if (sp.level().getServer() == null) return;
        ItemStack stack = ((Slot)(Object)this).getItem();
        if (!LifeItems.isLifeShard(stack)) return;
        LivesData data = LivesData.get(sp.level().getServer());
        if (data.getCrafted(sp.getUUID()) >= LivesData.CRAFT_LIMIT) {
            sp.sendSystemMessage(
                Component.literal("You have reached the lifetime crafting limit (" + LivesData.CRAFT_LIMIT + ") for Life Shards.")
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF4C4C))),
                true
            );
            cir.setReturnValue(false);
        }
    }

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
        int newRemaining = data.remainingCrafts(sp.getUUID());
        if (newRemaining == 0) {
            sp.sendSystemMessage(
                Component.literal("You have crafted the maximum number of Life Shards.")
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFE17B))),
                true
            );
        }
    }
}
