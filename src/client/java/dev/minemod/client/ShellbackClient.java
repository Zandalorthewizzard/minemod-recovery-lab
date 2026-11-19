package dev.minemod.client;

import dev.minemod.entity.ModEntities;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class ShellbackClient {
	private ShellbackClient() {
	}

	public static void init() {
		EntityRendererRegistry.register(ModEntities.SHELLBACK, ShellbackRenderer::new);
	}
}
