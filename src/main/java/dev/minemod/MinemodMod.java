package dev.minemod;

import dev.minemod.component.ModComponents;
import dev.minemod.entity.ModEntities;
import dev.minemod.item.ModItems;
import dev.minemod.parry.ParryHandler;
import dev.minemod.recipe.ModRecipes;
import dev.minemod.spawn.ModSpawns;

import net.fabricmc.api.ModInitializer;

public final class MinemodMod implements ModInitializer {
	public static final String MOD_ID = "minemod";

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModItems.init();
		ModRecipes.init();
		ModEntities.init();
		ModSpawns.init();
		ParryHandler.init();
	}
}
