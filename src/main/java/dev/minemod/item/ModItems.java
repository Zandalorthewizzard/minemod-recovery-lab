package dev.minemod.item;

import dev.minemod.MinemodMod;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Items registered by the mod. */
public final class ModItems {
	/** Trophy dropped by the shellback; the crafting material of the shield upgrade. */
	public static final ResourceKey<Item> SHELLBACK_PLATE_KEY = ResourceKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(MinemodMod.MOD_ID, "shellback_plate")
	);
	public static final Item SHELLBACK_PLATE = Registry.register(
			BuiltInRegistries.ITEM,
			SHELLBACK_PLATE_KEY.identifier(),
			new Item(new Item.Properties().setId(SHELLBACK_PLATE_KEY).stacksTo(64).rarity(Rarity.UNCOMMON))
	);

	private ModItems() {
	}

	public static void init() {
		// Fabric API 0.165.2 has no ItemGroupEvents, so the vanilla tab is extended by event.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.accept(SHELLBACK_PLATE));
	}
}
