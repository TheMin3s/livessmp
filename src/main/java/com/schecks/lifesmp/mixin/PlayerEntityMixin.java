package com.schecks.lifesmp.mixin;

import com.schecks.lifesmp.LivesData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void lifesmp$injectListName(CallbackInfoReturnable<Component> cir) {
        ServerPlayer sp = (ServerPlayer)(Object) this;
        var server = sp.level().getServer();
        if (server == null) return;
        int lives = LivesData.get(server).getLives(sp.getUUID());

        MutableComponent prefix = Component.literal("[" + lives + "❤] ")
            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF4C4C)));
        MutableComponent nameText = Component.literal(sp.getGameProfile().name())
            .setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
        cir.setReturnValue(prefix.append(nameText));
    }
}
