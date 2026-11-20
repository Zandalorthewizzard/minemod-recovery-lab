package dev.minemod.test;

import dev.minemod.entity.ModEntities;
import dev.minemod.entity.ShellbackEntity;
import dev.minemod.entity.ShellbackPhase;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
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
	private static final int COVER_X = 4;
	private static final int COVER_Z = 4;
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

	/**
	 * Chunk C: the frontal hit is deflected before {@code LivingEntity#hurtServer} runs, so the mob never
	 * learns who hit it and goes on strolling. The attacker stands behind a one-block pillar here, which
	 * also hides them from {@code NearestAttackableTargetGoal} (it is built with {@code mustSee}): getting a
	 * target at all can therefore only come from the memory of the deflected hit. The hit itself is made
	 * through the real attack path, {@code Player#attack}. The cover is dropped as soon as the mob has the
	 * target, so the mob then has a clear floor to close in on.
	 */
	@GameTest(maxTicks = 100)
	public void retaliation_after_deflect(final GameTestHelper helper) {
		placeFloor(helper);
		placeCover(helper, true);
		ShellbackEntity shellback = helper.spawn(ModEntities.SHELLBACK, new Vec3(4.0, 1.0, 3.0));
		Player player = spawnPlayer(helper, new Vec3(4.0, 1.0, 6.0), 180.0F);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));

		// Two idle ticks, so the hit is not recorded on tickCount 0: HurtByTargetGoal starts with a cached
		// timestamp of 0 and would ignore a memory stamped on the very first tick of the mob's life.
		helper.runAfterDelay(2, () -> {
			shellback.setYRot(0.0F);
			shellback.setYBodyRot(0.0F);
			shellback.setYHeadRot(0.0F);
			helper.assertTrue(!shellback.hasLineOfSight(player),
					"the pillar must hide the attacker from the mob, otherwise either path could find it");
			float before = shellback.getHealth();
			player.attack(shellback);
			helper.assertFalse(shellback.getHealth() < before,
					"frontal melee must still be deflected, health " + shellback.getHealth() + " was " + before);
			helper.assertTrue(shellback.getHealth() == before, "deflected hit must not change health");
			helper.assertTrue(shellback.getLastHurtByMob() == player,
					"the mob must remember who hit it, lastHurtByMob=" + shellback.getLastHurtByMob());
			new RetaliationWatch(helper, shellback, player).tick();
		});
	}

	@GameTest(maxTicks = 100)
	public void creative_player_is_targeted(final GameTestHelper helper) {
		placeFloor(helper);
		ShellbackEntity shellback = helper.spawn(ModEntities.SHELLBACK, new Vec3(4.0, 1.0, 3.0));
		shellback.setYRot(0.0F);
		shellback.setYBodyRot(0.0F);
		shellback.setYHeadRot(0.0F);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.CREATIVE);
		Vec3 absolute = helper.absoluteVec(new Vec3(4.0, 1.0, 5.0));
		player.setPos(absolute.x, absolute.y, absolute.z);
		float before = player.getHealth();

		helper.startSequence()
				// The target goal rescans on a randomised 10-tick interval, so a fixed check at tick 20
				// occasionally landed before the first scan and failed the suite. 40 ticks gives it 3-4 scans.
				.thenIdle(40)
				.thenExecute(() -> {
					helper.assertTrue(shellback.getTarget() == player,
							"creative player must be targeted, target=" + shellback.getTarget());
					helper.assertTrue(player.getHealth() == before,
							"creative player must not lose health before the attack lands");
				})
				.thenSucceed();
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

	/**
	 * Chunk D: the reflection gate has to be about melee, not about the shell. A real creeper blast that goes
	 * off strictly in front of the mob must take health off it, and fire, fall and magic damage must land too;
	 * a melee hit from a living non-player attacker (a wolf) must still bounce, and arrows keep passing on their
	 * own case ({@link #projectile_not_deflected}).
	 *
	 * The blast is a real one: {@code Level#explode(entity, ...)} builds the damage source itself, the way a
	 * vanilla creeper does, rather than a hand-made {@code damageSources().explosion(a, b)}, which is a different
	 * case and would prove nothing about the vanilla one.
	 *
	 * Each of the four non-melee damage types gets its own mob: vanilla gives a hurt entity ten ticks of
	 * invulnerability, and a second hit of equal damage inside that window is dropped by
	 * {@code LivingEntity}, which would hide the very gate this test is about.
	 */
	@GameTest(maxTicks = 60)
	public void creeper_front_damages(final GameTestHelper helper) {
		placeFloor(helper);

		assertDamageLands(helper, new Vec3(1.0, 1.0, 1.0), helper.getLevel().damageSources().onFire(), "fire");
		assertDamageLands(helper, new Vec3(7.0, 1.0, 7.0), helper.getLevel().damageSources().fall(), "fall");
		assertDamageLands(helper, new Vec3(1.0, 1.0, 7.0), helper.getLevel().damageSources().magic(), "magic");

		// NoAI on the wolf: it must stay inside the frontal cone.
		ShellbackEntity shellback = spawnShellback(helper, new Vec3(4.0, 1.0, 3.0), 0.0F);
		LivingEntity wolf = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new Vec3(3.0, 1.0, 4.2));
		float meleeBefore = shellback.getHealth();
		boolean hurt = shellback.hurtServer(helper.getLevel(),
				helper.getLevel().damageSources().mobAttack(wolf), HIT_DAMAGE);
		LOGGER.info("creeper_front_damages: wolf front melee deflect={} health {}", !hurt, shellback.getHealth());
		helper.assertFalse(hurt, "a frontal melee hit from a living attacker must still be deflected");
		helper.assertTrue(shellback.getHealth() == meleeBefore, "deflected hit must not change health");

		// The blast goes last: at this range a real one takes a 24 health mob all the way down, so nothing
		// after it could be observed. NoAI so it does not walk off and blow up on its own schedule.
		Entity creeper = helper.spawnWithNoFreeWill(EntityTypes.CREEPER, new Vec3(4.0, 1.0, 4.5));
		float before = shellback.getHealth();
		Vec3 at = creeper.position();
		helper.getLevel().explode(creeper, at.x, at.y, at.z, 3.0F, Level.ExplosionInteraction.MOB);
		LOGGER.info("creeper_front_damages: blast in front, health {} -> {}", before, shellback.getHealth());
		helper.assertTrue(shellback.getHealth() < before,
				"a creeper blast strictly in front must take health, was " + before + " now " + shellback.getHealth());

		helper.succeed();
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

	/** Two blocks of stone between the mob and the attacker: enough to hide a 0.7 tall mob's eyes. */
	private static void placeCover(final GameTestHelper helper, final boolean present) {
		BlockState state = present ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
		helper.setBlock(COVER_X, 1, COVER_Z, state);
		helper.setBlock(COVER_X, 2, COVER_Z, state);
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

	/**
	 * A damage type that is not a melee hit must reach a fresh mob. Its own mob, because a mob that was just hurt
	 * spends ten ticks immune and would drop a second hit of equal damage.
	 */
	private static void assertDamageLands(final GameTestHelper helper, final Vec3 pos, final DamageSource source,
			final String what) {
		ShellbackEntity shellback = spawnShellback(helper, pos, 0.0F);
		float before = shellback.getHealth();
		boolean hurt = shellback.hurtServer(helper.getLevel(), source, HIT_DAMAGE);
		LOGGER.info("creeper_front_damages: {} -> hurt={} health {} -> {}",
				what, hurt, before, shellback.getHealth());
		helper.assertTrue(hurt, what + " must land on the shell, health " + before);
		helper.assertTrue(shellback.getHealth() < before,
				what + " must reduce health, was " + before + " now " + shellback.getHealth());
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

	/**
	 * Polls once per tick: the deflected hit must show up as a target within {@link #TARGET_DEADLINE}
	 * ticks, and the mob must then close in on that target instead of resuming its stroll.
	 */
	private static final class RetaliationWatch {
		private static final int TARGET_DEADLINE = 40;
		private static final int CHASE_DEADLINE = 40;
		private static final double CHASE_DISTANCE = 1.0;
		private final GameTestHelper helper;
		private final ShellbackEntity shellback;
		private final Player player;
		private final long startTick;
		private final double startDistance;
		private long targetTick = -1L;

		RetaliationWatch(final GameTestHelper helper, final ShellbackEntity shellback, final Player player) {
			this.helper = helper;
			this.shellback = shellback;
			this.player = player;
			this.startTick = helper.getTick();
			this.startDistance = horizontalDistance(shellback, player);
		}

		void tick() {
			long elapsed = this.helper.getTick() - this.startTick;

			if (this.targetTick < 0) {
				if (this.shellback.getTarget() == this.player) {
					this.targetTick = this.helper.getTick();
					// The mob now knows who hit it; open the floor so the chase is not spent on the cover.
					placeCover(this.helper, false);
					LOGGER.info("retaliation_after_deflect: target acquired after {} ticks, phase={} distance={}",
							elapsed, this.shellback.getPhase(), this.startDistance);
				} else {
					this.helper.assertTrue(elapsed <= TARGET_DEADLINE,
							"a deflected frontal hit must make the mob retaliate within " + TARGET_DEADLINE
									+ " ticks, target=" + this.shellback.getTarget()
									+ " lastHurtByMob=" + this.shellback.getLastHurtByMob());
					this.helper.runAfterDelay(1, this::tick);
					return;
				}
			}

			double distance = horizontalDistance(this.shellback, this.player);

			if (distance <= this.startDistance - CHASE_DISTANCE) {
				LOGGER.info("retaliation_after_deflect: mob closed from {} to {} blocks in {} ticks",
						this.startDistance, distance, this.helper.getTick() - this.targetTick);
				this.helper.succeed();
				return;
			}

			this.helper.assertTrue(this.helper.getTick() - this.targetTick <= CHASE_DEADLINE,
					"the mob must go on the attack, distance " + distance + " of the original " + this.startDistance);
			this.helper.runAfterDelay(1, this::tick);
		}
	}

	private static double horizontalDistance(final Entity one, final Entity other) {
		double dx = one.getX() - other.getX();
		double dz = one.getZ() - other.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
