package dev.minemod.test;

import dev.minemod.entity.ModEntities;
import dev.minemod.entity.ShellbackEntity;
import dev.minemod.entity.ShellbackPhase;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server GameTests for the Shellback: frontal deflection, phase sequence and parry reaction.
 * Every test builds its own floor because the default game test structure is empty.
 */
public class ShellbackCombatGameTest {
	private static final int ARENA_RADIUS = 7;
	private static final float HIT_DAMAGE = 5.0F;
	private static final Logger LOGGER = LoggerFactory.getLogger(ShellbackCombatGameTest.class);

	@GameTest(maxTicks = 40)
	public void front_melee_deflected(final GameTestHelper helper) {
		placeFloor(helper);
		ShellbackEntity shellback = spawnShellback(helper, new Vec3(4.0, 1.0, 3.0), 0.0F);
		shellback.setYRot(90.0F);
		Player player = spawnPlayer(helper, new Vec3(4.0, 1.0, 4.6), 180.0F);
		float before = shellback.getHealth();

		helper.assertTrue(shellback.getMaxHealth() == 24.0F, "max health must be 24");
		helper.assertTrue(shellback.getAttributeValue(Attributes.ARMOR) == 6.0, "armor must be 6");
		helper.assertTrue(shellback.getAttributeValue(Attributes.ATTACK_DAMAGE) == 5.0, "attack damage must be 5");
		helper.assertTrue(shellback.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.23, "speed must be 0.23");

		boolean hurt = meleeHit(helper, shellback, player);

		helper.assertFalse(hurt, "front melee must be deflected");
		helper.assertTrue(shellback.getHealth() == before, "deflected hit must not change health");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void side_melee_damages(final GameTestHelper helper) {
		placeFloor(helper);
		ShellbackEntity shellback = spawnShellback(helper, new Vec3(4.0, 1.0, 3.0), 0.0F);
		Player player = spawnPlayer(helper, new Vec3(5.6, 1.0, 3.0), 270.0F);
		float before = shellback.getHealth();

		boolean hurt = meleeHit(helper, shellback, player);

		helper.assertTrue(hurt, "side melee must land");
		helper.assertTrue(shellback.getHealth() < before, "side melee must reduce health");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void back_melee_damages(final GameTestHelper helper) {
		placeFloor(helper);
		ShellbackEntity shellback = spawnShellback(helper, new Vec3(4.0, 1.0, 3.0), 0.0F);
		Player player = spawnPlayer(helper, new Vec3(4.0, 1.0, 1.4), 0.0F);
		float before = shellback.getHealth();

		boolean hurt = meleeHit(helper, shellback, player);

		helper.assertTrue(hurt, "back melee must land");
		helper.assertTrue(shellback.getHealth() < before, "back melee must reduce health");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void projectile_not_deflected(final GameTestHelper helper) {
		placeFloor(helper);
		ShellbackEntity shellback = spawnShellback(helper, new Vec3(4.0, 1.0, 3.0), 0.0F);
		Player shooter = spawnPlayer(helper, new Vec3(4.0, 1.0, 6.0), 180.0F);
		float before = shellback.getHealth();

		Arrow arrow = helper.spawn(EntityTypes.ARROW, new Vec3(4.0, 1.4, 5.0));
		arrow.setOwner(shooter);
		arrow.setDeltaMovement(0.0, 0.0, -1.5);

		helper.runAfterDelay(15, () -> {
			helper.assertTrue(shellback.getHealth() < before, "arrow must damage the front, not be deflected");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 160)
	public void lunge_sequence_then_exposed(final GameTestHelper helper) {
		placeFloor(helper);
		ShellbackEntity shellback = helper.spawn(ModEntities.SHELLBACK, new Vec3(4.0, 1.0, 1.5));
		shellback.setYRot(0.0F);
		Player player = spawnPlayer(helper, new Vec3(4.0, 1.0, 6.5), 180.0F);
		shellback.setTarget(player);

		new LungeWatcher(helper, shellback, player).tick();
	}

	@GameTest(maxTicks = 80)
	public void on_parried_exposes(final GameTestHelper helper) {
		placeFloor(helper);
		ShellbackEntity shellback = helper.spawn(ModEntities.SHELLBACK, new Vec3(4.0, 1.0, 1.5));
		shellback.setYRot(0.0F);
		Player player = spawnPlayer(helper, new Vec3(4.0, 1.0, 6.5), 180.0F);
		shellback.setTarget(player);

		whenPhase(helper, shellback, ShellbackPhase.TELEGRAPH, () -> {
			shellback.onParried(player);
			helper.assertTrue(shellback.getPhase() == ShellbackPhase.EXPOSED,
					"parry during telegraph must expose the shell, was " + shellback.getPhase());
			helper.succeed();
		});
	}

	private static void placeFloor(final GameTestHelper helper) {
		BlockState stone = Blocks.STONE.defaultBlockState();

		for (int x = 0; x <= ARENA_RADIUS; x++)
			for (int z = 0; z <= ARENA_RADIUS; z++)
				helper.setBlock(x, 0, z, stone);
	}

	private static ShellbackEntity spawnShellback(final GameTestHelper helper, final Vec3 pos, final float yaw) {
		ShellbackEntity shellback = helper.spawnWithNoFreeWill(ModEntities.SHELLBACK, pos);
		shellback.setYRot(yaw);
		shellback.setYBodyRot(yaw);
		shellback.setYHeadRot(yaw);
		return shellback;
	}

	private static Player spawnPlayer(final GameTestHelper helper, final Vec3 pos, final float yaw) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 absolute = helper.absoluteVec(pos);
		player.setPos(absolute.x, absolute.y, absolute.z);
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		return player;
	}

	private static boolean meleeHit(final GameTestHelper helper, final ShellbackEntity shellback, final Player player) {
		return shellback.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT_DAMAGE);
	}

	private static void whenPhase(final GameTestHelper helper, final ShellbackEntity shellback, final ShellbackPhase phase,
			final Runnable action) {
		if (shellback.getPhase() == phase) {
			action.run();
		} else {
			helper.runAfterDelay(1, () -> whenPhase(helper, shellback, phase, action));
		}
	}

	/**
	 * Samples the phase once per tick and asserts the whole lunge cycle.
	 */
	private static final class LungeWatcher {
		private final GameTestHelper helper;
		private final ShellbackEntity shellback;
		private final Player player;
		private final long startTick;
		private int telegraphTicks;
		private boolean sawLunge;
		private boolean exposed;
		private int exposedSamples;
		private boolean hitWhileExposed;

		LungeWatcher(final GameTestHelper helper, final ShellbackEntity shellback, final Player player) {
			this.helper = helper;
			this.shellback = shellback;
			this.player = player;
			this.startTick = helper.getTick();
		}

		void tick() {
			ShellbackPhase phase = this.shellback.getPhase();

			switch (phase) {
				case TELEGRAPH -> this.telegraphTicks++;
				case LUNGE -> this.sawLunge = true;
				case EXPOSED -> {
					this.exposedSamples++;
					if (!this.exposed) {
						this.exposed = true;
						long elapsed = this.helper.getTick() - this.startTick;
						LOGGER.info("lunge_sequence: telegraphTicks={} elapsed={} shellback={} playerHealth={}",
								this.telegraphTicks, elapsed, this.shellback.position(), this.player.getHealth());
						this.helper.assertTrue(this.telegraphTicks >= ShellbackEntity.TELEGRAPH_TICKS,
								"telegraph must last at least 16 ticks, observed " + this.telegraphTicks);
						this.helper.assertTrue(this.sawLunge, "lunge must be observed before exposure");
						this.helper.assertTrue(elapsed <= 100, "lunge cycle must finish within 100 ticks, took " + elapsed);
						this.helper.assertTrue(this.player.getHealth() == 15.0F,
								"lunge must land exactly one 5 damage MOB_ATTACK hit, player health " + this.player.getHealth());
						float before = this.shellback.getHealth();
						boolean hurt = meleeHit(this.helper, this.shellback, this.player);
						this.helper.assertTrue(hurt, "exposed shellback must take melee damage");
						this.helper.assertTrue(this.shellback.getHealth() < before, "exposed shellback health must drop");
						this.hitWhileExposed = true;
					}
				}
				case IDLE -> {
					if (this.hitWhileExposed) {
						LOGGER.info("lunge_sequence: exposedSamples={} cooldown ended at tick {}", this.exposedSamples, this.helper.getTick());
						this.helper.assertTrue(this.exposedSamples >= ShellbackEntity.EXPOSED_TICKS,
								"exposed window must last at least 30 ticks, observed " + this.exposedSamples);
						float before = this.shellback.getHealth();
						boolean hurt = meleeHit(this.helper, this.shellback, this.player);
						LOGGER.info("lunge_sequence: back to IDLE at tick {}, deflect={}", this.helper.getTick(), !hurt);
						this.helper.assertFalse(hurt, "shell must deflect again after exposure ends");
						this.helper.assertTrue(this.shellback.getHealth() == before, "deflected hit must not change health");
						this.helper.succeed();
						return;
					}
				}
			}

			this.helper.runAfterDelay(1, this::tick);
		}
	}
}
