package dev.minemod.parry;

import java.util.Map;
import java.util.WeakHashMap;

import dev.minemod.api.Staggerable;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Timed parry: snapshot in ALLOW_DAMAGE (before the blocking stack can break), decide in
 * AFTER_DAMAGE from vanilla's blocked flag (Q2), then stagger the attacking mob.
 */
public final class ParryHandler {
	private static final int PARTICLE_COUNT = 12;

	/**
	 * One snapshot per defender, weakly referenced and consumed by AFTER_DAMAGE. A hit that
	 * returns early (cooldown or death) leaves only an inert entry: the next hit replaces it in
	 * ALLOW_DAMAGE before its own AFTER_DAMAGE, so a stale snapshot never decides a parry.
	 */
	private static final Map<LivingEntity, ParryRules.Snapshot> SNAPSHOTS = new WeakHashMap<>();

	private ParryHandler() {
	}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// Q2/Q3: before applyItemBlocking damages or empties the use item.
			SNAPSHOTS.put(entity, new ParryRules.Snapshot(
					entity.isUsingItem(), entity.getUseItem().copy(), entity.getTicksUsingItem()));
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			ParryRules.Snapshot snapshot = SNAPSHOTS.remove(entity);
			if (ParryRules.isParry(snapshot, source, blocked)) {
				applyStagger((Mob) source.getEntity(), entity);
			}
		});
	}

	/** Section 3.3: Slowness IV + Weakness I for 30 ticks, 0.8 knockback, nav stop, Staggerable, Q14 feedback. */
	public static void applyStagger(final Mob attacker, final LivingEntity defender) {
		attacker.addEffect(new MobEffectInstance(
				MobEffects.SLOWNESS, ParryRules.STAGGER_DURATION_TICKS, ParryRules.STAGGER_SLOWNESS_AMPLIFIER));
		attacker.addEffect(new MobEffectInstance(
				MobEffects.WEAKNESS, ParryRules.STAGGER_DURATION_TICKS, ParryRules.STAGGER_WEAKNESS_AMPLIFIER));

		Vec3 away = attacker.position().subtract(defender.position());
		Vec3 push = new Vec3(away.x, 0.0, away.z);
		if (push.lengthSqr() < 1.0E-4) {
			push = new Vec3(0.0, 0.0, 1.0);
		}
		push = push.normalize().scale(ParryRules.STAGGER_KNOCKBACK);
		attacker.setDeltaMovement(attacker.getDeltaMovement().add(push.x, 0.2, push.z));
		attacker.getNavigation().stop();

		if (attacker instanceof Staggerable staggerable) {
			staggerable.onParried(defender);
		}
		defender.level().playSound(null, defender.getX(), defender.getY(), defender.getZ(),
				SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F);
		if (attacker.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
					attacker.getX(), attacker.getY(0.5), attacker.getZ(),
					PARTICLE_COUNT, 0.35, 0.5, 0.35, 0.02);
		}
	}
}
