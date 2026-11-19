package dev.minemod.client.shield;

import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;

/**
 * Registers the {@code minemod:plated_shield} special model codec.
 *
 * <p>Kept separate from the shared client entrypoint on purpose: the call is made
 * by the client bootstrap scope, not from {@code dev.minemod.client}.
 */
public final class PlatedShieldClient {
	public static final String PLATED_SHIELD_MODEL_ID = "minemod:plated_shield";

	public static void register() {
		SpecialModelRenderers.ID_MAPPER.put(Identifier.parse(PLATED_SHIELD_MODEL_ID), PlatedShieldSpecialRenderer.Unbaked.MAP_CODEC);
	}

	private PlatedShieldClient() {
	}
}
