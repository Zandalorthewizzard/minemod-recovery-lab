package dev.minemod.test;

import dev.minemod.test.support.ParryTestSupport;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

/**
 * Scope 4 acceptance tests for the timed parry. Every hit goes through the real damage path
 * ({@code Mob#doHurtTarget} or {@code hurtServer} with a vanilla {@code DamageSource}); no test
 * calls {@code ParryRules} directly.
 *
 * <p>{@code D = 5} is the first tick at which a vanilla shield blocks (Q1), so the parry window is
 * {@code [D, D + 6]} = ticks 5..11 of the use action.
 */
public class ParryGameTest {
	private static final int MAX_TICKS = 40;

	@GameTest(maxTicks = MAX_TICKS)
	public void in_window_staggers_zombie(final GameTestHelper helper) {
		Player player = ParryTestSupport.spawnDefender(helper, ParryTestSupport.platedShield());
		ParryTestSupport.beginBlocking(player, ParryTestSupport.D);
		Zombie zombie = ParryTestSupport.spawnAttackerInFront(helper, EntityTypes.ZOMBIE, player);
		float before = player.getHealth();
		boolean hurt = ParryTestSupport.meleeHit(helper, zombie, player);
		helper.assertFalse(hurt, "the raised plated shield must block the hit");
		helper.assertValueEqual(player.getHealth(), before, "a parried hit must deal no damage");
		ParryTestSupport.assertStaggered(helper, zombie);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void upper_bound_edge_staggers(final GameTestHelper helper) {
		Player player = ParryTestSupport.spawnDefender(helper, ParryTestSupport.platedShield());
		ParryTestSupport.beginBlocking(player, ParryTestSupport.D + 6);
		helper.assertValueEqual(player.getTicksUsingItem(), ParryTestSupport.D + 6, "ticksUsing at D+6");
		Zombie zombie = ParryTestSupport.spawnAttackerInFront(helper, EntityTypes.ZOMBIE, player);
		float before = player.getHealth();
		boolean hurt = ParryTestSupport.meleeHit(helper, zombie, player);
		helper.assertFalse(hurt, "the raised plated shield must block the hit");
		helper.assertValueEqual(player.getHealth(), before, "a parried hit at D+6 must deal no damage");
		ParryTestSupport.assertStaggered(helper, zombie);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void long_block_no_stagger(final GameTestHelper helper) {
		Player player = ParryTestSupport.spawnDefender(helper, ParryTestSupport.platedShield());
		ParryTestSupport.beginBlocking(player, ParryTestSupport.D + 7);
		helper.assertTrue(player.isBlocking(), "the shield must still block at D+7");
		Zombie zombie = ParryTestSupport.spawnAttackerInFront(helper, EntityTypes.ZOMBIE, player);
		float before = player.getHealth();
		boolean hurt = ParryTestSupport.meleeHit(helper, zombie, player);
		helper.assertFalse(hurt, "the raised plated shield must block the hit");
		helper.assertValueEqual(player.getHealth(), before, "the long block must still absorb the hit");
		ParryTestSupport.assertNotStaggered(helper, zombie);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void before_delay_not_blocked(final GameTestHelper helper) {
		Player player = ParryTestSupport.spawnDefender(helper, ParryTestSupport.platedShield());
		ParryTestSupport.beginBlocking(player, ParryTestSupport.D - 1);
		helper.assertFalse(player.isBlocking(), "the shield must not block before blockDelayTicks");
		Zombie zombie = ParryTestSupport.spawnAttackerInFront(helper, EntityTypes.ZOMBIE, player);
		float before = player.getHealth();
		boolean hurt = ParryTestSupport.meleeHit(helper, zombie, player);
		helper.assertTrue(hurt, "a hit at D-1 must land");
		helper.assertTrue(player.getHealth() < before, "a hit at D-1 must reduce player health");
		ParryTestSupport.assertNotStaggered(helper, zombie);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void regular_shield_no_stagger(final GameTestHelper helper) {
		Player player = ParryTestSupport.spawnDefender(helper, ParryTestSupport.vanillaShield());
		ParryTestSupport.beginBlocking(player, ParryTestSupport.D);
		Zombie zombie = ParryTestSupport.spawnAttackerInFront(helper, EntityTypes.ZOMBIE, player);
		float before = player.getHealth();
		boolean hurt = ParryTestSupport.meleeHit(helper, zombie, player);
		helper.assertFalse(hurt, "the raised shield must block the hit");
		helper.assertValueEqual(player.getHealth(), before, "a vanilla shield still absorbs the hit");
		ParryTestSupport.assertNotStaggered(helper, zombie);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void projectile_no_stagger(final GameTestHelper helper) {
		Player player = ParryTestSupport.spawnDefender(helper, ParryTestSupport.platedShield());
		ParryTestSupport.beginBlocking(player, ParryTestSupport.D);
		Skeleton skeleton = ParryTestSupport.spawnAttackerInFront(helper, EntityTypes.SKELETON, player);
		float before = player.getHealth();
		int durabilityBefore = player.getUseItem().getDamageValue();

		boolean hurt = ParryTestSupport.arrowHit(helper, player, skeleton);
		helper.assertFalse(hurt, "the raised plated shield must block the hit");
		helper.assertValueEqual(player.getHealth(), before, "a blocked arrow must deal no damage");
		helper.assertTrue(player.getUseItem().getDamageValue() > durabilityBefore,
				"the shield must absorb the arrow and spend durability");
		ParryTestSupport.assertNotStaggered(helper, skeleton);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void non_hostile_no_stagger(final GameTestHelper helper) {
		Player player = ParryTestSupport.spawnDefender(helper, ParryTestSupport.platedShield());
		ParryTestSupport.beginBlocking(player, ParryTestSupport.D);
		Wolf wolf = ParryTestSupport.spawnAttackerInFront(helper, EntityTypes.WOLF, player);
		float before = player.getHealth();
		boolean hurt = ParryTestSupport.meleeHit(helper, wolf, player);
		helper.assertFalse(hurt, "the raised plated shield must block the hit");
		helper.assertValueEqual(player.getHealth(), before, "a blocked wolf hit must deal no damage");
		ParryTestSupport.assertNotStaggered(helper, wolf);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void shield_breaks_on_parry(final GameTestHelper helper) {
		Player player = ParryTestSupport.spawnDefender(helper, ParryTestSupport.almostBrokenPlatedShield());
		ParryTestSupport.beginBlocking(player, ParryTestSupport.D);
		Zombie zombie = ParryTestSupport.spawnAttackerInFront(helper, EntityTypes.ZOMBIE, player);
		float before = player.getHealth();
		boolean hurt = ParryTestSupport.meleeHit(helper, zombie, player);
		helper.assertFalse(hurt, "the raised plated shield must block the hit");
		helper.assertValueEqual(player.getHealth(), before, "the breaking shield must still absorb the hit");
		// Q2: the snapshot copy proves the plated shield blocked even though the live stack is gone.
		helper.assertTrue(player.getItemInHand(InteractionHand.OFF_HAND).isEmpty(),
				"the one-durability shield must break on the parry");
		ParryTestSupport.assertStaggered(helper, zombie);
		helper.succeed();
	}
}
