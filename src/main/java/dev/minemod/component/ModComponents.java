package dev.minemod.component;

import dev.minemod.MinemodMod;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/**
 * Data components registered by the mod. {@link #PLATED} is the persistent,
 * synchronised marker of an upgraded shield.
 */
public final class ModComponents {
	/**
	 * Marker component of an upgraded shield. Unit value, so its JSON payload is {@code {}}.
	 * Registered in {@link #init()} before datapack recipes are loaded.
	 */
	public static DataComponentType<Unit> PLATED;

	private ModComponents() {
	}

	public static void init() {
		if (PLATED != null) {
			return;
		}

		PLATED = Registry.register(
				BuiltInRegistries.DATA_COMPONENT_TYPE,
				Identifier.fromNamespaceAndPath(MinemodMod.MOD_ID, "plated"),
				DataComponentType.<Unit>builder()
						.persistent(Unit.CODEC)
						.networkSynchronized(Unit.STREAM_CODEC)
						.build()
		);
	}
}
