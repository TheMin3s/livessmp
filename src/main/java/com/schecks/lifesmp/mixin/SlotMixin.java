package com.schecks.lifesmp.mixin;

import com.schecks.lifesmp.LifeConfig;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks taking a crafted Life Shard once a player has hit the lifetime
 * craft limit.
 *
 * mayPickup is declared on Slot — ResultSlot only inherits it — so a mixin
 * targeting ResultSlot can't @Inject into it (Mixin only matches methods
 * declared on the target class). This mixin therefore targets Slot and
 * filters to crafting result slots via instanceof. The counting half lives
 * in CraftingResultSlotMixin (onTake is declared on ResultSlot).
 */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void lifesmp$blockLifeShardWhenAtLimit(Player playerEntity, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ResultSlot)) return;   // crafting result slots only
        if (!(playerEntity instanceof ServerPlayer sp)) return;
        if (sp.level().getServer() == null) return;
        ItemStack stack = ((Slot) (Object) this).getItem();
        if (!LifeItems.isLifeShard(stack)) return;
        int craftLimit = LifeConfig.get().craftLimit;
        if (LivesData.get(sp.level().getServer()).getCrafted(sp.getUUID()) >= craftLimit) {
            sp.sendSystemMessage(
                Component.literal("You have reached the lifetime crafting limit ("
                        + craftLimit + ") for Life Shards.")
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF4C4C))),
                true
            );
            cir.setReturnValue(false);
        }
    }
}
