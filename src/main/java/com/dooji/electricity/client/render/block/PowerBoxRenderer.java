package com.dooji.electricity.client.render.block;

import com.dooji.electricity.block.PowerBoxBlock;
import com.dooji.electricity.block.PowerBoxBlockEntity;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PowerBoxRenderer {
	private static final double MAX_RENDER_DISTANCE_SQ = 64 * 64;
	private static final Logger LOGGER = LoggerFactory.getLogger(Electricity.MOD_ID);

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;

		Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
		MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

		for (PowerBoxBlockEntity powerBox : TrackedBlockEntities.ofType(PowerBoxBlockEntity.class)) {
			ObjRenderUtil.withAlignedPose(powerBox, event.getPoseStack(), bufferSource, cameraPos, MAX_RENDER_DISTANCE_SQ, state -> state.getValue(PowerBoxBlock.FACING),
					PowerBoxRenderer::rotationForPowerBox, (context, pose, buffers) -> ObjRenderer.render(context.model(), pose, buffers, context.texture(), context.packedLight()));
		}
		bufferSource.endBatch();
	}

	public static void init() {
		ResourceLocation modelLocation = ResourceLocation.fromNamespaceAndPath(Electricity.MOD_ID, "models/power_box/power_box.obj");
		ObjBlockRegistry.register(Electricity.POWER_BOX_BLOCK.get(), modelLocation, null);

		ObjInteractionRegistry.register(Electricity.POWER_BOX_BLOCK.get(), "insulator_Material", null);

		calculateAndRegisterBoundingBoxes();
	}

	private static void calculateAndRegisterBoundingBoxes() {
		var model = ObjLoader.getModel(ResourceLocation.fromNamespaceAndPath(Electricity.MOD_ID, "models/power_box/power_box.obj"));
		if (model == null) {
			LOGGER.error("Failed to load Power Box model");
			return;
		}

		Map<String, ObjModel.BoundingBox> insulatorBoxes = new HashMap<>();

		String[] insulatorGroups = {"insulator_Material"};

		for (String groupName : insulatorGroups) {
			ObjModel.BoundingBox bbox = model.getBoundingBox(groupName);
			if (bbox != null) {
				insulatorBoxes.put(groupName, bbox);
			} else {
				LOGGER.warn("Missing Power Box insulator group: {}", groupName);
			}
		}

		ObjBoundingBoxRegistry.registerBoundingBoxes(Electricity.POWER_BOX_BLOCK.get(), insulatorBoxes);
	}

	private static float rotationForPowerBox(Direction facing) {
		return switch (facing) {
			case EAST -> 0.0f;
			case SOUTH -> 270.0f;
			case WEST -> 180.0f;
			default -> 90.0f;
		};
	}
}
