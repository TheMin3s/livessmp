package com.schecks.lifesmp;

import com.schecks.lifesmp.commands.LifeCommand;
import com.schecks.lifesmp.commands.LivesCommand;
import com.schecks.lifesmp.events.DeathHandler;
import com.schecks.lifesmp.events.JoinHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class LifeSMP implements ModInitializer {
    public static final String MOD_ID = "lifesmp";

    @Override
    public void onInitialize() {
        DeathHandler.register();
        JoinHandler.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LifeCommand.register(dispatcher);
            LivesCommand.register(dispatcher);
        });
        // Dedicated event log goes to config/lifesmp/lifesmp.log; opens at server
        // start, closes at server stop. Auto-rotates every 6h. Never writes
        // to the main server console — no boot message either.
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
            LifeLog.init(server.getServerDirectory()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
            LifeLog.close());
    }
}
