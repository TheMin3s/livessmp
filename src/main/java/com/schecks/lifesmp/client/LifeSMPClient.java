package com.schecks.lifesmp.client;

import com.schecks.lifesmp.FileTransferPayload;
import com.schecks.lifesmp.LifeItems;
import com.schecks.lifesmp.LivesPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Client-side half of LifeSMP:
 *  - receives lives updates from the server and draws a compact hardcore-heart
 *    counter on the HUD, just above the armor bar;
 *  - receives offered-file transfers and shows a download confirmation;
 *  - opens the Revival Crystal name-entry screen on right-click.
 *
 * This class (and everything in the client package) is only loaded on a
 * physical client — the dedicated server never touches it.
 */
public class LifeSMPClient implements ClientModInitializer {
    private static final Identifier HUD_ID = Identifier.parse("lifesmp:lives_hud");
    /** Vanilla hardcore-heart GUI sprite (9x9). */
    private static final Identifier HARDCORE_HEART =
        Identifier.parse("minecraft:hud/heart/hardcore_full");

    @Override
    public void onInitializeClient() {
        // Receive lives updates pushed by the server.
        ClientPlayNetworking.registerGlobalReceiver(LivesPayload.TYPE, (payload, context) ->
            ClientLivesState.update(payload.lives(), payload.maxLives()));

        // Receive shared-file transfers; hop to the main thread to show the
        // confirmation screen.
        ClientPlayNetworking.registerGlobalReceiver(FileTransferPayload.TYPE, (payload, context) ->
            context.client().execute(() -> FileDownloadHandler.handle(payload)));

        // Right-clicking a Revival Crystal opens a name-entry screen that runs
        // /life crystal <name> for you. The command still does all the work.
        UseItemCallback.EVENT.register(LifeSMPClient::onUseItem);

        // Draw the heart counter immediately after (i.e. just above) the armor bar.
        HudElement element = (graphics, deltaTracker) -> renderLivesBar(graphics);
        HudElementRegistry.attachElementAfter(VanillaHudElements.ARMOR_BAR, HUD_ID, element);
    }

    private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
        if (!level.isClientSide()) return InteractionResult.PASS;
        if (!LifeItems.isRevivalCrystal(player.getItemInHand(hand))) return InteractionResult.PASS;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new RevivalCrystalScreen()));
        return InteractionResult.SUCCESS;
    }

    private static void renderLivesBar(GuiGraphicsExtractor g) {
        if (!ClientLivesState.hasData()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;

        int lives = ClientLivesState.lives();
        Font font = mc.font;
        int x = g.guiWidth() / 2 - 91;     // left edge of the hotbar
        int y = g.guiHeight() - 59;        // one row above the armor bar

        // Hardcore heart icon + "x<lives>" — compact, armor-bar styled.
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HARDCORE_HEART, x, y, 9, 9);
        g.text(font, Component.literal("x" + lives), x + 12, y + 1, 0xFFFFFFFF, true);
    }
}
