package dev.minemod.client.shield;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.equipment.ShieldModel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Special renderer for {@code minemod:plated_shield}: the vanilla shield model with the mod's
 * own stone-plated sprites from the vanilla shield atlas. Draw order is fixed: base plate,
 * vanilla banner patterns (with the base-colour layer), then the stone rim overlay.
 *
 * <p>The overlay is alpha-cutout in both branches ({@code entity_cutout_cull}, or the cutout
 * glint variant for an enchanted stack), so its transparent centre, handle and back never
 * overwrite the plate or the patterns. Glint is only the natural {@link ItemStack#hasFoil()}.
 */
public class PlatedShieldSpecialRenderer implements SpecialModelRenderer<DataComponentMap> {
	private static final SpriteId BASE = new SpriteId(Sheets.SHIELD_SHEET, Identifier.fromNamespaceAndPath("minemod", "entity/shield/plated_shield_base"));
	private static final SpriteId BASE_NO_PATTERN = new SpriteId(Sheets.SHIELD_SHEET, Identifier.fromNamespaceAndPath("minemod", "entity/shield/plated_shield_base_nopattern"));
	private static final SpriteId OVERLAY = new SpriteId(Sheets.SHIELD_SHEET, Identifier.fromNamespaceAndPath("minemod", "entity/shield/plated_shield_overlay"));

	private final SpriteGetter sprites;
	private final ShieldModel model;

	public PlatedShieldSpecialRenderer(final SpriteGetter sprites, final ShieldModel model) {
		this.sprites = sprites;
		this.model = model;
	}

	@Override
	public @Nullable DataComponentMap extractArgument(final ItemStack stack) {
		return stack.immutableComponents();
	}

	@Override
	public void submit(
		final @Nullable DataComponentMap components,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final int overlayCoords,
		final boolean hasFoil,
		final int outlineColor
	) {
		BannerPatternLayers patterns = components != null
			? (BannerPatternLayers)components.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
			: BannerPatternLayers.EMPTY;
		DyeColor baseColor = components != null ? (DyeColor)components.get(DataComponents.BASE_COLOR) : null;
		boolean hasPatterns = !patterns.layers().isEmpty() || baseColor != null;
		SpriteId base = hasPatterns ? BASE : BASE_NO_PATTERN;

		// 1. plate: plain stone when there is nothing to show, recessed plate otherwise
		if (hasFoil && !hasPatterns) {
			submitNodeCollector.submitModel(this.model, Unit.INSTANCE, poseStack, RenderTypes.entitySolidGlint(base.atlasLocation()), lightCoords, overlayCoords, -1, this.sprites.get(base), outlineColor);
		} else {
			submitNodeCollector.submitModel(this.model, Unit.INSTANCE, poseStack, lightCoords, overlayCoords, -1, base, this.sprites, outlineColor);
		}
		// 2. vanilla banner patterns and base colour, in the vanilla order
		if (hasPatterns) {
			BannerRenderer.submitPatterns(this.sprites, poseStack, submitNodeCollector, lightCoords, overlayCoords, this.model, Unit.INSTANCE, false, Objects.requireNonNullElse(baseColor, DyeColor.WHITE), patterns);
			if (hasFoil) {
				submitNodeCollector.order(patterns.layers().size() + 2)
					.submitModel(this.model, Unit.INSTANCE, poseStack, RenderTypes.patternedShieldGlint(), lightCoords, overlayCoords, -1, this.sprites.get(base), 0);
			}
		}
		// 3. stone rim and amber rivets on top of everything. Alpha-cutout in both branches, so
		// the transparent centre, handle and back never overwrite the plate or the patterns.
		OrderedSubmitNodeCollector overlay = submitNodeCollector.order(hasPatterns ? patterns.layers().size() + 3 : 1);
		if (hasFoil) {
			overlay.submitModel(this.model, Unit.INSTANCE, poseStack, RenderTypes.armorCutoutNoCullGlint(OVERLAY.atlasLocation()), lightCoords, overlayCoords, -1, this.sprites.get(OVERLAY), outlineColor);
		} else {
			overlay.submitModel(this.model, Unit.INSTANCE, poseStack, RenderTypes.entityCutoutCull(OVERLAY.atlasLocation()), lightCoords, overlayCoords, -1, this.sprites.get(OVERLAY), outlineColor);
		}
	}

	@Override
	public void getExtents(final Consumer<Vector3fc> output) {
		this.model.root().getExtentsForGui(new PoseStack(), output);
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked<DataComponentMap> {
		public static final PlatedShieldSpecialRenderer.Unbaked INSTANCE = new PlatedShieldSpecialRenderer.Unbaked();
		public static final MapCodec<PlatedShieldSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

		@Override
		public MapCodec<PlatedShieldSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<DataComponentMap> bake(final SpecialModelRenderer.BakingContext context) {
			return new PlatedShieldSpecialRenderer(context.sprites(), new ShieldModel(context.entityModelSet().bakeLayer(ModelLayers.SHIELD)));
		}
	}
}
