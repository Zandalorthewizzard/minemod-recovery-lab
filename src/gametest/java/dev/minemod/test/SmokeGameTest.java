package dev.minemod.test;

import dev.minemod.MinemodMod;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmokeGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(SmokeGameTest.class);

	@GameTest
	public void modIsLoaded(GameTestHelper helper) {
		helper.assertTrue(FabricLoader.getInstance().isModLoaded(MinemodMod.MOD_ID),
				"Mod " + MinemodMod.MOD_ID + " is not loaded");
		LOGGER.info("SmokeGameTest passed: mod {} is loaded", MinemodMod.MOD_ID);
		helper.succeed();
	}
}
