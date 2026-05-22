package com.schecks.lifesmp;

import com.schecks.lifesmp.commands.LifeCommand;
import com.schecks.lifesmp.commands.LivesCommand;
import com.schecks.lifesmp.events.DeathHandler;
import com.schecks.lifesmp.events.InteractHandler;
import com.schecks.lifesmp.events.JoinHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class LifeSMP implements ModInitializer {
    public static final String MOD_ID = "lifesmp";

    @Override
    public void onInitialize() {
        LivesNet.registerPayloads();
        FileShare.registerPayload();
        NanoNet.register();
        DirNet.register();
        DeathHandler.register();
        JoinHandler.register();
        InteractHandler.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LifeCommand.register(dispatcher);
            LivesCommand.register(dispatcher);
        });
        // Dedicated event log goes to config/lifesmp/lifesmp.log; opens at server
        // start, closes at server stop. Auto-rotates every 6h. Never writes
        // to the main server console — no boot message either. The tunable
        // config.json is loaded alongside it.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LifeLog.init(server.getServerDirectory());
            LifeConfig.init(server.getServerDirectory());
            FileShare.init(server);
        });
        // Boot-time update check — runs after config is loaded. With auto-update
        // enabled (the default) it downloads, installs and restarts into a newer
        // version on its own; otherwise it just logs a single "update available"
        // warning to the console.
        ServerLifecycleEvents.SERVER_STARTED.register(UpdateChecker::checkOnBoot);
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
            LifeLog.close());
    }
}
