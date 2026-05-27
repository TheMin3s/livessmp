package com.schecks.lifesmp.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.schecks.lifesmp.LifeConfig;
import com.schecks.lifesmp.LifeItems;
import com.schecks.lifesmp.LifeLog;
import com.schecks.lifesmp.LifeUtil;
import com.schecks.lifesmp.LivesData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class LifeCommand {
    private LifeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("life")
            .then(Commands.literal("crystal")
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(LifeCommand::useCrystal)))
            // Bare /life withdraw defaults to 1; the explicit form takes a
            // quantity. Upper bound is a generous static literal — the real
            // limit is validated against the live config in the handler.
            .then(Commands.literal("withdraw")
                .executes(ctx -> withdraw(ctx, 1))
                .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 1000))
                    .executes(ctx -> withdraw(ctx, IntegerArgumentType.getInteger(ctx, "quantity")))))
            .then(Commands.literal("deposit")
                .executes(LifeCommand::deposit))
        );
    }

    private static int useCrystal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        MinecraftServer server = self.level().getServer();
        if (server == null) return 0;

        ItemStack held = self.getMainHandItem();
        if (!LifeItems.isRevivalCrystal(held)) {
            ctx.getSource().sendFailure(Component.literal("You must be holding a Revival Crystal in your main hand."));
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "player");
        NameAndId target = LifeUtil.resolveNameAndId(server, name);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown player: " + name));
            return 0;
        }
        LivesData data = LivesData.get(server);
        if (data.getLives(target.id()) > 0) {
            ctx.getSource().sendFailure(Component.literal(target.name() + " is not out of lives."));
            return 0;
        }

        int reviveLives = LifeConfig.get().revivalCrystalLives;
        LifeUtil.unban(server, target);
        data.setLives(target.id(), reviveLives);
        held.shrink(1);
        int grantedLives = data.getLives(target.id());   // post-clamp actual
        LifeLog.info("[lifesmp] {} revived {} via Revival Crystal (lives now {})",
            self.getGameProfile().name(), target.name(), grantedLives);

        ctx.getSource().sendSuccess(() ->
            Component.literal("Revived ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                .append(Component.literal(target.name()).setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(" with " + grantedLives + " lives.").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))),
            false
        );
        server.getPlayerList().broadcastSystemMessage(
            Component.literal(target.name() + " has been revived by " + self.getGameProfile().name() + "!")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFE17B))),
            false
        );
        return 1;
    }

    private static int withdraw(CommandContext<CommandSourceStack> ctx, int quantity) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        MinecraftServer server = self.level().getServer();
        if (server == null) return 0;

        LivesData data = LivesData.get(server);
        int current = data.getLives(self.getUUID());
        int floor = LifeConfig.get().minLivesAfterWithdraw;
        if (current - quantity < floor) {
            ctx.getSource().sendFailure(Component.literal(
                "You must keep at least " + floor + " life" + (floor == 1 ? "" : "s")
                    + ". You have " + current + "."
            ));
            return 0;
        }

        data.addLives(self.getUUID(), -quantity);
        LifeLog.info("[lifesmp] {} withdrew {} life(s) (now {})",
            self.getGameProfile().name(), quantity, current - quantity);
        ItemStack shards = LifeItems.createLifeShard(quantity);
        if (!self.getInventory().add(shards)) {
            ServerLevel sl = self.level();
            ItemEntity drop = new ItemEntity(sl, self.getX(), self.getY(), self.getZ(), shards);
            drop.setDefaultPickUpDelay();
            sl.addFreshEntity(drop);
        }
        LifeUtil.refreshTabName(server, self);

        final int finalQuantity = quantity;
        final int finalRemaining = current - quantity;
        ctx.getSource().sendSuccess(() ->
            Component.literal("Withdrew ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                .append(Component.literal(finalQuantity + " life" + (finalQuantity == 1 ? "" : "s"))
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
                .append(Component.literal(". You have " + finalRemaining + " left.")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))),
            false
        );
        return 1;
    }

    private static int deposit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        MinecraftServer server = self.level().getServer();
        if (server == null) return 0;

        LivesData data = LivesData.get(server);
        int current = data.getLives(self.getUUID());
        int maxLives = LifeConfig.get().maxLives;
        int capacity = maxLives - current;
        if (capacity <= 0) {
            ctx.getSource().sendFailure(Component.literal("You are already at max lives (" + maxLives + ")."));
            return 0;
        }

        Inventory inv = self.getInventory();
        int deposited = 0;
        for (int i = 0; i < inv.getContainerSize() && deposited < capacity; i++) {
            ItemStack s = inv.getItem(i);
            if (!LifeItems.isLifeShard(s)) continue;
            int take = Math.min(s.getCount(), capacity - deposited);
            if (take <= 0) continue;
            // Use the canonical Inventory.removeItem — it both clears the slot
            // when emptied and triggers the inventory's change tracking, which
            // bare shrink() doesn't always do.
            inv.removeItem(i, take);
            deposited += take;
        }

        if (deposited <= 0) {
            ctx.getSource().sendFailure(Component.literal("You have no Life Shards to deposit."));
            return 0;
        }
        data.addLives(self.getUUID(), deposited);
        // Mark dirty and resend the full menu state. broadcastFullState forces
        // every slot to be re-sent to the client, bypassing the diff cache
        // that was missing the shard removal in earlier builds.
        inv.setChanged();
        self.containerMenu.broadcastFullState();
        LifeUtil.refreshTabName(server, self);
        LifeLog.info("[lifesmp] {} deposited {} life(s) (now {})",
            self.getGameProfile().name(), deposited, data.getLives(self.getUUID()));

        int totalNow = data.getLives(self.getUUID());
        final int dep = deposited;
        ctx.getSource().sendSuccess(() ->
            Component.literal("Deposited ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                .append(Component.literal(dep + " life" + (dep == 1 ? "" : "s"))
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
                .append(Component.literal(". You now have " + totalNow + ".")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))),
            false
        );
        return 1;
    }
}
