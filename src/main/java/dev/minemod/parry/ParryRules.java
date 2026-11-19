package dev.minemod.parry;

import dev.minemod.component.ModComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;

/** Pure parry decision and section 3.3 balance constants. Never reads live entity state. */
public final class ParryRules {
	/** Upper bound of the parry window after the effective block start, in ticks. */
	public static final int PARRY_WINDOW_TICKS = 6;
	public static final int STAGGER_DURATION_TICKS = 30;
	/** Slowness IV. */
	public static final int STAGGER_SLOWNESS_AMPLIFIER = 3;
	/** Weakness I. */
	public static final int STAGGER_WEAKNESS_AMPLIFIER = 0;
	public static final double STAGGER_KNOCKBACK = 0.8;

	private ParryRules() {
	}

	/** Defender use-item state from ALLOW_DAMAGE; the stack is a copy, so a broken shield still counts. */
	public record Snapshot(boolean usingItem, ItemStack useItem, int ticksUsing) {
	}

	/**
	 * blocked && plated shield && ticksUsing <= blockDelayTicks + 6 && direct, non-projectile
	 * MOB_ATTACK from an Enemy Mob. Q1 fixes the lower bound (vanilla block delay) and D = 5.
	 */
	public static boolean isParry(final Snapshot snapshot, final DamageSource source, final boolean blocked) {
		if (!blocked || snapshot == null || !snapshot.usingItem()) {
			return false;
		}
		ItemStack shield = snapshot.useItem();
		if (shield.isEmpty() || !shield.has(ModComponents.PLATED)) {
			return false;
		}
		BlocksAttacks blocksAttacks = shield.get(DataComponents.BLOCKS_ATTACKS);
		if (blocksAttacks == null) {
			return false;
		}
		if (snapshot.ticksUsing() > blocksAttacks.blockDelayTicks() + PARRY_WINDOW_TICKS) {
			return false;
		}
		if (!source.is(DamageTypes.MOB_ATTACK)) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (attacker == null || source.getDirectEntity() != attacker) {
			return false;
		}
		if (!(attacker instanceof Mob mob) || !(mob instanceof Enemy)) {
			return false;
		}
		return !source.is(DamageTypeTags.IS_PROJECTILE);
	}
}
