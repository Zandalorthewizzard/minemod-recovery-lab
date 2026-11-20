package dev.minemod.client;

import dev.minemod.component.ModComponents;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Adds the plated-shield tooltip. Only stacks that actually carry {@link ModComponents#PLATED}
 * get the two extra lines, so a plain vanilla shield stays untouched.
 */
public final class ItemTooltips {
	/** Keys fixed by spec 3.4; S8 owns the lang files. */
	private static final String PLATED_KEY = "tooltip.minemod.plated_shield";
	private static final String PLATED_HINT_KEY = "tooltip.minemod.plated_shield.hint";

	private ItemTooltips() {
	}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
			if (ModComponents.PLATED == null || !stack.has(ModComponents.PLATED)) {
				return;
			}

			lines.add(Component.translatable(PLATED_KEY).withStyle(ChatFormatting.GOLD));
			lines.add(Component.translatable(PLATED_HINT_KEY).withStyle(ChatFormatting.GRAY));
		});
	}
}
