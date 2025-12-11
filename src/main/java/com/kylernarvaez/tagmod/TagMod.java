package com.kylernarvaez.tagmod;

import net.fabricmc.api.ModInitializer;
//testing commits
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TagMod implements ModInitializer {
	public static final String MOD_ID = "tag-mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {

		LOGGER.info("Hello Fabric world!");
	}
}