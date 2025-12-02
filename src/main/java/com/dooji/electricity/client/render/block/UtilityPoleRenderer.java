package com.dooji.electricity.client.render.block;

import com.dooji.electricity.block.UtilityPoleBlock;
import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.client.render.obj.ObjBlockRegistry;
import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.render.obj.ObjInteractionRegistry;
import com.dooji.electricity.client.render.obj.ObjLoader;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.render.obj.ObjRenderUtil;
import com.dooji.electricity.client.render.obj.ObjRendererBase;
import com.dooji.electricity.main.Electricity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class UtilityPoleRenderer extends ObjRendererBase {
	private static final double MAX_RENDER_DISTANCE_SQ = 64 * 64;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;

		Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
		HashSet<BlockPos> seen = new HashSet<>();

		for (UtilityPoleBlockEntity blockEntity : TrackedBlockEntities.ofType(UtilityPoleBlockEntity.class)) {
			seen.add(blockEntity.getBlockPos());
			ObjRenderUtil.withAlignedPose(blockEntity, event.getPoseStack(), mc.renderBuffers().bufferSource(), cameraPos, MAX_RENDER_DISTANCE_SQ, state -> state.getValue(UtilityPoleBlock.FACING),
					UtilityPoleRenderer::rotationForPole,
					(context, pose, buffers) -> renderBaked(context.model(), pose, event.getProjectionMatrix(), context.texture(), context.packedLight(), blockEntity.getBlockPos()));
		}

		cleanupCache(BUFFER_CACHE, seen);
	}

	public static void init() {
		ResourceLocation modelLocation = new ResourceLocation(Electricity.MOD_ID, "models/utility_pole/utility_pole.obj");
		ObjBlockRegistry.register(Electricity.UTILITY_POLE_BLOCK.get(), modelLocation, null);

		ObjInteractionRegistry.register(Electricity.UTILITY_POLE_BLOCK.get(), "insulator_1_Material.023", null);
		ObjInteractionRegistry.register(Electricity.UTILITY_POLE_BLOCK.get(), "insulator_2_Material.009", null);
		ObjInteractionRegistry.register(Electricity.UTILITY_POLE_BLOCK.get(), "insulator_3_Material.016", null);
		ObjInteractionRegistry.register(Electricity.UTILITY_POLE_BLOCK.get(), "insulator_4_Material.001", null);
		ObjInteractionRegistry.register(Electricity.UTILITY_POLE_BLOCK.get(), "insulator_5_Material.051", null);
		ObjInteractionRegistry.register(Electricity.UTILITY_POLE_BLOCK.get(), "insulator_6_Material.037", null);
		ObjInteractionRegistry.register(Electricity.UTILITY_POLE_BLOCK.get(), "insulator_7_Material.030", null);
		ObjInteractionRegistry.register(Electricity.UTILITY_POLE_BLOCK.get(), "insulator_8_Material.058", null);

		calculateAndRegisterBoundingBoxes();
	}

	private static void calculateAndRegisterBoundingBoxes() {
		var model = ObjLoader.getModel(new ResourceLocation(Electricity.MOD_ID, "models/utility_pole/utility_pole.obj"));
		if (model == null) return;

		Map<String, ObjModel.BoundingBox> insulatorBoxes = new HashMap<>();

		String[] insulatorGroups = {"insulator_1_Material.023", "insulator_2_Material.009", "insulator_3_Material.016", "insulator_4_Material.001", "insulator_5_Material.051",
				"insulator_6_Material.037", "insulator_7_Material.030", "insulator_8_Material.058"};

		for (String groupName : insulatorGroups) {
			ObjModel.BoundingBox bbox = model.getBoundingBox(groupName);
			if (bbox != null) {
				insulatorBoxes.put(groupName, bbox);
			}
		}

		ObjBoundingBoxRegistry.registerBoundingBoxes(Electricity.UTILITY_POLE_BLOCK.get(), insulatorBoxes);
	}

	private static float rotationForPole(Direction facing) {
		return switch (facing) {
			case EAST -> 180.0f;
			case SOUTH -> 270.0f;
			case WEST -> 0.0f;
			default -> 90.0f;
		};
	}

	private static void renderBaked(ObjModel model, PoseStack poseStack, Matrix4f projectionMatrix, ResourceLocation texture, int packedLight, BlockPos pos) {
		renderGrouped(model, poseStack, projectionMatrix, texture, packedLight, pos, BUFFER_CACHE);
	}
}
