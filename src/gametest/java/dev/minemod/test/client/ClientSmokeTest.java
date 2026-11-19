package dev.minemod.test.client;

import java.util.List;
import java.util.Locale;

import dev.minemod.component.ModComponents;
import dev.minemod.entity.ShellbackEntity;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;

/**
 * Client smoke test for the Shellback panel: a real singleplayer world, a summoned Shellback, an
 * upgraded shield in the inventory and its tooltip. Screenshots are plain captures - no pixel
 * comparison - so the test proves the client can render the entity without falling over instead of
 * pinning the renderer to a specific frame.
 */
public class ClientSmokeTest implements FabricClientGameTest {
	private static final String PLATED_KEY = "tooltip.minemod.plated_shield";
	private static final String PLATED_HINT_KEY = "tooltip.minemod.plated_shield.hint";
	private static final String PLATED_SHIELD_STACK = "minecraft:shield[minemod:plated={}]";
	private static final int ENTITY_TIMEOUT_TICKS = 400;

	@Override
	public void runTest(final ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create()) {
			context.waitTicks(20);

			Vec3 spawn = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().position());
			world.getServer().runCommand(String.format(Locale.ROOT, "summon minemod:shellback %.2f %.2f %.2f", spawn.x - 5.0, spawn.y, spawn.z));

			context.waitFor(mc -> !mc.level.getEntitiesOfClass(ShellbackEntity.class, mc.player.getBoundingBox().inflate(64.0)).isEmpty(), ENTITY_TIMEOUT_TICKS);

			// Look west and slightly down, where the Shellback was summoned (the sun sits east at
			// this time of day), then capture the world view.
			context.getInput().lookAt(90.0F, 12.0F);
			context.waitTicks(20);
			context.takeScreenshot("s7-shellback-smoke");

			world.getServer().runCommand("give @p " + PLATED_SHIELD_STACK);
			context.waitTicks(10);

			List<Component> tooltip = context.computeOnClient(mc -> {
				ItemStack plated = findPlatedShield(mc);

				if (plated.isEmpty()) {
					throw new AssertionError("no stack with minemod:plated in the player inventory");
				}

				return plated.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
			});

			assertTooltipKey(tooltip, PLATED_KEY);
			assertTooltipKey(tooltip, PLATED_HINT_KEY);

			// Hover the hotbar slot holding the shield so the tooltip is visible in the capture.
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(5);

			double[] cursor = context.computeOnClient(mc -> {
				int scale = mc.getWindow().getGuiScale();
				double left = (mc.getWindow().getGuiScaledWidth() - 176.0) / 2.0;
				double top = (mc.getWindow().getGuiScaledHeight() - 166.0) / 2.0;
				// First hotbar slot: 8px in from the GUI edge, 142px down; +8 centres on the slot.
				return new double[] {(left + 8.0 + 8.0) * scale, (top + 142.0 + 8.0) * scale};
			});
			context.getInput().setCursorPos(cursor[0], cursor[1]);
			context.waitTicks(5);
			context.takeScreenshot("s7-plated-shield-tooltip");
		}
	}

	private static ItemStack findPlatedShield(final Minecraft minecraft) {
		if (ModComponents.PLATED == null) {
			return ItemStack.EMPTY;
		}

		for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = minecraft.player.getInventory().getItem(slot);

			if (stack.has(ModComponents.PLATED)) {
				return stack;
			}
		}

		return ItemStack.EMPTY;
	}

	private static void assertTooltipKey(final List<Component> tooltip, final String key) {
		for (Component line : tooltip) {
			if (key.equals(line.getString()) || line.getContents() instanceof TranslatableContents translatable && key.equals(translatable.getKey())) {
				return;
			}
		}

		throw new AssertionError("tooltip is missing " + key + ": " + tooltip);
	}
}
