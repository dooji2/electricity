package com.dooji.electricity.client.render.block;

import com.dooji.electricity.block.WindTurbineBlock;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.client.render.obj.ObjBlockRegistry;
import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.render.obj.ObjInteractionRegistry;
import com.dooji.electricity.client.render.obj.ObjLoader;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.render.obj.ObjRenderUtil;
import com.dooji.electricity.client.render.obj.ObjRenderer;
import com.dooji.electricity.main.Electricity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class WindTurbineRenderer {
	private static final double MAX_RENDER_DISTANCE_SQ = 128 * 128;
	private static final Logger LOGGER = LoggerFactory.getLogger(Electricity.MOD_ID);

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;

		Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
		MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

		for (WindTurbineBlockEntity turbine : TrackedBlockEntities.ofType(WindTurbineBlockEntity.class)) {
			ObjRenderUtil.withAlignedPose(turbine, event.getPoseStack(), bufferSource, cameraPos, MAX_RENDER_DISTANCE_SQ, state -> state.getValue(WindTurbineBlock.FACING),
					WindTurbineRenderer::rotationForTurbine,
					(context, pose, buffers) -> renderWindTurbineWithAnimation(context.model(), pose, buffers, context.texture(), context.packedLight(), turbine));
		}
		bufferSource.endBatch();
	}

	private static void renderWindTurbineWithAnimation(ObjModel model, PoseStack poseStack, MultiBufferSource bufferSource, ResourceLocation textureLocation, int packedLight,
			WindTurbineBlockEntity blockEntity) {
		float rotation1 = blockEntity.getRotation1();
		float rotation2 = blockEntity.getRotation2();

		Vec3 hubCenter = null;
		for (Map.Entry<String, ObjModel.ObjGroup> entry : model.groups.entrySet()) {
			if (entry.getKey().equals("rotate_2_Plastic")) {
				hubCenter = calculateGroupCenter(entry.getValue());
				break;
			}
		}

		if (hubCenter == null) {
			hubCenter = new Vec3(0.0, 12.65, 0.95);
		}

		for (Map.Entry<String, ObjModel.ObjGroup> entry : model.groups.entrySet()) {
			String groupName = entry.getKey();
			ObjModel.ObjGroup group = entry.getValue();

			poseStack.pushPose();

			if (groupName.equals("rotate_1_Plastic")) {
				poseStack.translate(hubCenter.x, hubCenter.y, hubCenter.z);
				poseStack.mulPose(Axis.ZP.rotationDegrees(rotation1));
				poseStack.translate(-hubCenter.x, -hubCenter.y, -hubCenter.z);
			} else if (groupName.equals("rotate_2_Plastic")) {
				poseStack.translate(hubCenter.x, hubCenter.y, hubCenter.z);
				poseStack.mulPose(Axis.ZP.rotationDegrees(rotation2));
				poseStack.translate(-hubCenter.x, -hubCenter.y, -hubCenter.z);
			}

			ObjRenderer.renderGroup(group, poseStack, bufferSource, model, textureLocation, packedLight);
			poseStack.popPose();
		}
	}

	private static Vec3 calculateGroupCenter(ObjModel.ObjGroup group) {
		if (group.vertices.isEmpty()) return Vec3.ZERO;

		double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
		double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
		double minZ = Double.MAX_VALUE, maxZ = Double.MIN_VALUE;

		for (Vector3f vertex : group.vertices) {
			minX = Math.min(minX, vertex.x);
			maxX = Math.max(maxX, vertex.x);
			minY = Math.min(minY, vertex.y);
			maxY = Math.max(maxY, vertex.y);
			minZ = Math.min(minZ, vertex.z);
			maxZ = Math.max(maxZ, vertex.z);
		}

		return new Vec3((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
	}

	public static void init() {
		ResourceLocation modelLocation = ResourceLocation.fromNamespaceAndPath(Electricity.MOD_ID, "models/wind_turbine/wind_turbine.obj");
		ObjBlockRegistry.register(Electricity.WIND_TURBINE_BLOCK.get(), modelLocation, null);

		ObjInteractionRegistry.register(Electricity.WIND_TURBINE_BLOCK.get(), "insulator_Plastic", null);

		calculateAndRegisterBoundingBoxes();
	}

	private static void calculateAndRegisterBoundingBoxes() {
		var model = ObjLoader.getModel(ResourceLocation.fromNamespaceAndPath(Electricity.MOD_ID, "models/wind_turbine/wind_turbine.obj"));
		if (model == null) {
			LOGGER.error("Failed to load Wind Turbine model");
			return;
		}

		Map<String, ObjModel.BoundingBox> insulatorBoxes = new HashMap<>();

		String[] insulatorGroups = {"insulator_Plastic"};

		for (String groupName : insulatorGroups) {
			ObjModel.BoundingBox bbox = model.getBoundingBox(groupName);
			if (bbox != null) {
				insulatorBoxes.put(groupName, bbox);
			} else {
				LOGGER.warn("Missing Wind Turbine insulator group: {}", groupName);
			}
		}

		ObjBoundingBoxRegistry.registerBoundingBoxes(Electricity.WIND_TURBINE_BLOCK.get(), insulatorBoxes);
	}

	private static float rotationForTurbine(Direction facing) {
		return switch (facing) {
			case EAST -> 90.0f;
			case SOUTH -> 0.0f;
			case WEST -> 270.0f;
			default -> 180.0f;
		};
	}
}
