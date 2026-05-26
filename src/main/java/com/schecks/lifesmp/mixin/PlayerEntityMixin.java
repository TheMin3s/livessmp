package com.schecks.lifesmp.mixin;

import com.schecks.lifesmp.LifeConfig;
import com.schecks.lifesmp.LivesData;
import com.schecks.lifesmp.MaskConfig;
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
        boolean withLives = LifeConfig.get().tablistDisplay;
        String mask = MaskConfig.maskFor(sp.getUUID());
        if (!withLives && mask == null) return;          // vanilla — neither feature active

        String displayName = mask != null ? mask : sp.getGameProfile().name();
        MutableComponent nameText = Component.literal(displayName)
            .setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));

        if (withLives) {
            var server = sp.level().getServer();
            if (server == null) {
                cir.setReturnValue(nameText);
                return;
            }
            int lives = LivesData.get(server).getLives(sp.getUUID());
            MutableComponent prefix = Component.literal("[" + lives + "❤] ")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF4C4C)));
            cir.setReturnValue(prefix.append(nameText));
        } else {
            cir.setReturnValue(nameText);
        }
    }
}
