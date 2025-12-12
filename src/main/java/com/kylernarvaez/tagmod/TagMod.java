package com.kylernarvaez.tagmod;

import net.fabricmc.api.ModInitializer;

import com.kylernarvaez.tagmod.TagCommand.StartGame;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TagMod implements ModInitializer {
	public static final String MOD_ID = "tag-mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		StartGame.register();

		// Register the tick listener ONCE
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			StartGame.onServerTick(server);
		});
	}
}