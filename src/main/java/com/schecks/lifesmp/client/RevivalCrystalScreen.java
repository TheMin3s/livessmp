package com.schecks.lifesmp.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Modded-client convenience UI: right-clicking a Revival Crystal opens this
 * screen to type a player name, then runs /life crystal &lt;name&gt; for you.
 * The command — and the crystal consumption — is still handled entirely by
 * the server; this screen only saves you from typing the command out.
 */
public final class RevivalCrystalScreen extends Screen {
    private static final int KEY_ENTER    = 257;   // GLFW_KEY_ENTER
    private static final int KEY_KP_ENTER = 335;   // GLFW_KEY_KP_ENTER

    private EditBox nameField;
    private Button reviveButton;

    public RevivalCrystalScreen() {
        super(Component.literal("Revival Crystal"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        nameField = new EditBox(this.font, cx - 100, cy - 10, 200, 20,
            Component.literal("Player name"));
        nameField.setMaxLength(16);
        nameField.setHint(Component.literal("Player name"));
        nameField.setResponder(s -> refresh());
        addRenderableWidget(nameField);

        reviveButton = Button.builder(Component.literal("Revive"), b -> submit())
            .bounds(cx - 100, cy + 18, 98, 20)
            .build();
        addRenderableWidget(reviveButton);

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
            .bounds(cx + 2, cy + 18, 98, 20)
            .build());

        setInitialFocus(nameField);
        refresh();
    }

    /** Enables the Revive button only when a name has been entered. */
    private void refresh() {
        if (reviveButton != null && nameField != null) {
            reviveButton.active = !nameField.getValue().isBlank();
        }
    }

    private void submit() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) return;
        ClientPacketListener conn = this.minecraft == null ? null : this.minecraft.getConnection();
        if (conn != null) {
            conn.sendCommand("life crystal " + name);
        }
        onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == KEY_ENTER || event.key() == KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int cy = this.height / 2;
        g.centeredText(this.font, this.title, cx, cy - 42, 0xFFFFE17B);
        g.centeredText(this.font,
            Component.literal("Enter the player you want to revive"),
            cx, cy - 28, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
