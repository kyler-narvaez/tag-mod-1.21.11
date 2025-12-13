package com.kylernarvaez.tagmod;

import net.fabricmc.api.ModInitializer;

import com.kylernarvaez.tagmod.TagCommand.StartGame;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TagMod implements ModInitializer {
	public static final String MOD_ID = "tag-mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		StartGame.register();
		registerDeathHandler();

		// Register the tick listener ONCE
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			StartGame.onServerTick(server);
		});

	}

	private static void registerDeathHandler() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayerEntity player)) return;
			if (!StartGame.isGameRunning()) return;

			StartGame.onPlayerDeath(player);
		});
	}

	private static void registerRespawnHandler() {
		ServerPlayerEvents.AFTER_RESPAWN.register(
				(oldPlayer, newPlayer, alive) -> {
					if (!StartGame.isGameRunning()) return;

					StartGame.onPlayerRespawn(newPlayer);
				}
		);
	}
}