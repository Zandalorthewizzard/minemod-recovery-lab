package dev.minemod.api;

import net.minecraft.world.entity.LivingEntity;

/**
 * Implemented by mobs that react to a successful timed parry.
 */
public interface Staggerable {
	/**
	 * Called on the attacker when its melee hit is parried by {@code defender}.
	 */
	void onParried(LivingEntity defender);
}
