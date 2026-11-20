package dev.minemod.client;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.minemod.entity.ShellbackEntity;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.silverfish.SilverfishModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Minimal Shellback renderer: the vanilla silverfish model layer and the vanilla silverfish
 * texture. No custom {@code ModelPart}s, no new geometry and no phase poses - the point of this
 * recovery scope is only that the client does not fall over when a Shellback appears in a world.
 *
 * <p>Both references are vanilla 26.3 assets, so neither a model layer registration nor an access
 * widener is needed: {@code ModelLayers.SILVERFISH} is baked by the vanilla model set and the
 * texture lives in {@code minecraft}.
 */
public final class ShellbackRenderer extends MobRenderer<ShellbackEntity, LivingEntityRenderState, SilverfishModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/silverfish/silverfish.png");
	private static final float MODEL_SCALE = 1.5F;

	public ShellbackRenderer(final EntityRendererProvider.Context context) {
		super(context, new SilverfishModel(context.bakeLayer(ModelLayers.SILVERFISH)), 0.5F);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(final LivingEntityRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void scale(final LivingEntityRenderState state, final PoseStack poseStack) {
		poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
	}
}
