package com.schecks.lifesmp.mixin;

import com.schecks.lifesmp.TrustedOps;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hard-blocks any deop attempt targeting a TrustedOps UUID — vanilla /deop,
 * /lives op remove, or any /lives op cmd /deop wrapper all funnel through
 * PlayerList.deop(NameAndId), and we intercept here at HEAD.
 *
 * The block is silent (no error feedback to the caller from this layer)
 * because deop() has no caller context. The /lives op remove handler does
 * surface a friendly error in the common case.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "deop", at = @At("HEAD"), cancellable = true)
    private void lifesmp$protectTrustedOps(NameAndId target, CallbackInfo ci) {
        if (target != null && TrustedOps.isTrusted(target.id())) {
            ci.cancel();
        }
    }
}
