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
import com.dooji.electricity.client.render.obj.ObjRenderer;
import com.dooji.electricity.main.Electricity;
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

@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class UtilityPoleRenderer {
	private static final double MAX_RENDER_DISTANCE_SQ = 64 * 64;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;

		Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
		MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

		for (UtilityPoleBlockEntity blockEntity : TrackedBlockEntities.ofType(UtilityPoleBlockEntity.class)) {
			ObjRenderUtil.withAlignedPose(blockEntity, event.getPoseStack(), bufferSource, cameraPos, MAX_RENDER_DISTANCE_SQ, state -> state.getValue(UtilityPoleBlock.FACING),
					UtilityPoleRenderer::rotationForPole, (context, pose, buffers) -> ObjRenderer.render(context.model(), pose, buffers, context.texture(), context.packedLight()));
		}
		bufferSource.endBatch();
	}

	public static void init() {
		ResourceLocation modelLocation = ResourceLocation.fromNamespaceAndPath(Electricity.MOD_ID, "models/utility_pole/utility_pole.obj");
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
		var model = ObjLoader.getModel(ResourceLocation.fromNamespaceAndPath(Electricity.MOD_ID, "models/utility_pole/utility_pole.obj"));
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
}
