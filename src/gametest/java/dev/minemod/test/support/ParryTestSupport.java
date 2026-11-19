package dev.minemod.test.support;

import dev.minemod.component.ModComponents;
import dev.minemod.parry.ParryRules;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Shared recipe for the timed-parry GameTests (Q12): a survival mock Player that is not added to
 * the level, with a shield in the off hand; {@code ticksUsing} advances via manual {@code tick()}
 * calls. {@link #D} is the first blocking tick (Q1). Never use {@code makeMockServerPlayerInLevel}.
 */
public final class ParryTestSupport {
	/** First tick at which a vanilla shield blocks: {@code blockDelayTicks() == 5} (Q1). */
	public static final int D = 5;
	/** The vanilla shield fully blocks this test-arrow damage. */
	public static final float ARROW_DAMAGE = 4.0F;
	private static final double ATTACKER_DISTANCE = 1.0;
	private static final Vec3 DEFENDER_POS = new Vec3(1.5, 1.5, 2.5);

	private ParryTestSupport() {
	}

	/** Vanilla shield upgraded with the Scope 3 {@code minemod:plated} marker. */
	public static ItemStack platedShield() {
		ItemStack shield = new ItemStack(Items.SHIELD);
		shield.set(ModComponents.PLATED, Unit.INSTANCE);
		return shield;
	}

	/** Plain vanilla shield without the plated marker. */
	public static ItemStack vanillaShield() {
		return new ItemStack(Items.SHIELD);
	}

	/** Plated shield with one durability point left, so the blocking hit breaks it. */
	public static ItemStack almostBrokenPlatedShield() {
		ItemStack shield = platedShield();
		shield.setDamageValue(335);
		return shield;
	}

	/** Survival mock player at a fixed arena spot with {@code shield} in the off hand. */
	public static Player spawnDefender(final GameTestHelper helper, final ItemStack shield) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setNoGravity(true);
		player.setPos(helper.absoluteVec(DEFENDER_POS));
		player.setItemInHand(InteractionHand.OFF_HAND, shield);
		return player;
	}

	/** Starts the off-hand use action and advances exactly {@code ticks} player ticks. */
	public static Player beginBlocking(final Player defender, final int ticks) {
		defender.startUsingItem(InteractionHand.OFF_HAND);
		for (int i = 0; i < ticks; i++) {
			defender.tick();
		}
		return defender;
	}

	/** Spawns the attacker one block along the defender's look direction, then faces the defender at it. */
	public static <T extends Mob> T spawnAttackerInFront(final GameTestHelper helper, final EntityType<T> type,
			final Player defender) {
		Vec3 look = Vec3.directionFromRotation(0.0F, defender.getYRot());
		Vec3 relative = helper.relativeVec(defender.position());
		T attacker = helper.spawnWithNoFreeWill(type,
				relative.add(look.x * ATTACKER_DISTANCE, 0.0, look.z * ATTACKER_DISTANCE));
		attacker.setNoGravity(true);
		defender.lookAt(EntityAnchorArgument.Anchor.EYES, attacker.getEyePosition());
		return attacker;
	}

	/** Real melee path: the mob builds its own MOB_ATTACK source inside {@code doHurtTarget}. */
	public static boolean meleeHit(final GameTestHelper helper, final Mob attacker, final Player defender) {
		return attacker.doHurtTarget(helper.getLevel(), defender);
	}

	/** Real projectile source owned by {@code shooter}; the hit goes through {@code hurtServer}. */
	public static boolean arrowHit(final GameTestHelper helper, final Player defender, final LivingEntity shooter) {
		Vec3 look = Vec3.directionFromRotation(0.0F, defender.getYRot());
		Vec3 relative = helper.relativeVec(defender.position());
		Arrow arrow = helper.spawn(EntityTypes.ARROW,
				relative.add(look.x * ATTACKER_DISTANCE, 0.0, look.z * ATTACKER_DISTANCE));
		arrow.setNoGravity(true);
		arrow.setOwner(shooter);
		return defender.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, shooter), ARROW_DAMAGE);
	}

	/** Asserts the stagger contract: Slowness IV and Weakness I, both for 30 ticks. */
	public static void assertStaggered(final GameTestHelper helper, final LivingEntity attacker) {
		MobEffectInstance slowness = attacker.getEffect(MobEffects.SLOWNESS);
		helper.assertTrue(slowness != null, "a parried attacker must get Slowness");
		helper.assertValueEqual(slowness.getAmplifier(), ParryRules.STAGGER_SLOWNESS_AMPLIFIER, "Slowness amplifier");
		helper.assertValueEqual(slowness.getDuration(), ParryRules.STAGGER_DURATION_TICKS, "Slowness duration");
		MobEffectInstance weakness = attacker.getEffect(MobEffects.WEAKNESS);
		helper.assertTrue(weakness != null, "a parried attacker must get Weakness");
		helper.assertValueEqual(weakness.getAmplifier(), ParryRules.STAGGER_WEAKNESS_AMPLIFIER, "Weakness amplifier");
		helper.assertValueEqual(weakness.getDuration(), ParryRules.STAGGER_DURATION_TICKS, "Weakness duration");
	}

	/** Asserts that no stagger was applied. */
	public static void assertNotStaggered(final GameTestHelper helper, final LivingEntity attacker) {
		helper.assertTrue(attacker.getEffect(MobEffects.SLOWNESS) == null, "attacker must not get Slowness");
		helper.assertTrue(attacker.getEffect(MobEffects.WEAKNESS) == null, "attacker must not get Weakness");
	}
}
