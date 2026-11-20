package dev.minemod.entity;

import dev.minemod.api.Staggerable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Cave quadruped with a plated front: melee hits from the frontal 60 degree cone are
 * deflected unless the shell is {@link ShellbackPhase#EXPOSED} after a lunge.
 */
public class ShellbackEntity extends Monster implements Staggerable {
	public static final int TELEGRAPH_TICKS = 16;
	public static final int LUNGE_MAX_TICKS = 12;
	public static final int EXPOSED_TICKS = 30;
	public static final int LUNGE_COOLDOWN_TICKS = 40;
	/** cos(60 degrees): a hit is frontal when the horizontal dot product is at least this. */
	public static final double FRONTAL_COS = 0.5;

	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(ShellbackEntity.class, EntityDataSerializers.BYTE);

	private int lungeCooldown, exposedTicks;

	public ShellbackEntity(final EntityType<? extends ShellbackEntity> type, final Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 24.0)
				.add(Attributes.ARMOR, 6.0)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.MOVEMENT_SPEED, 0.23);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, (byte) ShellbackPhase.IDLE.ordinal());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new ShellbackLungeGoal(this));
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(2, new ShellbackMeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(3, new RandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
		// A deflected hit never reaches LivingEntity#hurtServer, so the default target selector has to be
		// told about the attacker through the hurt-by memory; without this goal the mob never answers a
		// blocked hit and keeps strolling.
		this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	public ShellbackPhase getPhase() {
		ShellbackPhase[] phases = ShellbackPhase.values();
		byte id = this.entityData.get(DATA_PHASE);
		return id >= 0 && id < phases.length ? phases[id] : ShellbackPhase.IDLE;
	}

	void setPhase(final ShellbackPhase phase) {
		this.entityData.set(DATA_PHASE, (byte) phase.ordinal());
		// The tick that enters EXPOSED also runs customServerAiStep, so start one below zero
		// to keep the open window at exactly EXPOSED_TICKS ticks.
		this.exposedTicks = phase == ShellbackPhase.EXPOSED ? -1 : 0;
	}

	boolean isLungeReady() {
		return this.lungeCooldown <= 0 && this.getPhase() == ShellbackPhase.IDLE;
	}

	/**
	 * Two vanilla gates keep a creative player out of combat targeting: {@code Mob#asValidTarget}
	 * drops a creative player outright, and {@code LivingEntity#canAttack} answers through
	 * {@code Player#canBeSeenAsEnemy}, which is false while {@code Abilities#invulnerable} is set
	 * (creative and spectator both set it). A summoned Shellback still has to notice a creative
	 * player and wind up at them; the game mode itself refuses the damage, so nothing here makes
	 * the hit land. Both gates are opened for creative players only: spectators stay untargetable
	 * and every other target keeps the vanilla answer, which leaves survival behaviour unchanged.
	 */
	@Override
	public boolean canAttack(final LivingEntity target) {
		return target instanceof Player player && player.isCreative() || super.canAttack(target);
	}

	@Override
	protected LivingEntity asValidTarget(final LivingEntity target) {
		if (target instanceof Player player && player.isCreative()) {
			return target;
		}

		return super.asValidTarget(target);
	}

	/**
	 * Vanilla {@link MeleeAttackGoal} refuses a creative player twice: it will not continue for
	 * one, and it clears the mob's target when the goal stops. The Shellback keeps chasing and
	 * swinging at a creative player instead — the game mode itself refuses the damage — while
	 * spectators stay excluded and every other target goes through the vanilla path untouched.
	 */
	private static final class ShellbackMeleeAttackGoal extends MeleeAttackGoal {
		ShellbackMeleeAttackGoal(final ShellbackEntity shellback, final double speedModifier,
				final boolean followTargetEvenIfNotSeen) {
			super(shellback, speedModifier, followTargetEvenIfNotSeen);
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity current = this.mob.getTarget();

			if (current instanceof Player player && player.isCreative()) {
				return current.isAlive() && this.mob.isWithinHome(current.blockPosition());
			}

			return super.canContinueToUse();
		}

		@Override
		public void stop() {
			LivingEntity current = this.mob.getTarget();

			if (current instanceof Player player && player.isCreative()) {
				this.mob.setAggressive(false);
				this.mob.getNavigation().stop();
				return;
			}

			super.stop();
		}
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (this.lungeCooldown > 0) {
			this.lungeCooldown--;
		}

		if (this.getPhase() == ShellbackPhase.EXPOSED) {
			this.getNavigation().stop();
			this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
			if (++this.exposedTicks >= EXPOSED_TICKS) {
				this.setPhase(ShellbackPhase.IDLE);
				this.lungeCooldown = LUNGE_COOLDOWN_TICKS;
			}
		}
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		if (this.canDeflect(source)) {
			this.deflect(level, source);
			return false;
		}

		return super.hurtServer(level, source, damage);
	}

	/**
	 * Only a melee hit from a living attacker is worth deflecting. A creeper blast does not arrive as a
	 * hand-made {@code explosion(a, b)} with a null direct entity: {@code Explosion#getDefaultDamageSource}
	 * resolves a living explosion source through {@code getIndirectSourceEntity}, which answers the creeper
	 * itself, so an explosion reaches here as {@code direct == causing == creeper}. Every shape check below
	 * then matches a punch, and the shell used to swallow the blast. The damage type is what separates them.
	 */
	private boolean canDeflect(final DamageSource source) {
		if (this.getPhase() == ShellbackPhase.EXPOSED) {
			return false;
		}

		if (!isMeleeAttack(source)) {
			return false;
		}

		Entity direct = source.getDirectEntity();
		Entity attacker = source.getEntity();

		if (direct == null || direct != attacker || !(attacker instanceof LivingEntity)) {
			return false;
		}

		return this.isFrontal(attacker);
	}

	/**
	 * The damage types a living entity produces when it swings at something. {@code LivingEntity#doHurtTarget}
	 * hands out {@code mob_attack} for a mob and {@code player_attack} for a player, so a wolf bite and a sword
	 * swing both stay deflectable; {@code is_player_attack} adds the spear and the mace, which are swings too.
	 * Explosions, fire, falling, magic and projectiles are none of these — they land.
	 */
	private static boolean isMeleeAttack(final DamageSource source) {
		return source.is(DamageTypes.MOB_ATTACK)
				|| source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
				|| source.is(DamageTypeTags.IS_PLAYER_ATTACK);
	}

	private boolean isFrontal(final Entity attacker) {
		Vec3 toAttacker = new Vec3(attacker.getX() - this.getX(), 0.0, attacker.getZ() - this.getZ());

		if (toAttacker.lengthSqr() < 1.0E-6) {
			return true;
		}

		Vec3 facing = Vec3.directionFromRotation(0.0F, this.yBodyRot);
		return facing.dot(toAttacker.normalize()) >= FRONTAL_COS;
	}

	private void deflect(final ServerLevel level, final DamageSource source) {
		Entity attacker = source.getEntity();

		if (attacker != null) {
			if (attacker instanceof LivingEntity livingAttacker) {
				// hurtServer returns before super.hurtServer, so LivingEntity never records the attacker.
				// Do it here, or the mob has no idea it was hit and the retaliation goal has nothing to use.
				this.setLastHurtByMob(livingAttacker);
			}

			Vec3 away = new Vec3(attacker.getX() - this.getX(), 0.0, attacker.getZ() - this.getZ());

			if (away.lengthSqr() > 1.0E-6) {
				away = away.normalize();
				attacker.push(away.x * 0.5, 0.2, away.z * 0.5);
			}
		}

		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, this.getX(), this.getY() + 0.6, this.getZ(), 8, 0.35, 0.35, 0.35, 0.05);
	}

	/** A timed parry interrupts a wind-up or a lunge and opens the shell. */
	@Override
	public void onParried(final LivingEntity defender) {
		ShellbackPhase phase = this.getPhase();

		if (phase == ShellbackPhase.TELEGRAPH || phase == ShellbackPhase.LUNGE) {
			this.setPhase(ShellbackPhase.EXPOSED);
			this.getNavigation().stop();
			this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
		}
	}
}
