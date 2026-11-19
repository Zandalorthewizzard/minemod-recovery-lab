package dev.minemod.spawn;

import dev.minemod.MinemodMod;
import dev.minemod.entity.ModEntities;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/** Spawn egg of the shellback, kept next to the spawn rules it belongs to. */
public final class ModSpawnEggs {
	public static final ResourceKey<Item> SHELLBACK_SPAWN_EGG_KEY = ResourceKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath(MinemodMod.MOD_ID, "shellback_spawn_egg"));
	public static final Item SHELLBACK_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, SHELLBACK_SPAWN_EGG_KEY,
			new SpawnEggItem(new Item.Properties().setId(SHELLBACK_SPAWN_EGG_KEY).spawnEgg(ModEntities.SHELLBACK)));

	private ModSpawnEggs() {
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(SHELLBACK_SPAWN_EGG));
	}
}