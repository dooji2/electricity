package com.dooji.electricity.client.render.block;

import com.dooji.electricity.block.ElectricCabinBlock;
import com.dooji.electricity.block.ElectricCabinBlockEntity;
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
public class ElectricCabinRenderer {
	private static final double MAX_RENDER_DISTANCE_SQ = 64 * 64;
	private static final Logger LOGGER = LoggerFactory.getLogger(Electricity.MOD_ID);

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;

		Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
		MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

		for (ElectricCabinBlockEntity cabin : TrackedBlockEntities.ofType(ElectricCabinBlockEntity.class)) {
			ObjRenderUtil.withAlignedPose(cabin, event.getPoseStack(), bufferSource, cameraPos, MAX_RENDER_DISTANCE_SQ, state -> state.getValue(ElectricCabinBlock.FACING),
					ElectricCabinRenderer::rotationForCabin, (context, pose, buffers) -> ObjRenderer.render(context.model(), pose, buffers, context.texture(), context.packedLight()));
		}
		bufferSource.endBatch();
	}

	public static void init() {
		ResourceLocation modelLocation = new ResourceLocation(Electricity.MOD_ID, "models/electric_cab/cab.obj");
		ObjBlockRegistry.register(Electricity.ELECTRIC_CABIN_BLOCK.get(), modelLocation, null);

		ObjInteractionRegistry.register(Electricity.ELECTRIC_CABIN_BLOCK.get(), "insulator_input_Material.065", null);
		ObjInteractionRegistry.register(Electricity.ELECTRIC_CABIN_BLOCK.get(), "insulatoroutput_Material.044", null);

		calculateAndRegisterBoundingBoxes();
	}

	private static void calculateAndRegisterBoundingBoxes() {
		var model = ObjLoader.getModel(new ResourceLocation(Electricity.MOD_ID, "models/electric_cab/cab.obj"));
		if (model == null) {
			LOGGER.error("Failed to load Electric Cabin model");
			return;
		}

		Map<String, ObjModel.BoundingBox> insulatorBoxes = new HashMap<>();

		String[] insulatorGroups = {"insulator_input_Material.065", "insulatoroutput_Material.044"};

		for (String groupName : insulatorGroups) {
			ObjModel.BoundingBox bbox = model.getBoundingBox(groupName);
			if (bbox != null) {
				insulatorBoxes.put(groupName, bbox);
			} else {
				LOGGER.warn("Missing Electric Cabin insulator group: {}", groupName);
			}
		}

		ObjBoundingBoxRegistry.registerBoundingBoxes(Electricity.ELECTRIC_CABIN_BLOCK.get(), insulatorBoxes);
	}

	private static float rotationForCabin(Direction facing) {
		return switch (facing) {
			case EAST -> 0.0f;
			case SOUTH -> 270.0f;
			case WEST -> 180.0f;
			default -> 90.0f;
		};
	}
}
