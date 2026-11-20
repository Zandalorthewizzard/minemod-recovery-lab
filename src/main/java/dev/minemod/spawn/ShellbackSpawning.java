package dev.minemod.spawn;

import dev.minemod.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;

/** Underground spawn placement of the shellback: dark, covered overworld spots below {@link #MAX_SPAWN_Y}. */
public final class ShellbackSpawning {
	public static final int MAX_SPAWN_Y = 50;

	private ShellbackSpawning() {
	}

	/** Registers ON_GROUND placement; called from {@link ModSpawns#init()}. */
	public static void init() {
		SpawnPlacements.register(ModEntities.SHELLBACK, SpawnPlacementTypes.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ShellbackSpawning::isUndergroundSpot);
	}

	/** Spawn predicate: underground geometry first, then the unmodified vanilla monster light rules. */
	public static <T extends Mob> boolean isUndergroundSpot(final EntityType<T> type, final ServerLevelAccessor level,
			final EntitySpawnReason reason, final BlockPos pos, final RandomSource random) {
		return isUndergroundSpot(pos.getY(), level.canSeeSky(pos))
				&& Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
	}

	/** Pure geometric half, split out so a GameTest can pin both inputs exactly. */
	public static boolean isUndergroundSpot(final int y, final boolean canSeeSky) {
		return y < MAX_SPAWN_Y && !canSeeSky;
	}
}
