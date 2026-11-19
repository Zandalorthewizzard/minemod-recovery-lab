package dev.minemod.entity;

import dev.minemod.MinemodMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Registers {@code minemod:shellback} and its attributes. Spawn placement and biome spawns
 * belong to Scope 6.
 */
public final class ModEntities {
	public static final ResourceKey<EntityType<?>> SHELLBACK_KEY = shellbackKey();

	/** Cave quadruped with a 0.9 x 0.7 hitbox (Scope 5 contract). */
	public static final EntityType<ShellbackEntity> SHELLBACK = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, SHELLBACK_KEY,
			EntityType.Builder.of(ShellbackEntity::new, MobCategory.MONSTER)
					.sized(0.9F, 0.7F).clientTrackingRange(8)
					.build(SHELLBACK_KEY)
	);

	private ModEntities() {
	}

	private static ResourceKey<EntityType<?>> shellbackKey() {
		return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MinemodMod.MOD_ID, "shellback"));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(SHELLBACK, ShellbackEntity.createAttributes());
	}
}
