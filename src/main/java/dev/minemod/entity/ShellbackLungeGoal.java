package dev.minemod.entity;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Drives TELEGRAPH to LUNGE and opens the shell. The exposed timer and cooldown live on the
 * entity, so a parry can interrupt either phase.
 */
public class ShellbackLungeGoal extends Goal {
	private static final double MIN_RANGE_SQR = 3.0 * 3.0;
	private static final double MAX_RANGE_SQR = 8.0 * 8.0;
	private static final double LUNGE_SPEED = 1.1;
	private static final double LUNGE_LIFT = 0.25;
	private static final double HIT_INFLATE = 0.3;
	private final ShellbackEntity shellback;
	private ShellbackPhase trackedPhase = ShellbackPhase.IDLE;
	private int ticksInPhase;
	private boolean hitThisLunge;

	public ShellbackLungeGoal(final ShellbackEntity shellback) {
		this.shellback = shellback;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.shellback.getTarget();

		if (target == null || !target.isAlive() || !this.shellback.isLungeReady()) {
			return false;
		}

		double rangeSqr = this.shellback.distanceToSqr(target);
		return rangeSqr >= MIN_RANGE_SQR && rangeSqr <= MAX_RANGE_SQR && this.shellback.hasLineOfSight(target);
	}

	@Override
	public void start() {
		this.trackedPhase = ShellbackPhase.TELEGRAPH;
		// GoalSelector ticks a freshly started goal in the same tick, so begin at -1 to get
		// TELEGRAPH_TICKS full ticks of wind-up after the start tick.
		this.ticksInPhase = -1;
		this.hitThisLunge = false;
		this.shellback.setPhase(ShellbackPhase.TELEGRAPH);
		this.shellback.getNavigation().stop();
		this.playFeedback(SoundEvents.RAVAGER_ROAR, ParticleTypes.LARGE_SMOKE, 10);
	}

	@Override
	public boolean canContinueToUse() {
		ShellbackPhase phase = this.shellback.getPhase();
		return phase == ShellbackPhase.TELEGRAPH || phase == ShellbackPhase.LUNGE || phase == ShellbackPhase.EXPOSED;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ShellbackPhase phase = this.shellback.getPhase();

		if (phase != this.trackedPhase) {
			this.trackedPhase = phase;
			this.ticksInPhase = 0;
		}

		this.ticksInPhase++;

		switch (phase) {
			case TELEGRAPH -> this.tickTelegraph();
			case LUNGE -> this.tickLunge();
			default -> {
			}
		}
	}

	private void tickTelegraph() {
		LivingEntity target = this.shellback.getTarget();
		this.shellback.getNavigation().stop();

		if (target == null || !target.isAlive()) {
			this.shellback.setPhase(ShellbackPhase.IDLE);
			return;
		}

		this.shellback.getLookControl().setLookAt(target, 30.0F, 30.0F);

		if (this.ticksInPhase >= ShellbackEntity.TELEGRAPH_TICKS) {
			this.beginLunge(target);
		}
	}

	private void beginLunge(final LivingEntity target) {
		Vec3 toTarget = new Vec3(target.getX() - this.shellback.getX(), 0.0, target.getZ() - this.shellback.getZ());

		if (toTarget.lengthSqr() > 1.0E-6) {
			toTarget = toTarget.normalize();
		}

		this.hitThisLunge = false;
		this.shellback.setDeltaMovement(toTarget.x * LUNGE_SPEED, LUNGE_LIFT, toTarget.z * LUNGE_SPEED);
		this.shellback.setPhase(ShellbackPhase.LUNGE);
	}

	private void tickLunge() {
		LivingEntity target = this.shellback.getTarget();

		if (!this.hitThisLunge && target != null && target.isAlive()) {
			AABB hitBox = this.shellback.getBoundingBox().inflate(HIT_INFLATE);

			if (hitBox.intersects(target.getBoundingBox())) {
				this.hitThisLunge = true;

				if (this.shellback.level() instanceof ServerLevel level && this.shellback.doHurtTarget(level, target)) {
					this.playFeedback(SoundEvents.PLAYER_ATTACK_STRONG, ParticleTypes.CRIT, 8);
				}

				this.openShell();
				return;
			}
		}

		if (this.shellback.onGround() && this.ticksInPhase >= 4) {
			this.openShell();
			return;
		}

		if (this.ticksInPhase >= ShellbackEntity.LUNGE_MAX_TICKS) {
			this.openShell();
		}
	}

	private void openShell() {
		this.shellback.getNavigation().stop();
		this.shellback.setDeltaMovement(0.0, this.shellback.getDeltaMovement().y, 0.0);
		this.shellback.setPhase(ShellbackPhase.EXPOSED);
	}

	private void playFeedback(final SoundEvent sound, final ParticleOptions particle, final int count) {
		if (this.shellback.level() instanceof ServerLevel level) {
			level.playSound(null, this.shellback.getX(), this.shellback.getY(), this.shellback.getZ(), sound, SoundSource.HOSTILE, 1.0F, 0.8F);
			level.sendParticles(particle, this.shellback.getX(), this.shellback.getY() + 0.5, this.shellback.getZ(), count, 0.4, 0.4, 0.4, 0.02);
		}
	}

	@Override
	public void stop() {
		this.ticksInPhase = 0;
		this.trackedPhase = this.shellback.getPhase();

		if (this.shellback.getPhase() == ShellbackPhase.TELEGRAPH) {
			this.shellback.setPhase(ShellbackPhase.IDLE);
		}
	}
}
