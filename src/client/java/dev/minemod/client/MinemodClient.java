package dev.minemod.client;

import net.fabricmc.api.ClientModInitializer;

public final class MinemodClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ShellbackClient.init();
		ItemTooltips.init();
	}
}
