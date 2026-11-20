package dev.minemod.spawn;

import dev.minemod.entity.ModEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.world.entity.MobCategory;

/**
 * Natural spawns of the shellback: rare overworld monsters that the placement predicate of
 * {@link ShellbackSpawning} keeps underground.
 */
public final class ModSpawns {
	private ModSpawns() {
	}

	public static void init() {
		ShellbackSpawning.init();
		ModSpawnEggs.init();
		BiomeModifications.addSpawn(
				BiomeSelectors.foundInOverworld(),
				MobCategory.MONSTER,
				ModEntities.SHELLBACK,
				25, 1, 2
		);
	}
}
