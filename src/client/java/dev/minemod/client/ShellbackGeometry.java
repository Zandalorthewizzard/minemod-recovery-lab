package dev.minemod.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Rest geometry for the Shellback: a low, armoured quadruped with three overlapping stone
 * plates, amber eyes and a soft underside.  Pure geometry only - no phases, renderer,
 * registries or render state - so S7 can register the layer and animate the parts by name.
 *
 * <p>Units are vanilla model pixels (y grows downwards, -z is the front).  Pivots, cube
 * origins and {@link CubeDeformation} grow values mirror the {@code UV_LAYOUT} table in
 * {@code tools/gen_textures.py}; the texture is
 * {@code minemod:textures/entity/shellback.png} at 64x64.
 */
public final class ShellbackGeometry {
	public static final String BODY = "body";
	public static final String HEAD = "head";
	public static final String PLATE_FRONT = "plate_front";
	public static final String PLATE_MID = "plate_mid";
	public static final String PLATE_REAR = "plate_rear";
	public static final String TAIL = "tail";
	public static final String UNDERSIDE = "underside";
	public static final String LEG_FRONT_LEFT = "leg_front_left";
	public static final String LEG_FRONT_RIGHT = "leg_front_right";
	public static final String LEG_BACK_LEFT = "leg_back_left";
	public static final String LEG_BACK_RIGHT = "leg_back_right";

	public static final int TEXTURE_WIDTH = 64;
	public static final int TEXTURE_HEIGHT = 64;

	private ShellbackGeometry() {
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild(BODY, cube(0, 0, -4.0F, -6.0F, -6.0F, 8.0F, 6.0F, 12.0F, 0.0F, 0.0F, 0.0F), PartPose.offset(0.0F, 20.0F, 0.0F));
		root.addOrReplaceChild(HEAD, cube(40, 0, -3.0F, -3.0F, -5.0F, 6.0F, 5.0F, 5.0F, 0.0F, 0.0F, 0.0F), PartPose.offset(0.0F, 17.0F, -6.0F));
		root.addOrReplaceChild(PLATE_FRONT, cube(0, 18, -5.0F, -3.0F, -5.0F, 10.0F, 3.0F, 5.0F, 0.0F, 0.0F, 0.0F), PartPose.offset(0.0F, 16.0F, -3.0F));
		root.addOrReplaceChild(PLATE_MID, cube(30, 18, -5.0F, -3.0F, -3.0F, 10.0F, 3.0F, 5.0F, 0.05F, 0.0F, 0.0F), PartPose.offset(0.0F, 15.0F, -1.0F));
		root.addOrReplaceChild(PLATE_REAR, cube(0, 26, -5.0F, -3.0F, -2.0F, 10.0F, 3.0F, 5.0F, 0.1F, 0.0F, 0.0F), PartPose.offset(0.0F, 14.0F, 2.0F));
		root.addOrReplaceChild(TAIL, cube(30, 26, -1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F, 0.0F, 0.0F, 0.0F), PartPose.offset(0.0F, 16.0F, 6.0F));
		root.addOrReplaceChild(UNDERSIDE, cube(0, 34, -3.5F, 0.0F, -5.0F, 7.0F, 1.0F, 10.0F, -0.02F, -0.02F, 0.0F), PartPose.offset(0.0F, 20.0F, 0.0F));
		root.addOrReplaceChild(LEG_FRONT_LEFT, cube(0, 45, -1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F, 0.0F, 0.0F, 0.0F), PartPose.offset(2.0F, 20.0F, -4.0F));
		root.addOrReplaceChild(LEG_FRONT_RIGHT, cube(12, 45, -1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F, 0.0F, 0.0F, 0.0F), PartPose.offset(-2.0F, 20.0F, -4.0F));
		root.addOrReplaceChild(LEG_BACK_LEFT, cube(24, 45, -1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F, 0.0F, 0.0F, 0.0F), PartPose.offset(2.0F, 20.0F, 4.0F));
		root.addOrReplaceChild(LEG_BACK_RIGHT, cube(36, 45, -1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F, 0.0F, 0.0F, 0.0F), PartPose.offset(-2.0F, 20.0F, 4.0F));
		return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
	}

	private static CubeListBuilder cube(int u, int v, float x, float y, float z,
			float w, float h, float d, float growX, float growY, float growZ) {
		return CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d, new CubeDeformation(growX, growY, growZ));
	}
}
