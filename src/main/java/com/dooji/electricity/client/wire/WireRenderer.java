package com.dooji.electricity.client.wire;

import com.dooji.electricity.block.ElectricCabinBlockEntity;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.client.render.obj.ObjRaycaster;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.wire.WireConnection;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Electricity.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WireRenderer {
	private static final WireStyle ACTIVE_WIRE = new WireStyle(0, 0, 0, 255);
	private static final int FULL_BRIGHT = 15728880;
	private static final double MIN_LENGTH = 0.1;
	private static final double WIRE_RADIUS = 0.025;
	private static final int WIRE_SIDES = 12;
	private static final int DEFLECTION_SIDES = 8;
	private static final double DEFLECTION_STEP = 0.25;
	private static final double SEGMENT_UNIT = 0.1;
	private static final ResourceLocation WIRE_TEXTURE = new ResourceLocation("minecraft", "block/black_wool");

	private record WireStyle(int red, int green, int blue, int alpha) {
	}

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) return;

		PoseStack poseStack = event.getPoseStack();
		renderAllWires(minecraft, poseStack);
	}

	public static void renderAllWires(Minecraft minecraft, PoseStack poseStack) {
		Collection<WireConnection> connections = WireManagerClient.getAllWireConnections();
		ClientLevel level = minecraft.level;
		if (level == null) return;

		Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
		MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

		for (WireConnection connection : connections) {
			renderWire(level, connection, poseStack, bufferSource, cameraPos);
		}

		renderWirePreview(minecraft, poseStack, bufferSource, cameraPos);
		bufferSource.endBatch();
	}

	private static void renderWire(ClientLevel level, WireConnection connection, PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
		Vec3 startConnectionPoint = WireManagerClient.getWirePosition(level, connection.getStartInsulatorId(), connection.getStartBlockPos());
		Vec3 endConnectionPoint = WireManagerClient.getWirePosition(level, connection.getEndInsulatorId(), connection.getEndBlockPos());

		if (startConnectionPoint == null || endConnectionPoint == null) return;

		renderWireSpan(startConnectionPoint, endConnectionPoint, cameraPos, poseStack, bufferSource, FULL_BRIGHT, ACTIVE_WIRE);
	}

	private static void renderWirePreview(Minecraft minecraft, PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
		Vec3 pendingPos = WireManagerClient.getPendingConnection();
		if (pendingPos == null || minecraft.player == null) return;

		Vec3 startConnectionPoint = pendingPos;
		Vec3 endConnectionPoint = resolvePreviewEndpoint(minecraft, startConnectionPoint);
		if (endConnectionPoint == null) return;

		renderWireSpan(startConnectionPoint, endConnectionPoint, cameraPos, poseStack, bufferSource, FULL_BRIGHT, ACTIVE_WIRE);
	}

	private static void renderWireSpan(Vec3 start, Vec3 end, Vec3 cameraPos, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, WireStyle style) {
		if (start == null || end == null) return;

		Vec3 span = end.subtract(start);
		if (span.lengthSqr() < MIN_LENGTH * MIN_LENGTH) return;

		poseStack.pushPose();
		Vec3 viewStart = start.subtract(cameraPos);
		poseStack.translate(viewStart.x, viewStart.y, viewStart.z);

		TextureAtlasSprite sprite = getWireSprite();
		if (WirePhysics.shouldUseDeflection(start, end)) {
			renderDeflectedSpan(Vec3.ZERO, span, poseStack, bufferSource, packedLight, style, sprite);
		} else {
			renderStraightSpan(Vec3.ZERO, span, poseStack, bufferSource, packedLight, style, sprite);
		}

		poseStack.popPose();
	}

	private static void renderStraightSpan(Vec3 startVec, Vec3 endVec, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, WireStyle style, TextureAtlasSprite sprite) {
		Vec3 direction = endVec.subtract(startVec);
		double distance = direction.length();
		if (distance < MIN_LENGTH) return;

		poseStack.pushPose();
		poseStack.translate(startVec.x, startVec.y, startVec.z);

		double yaw = Math.toDegrees(Math.atan2(direction.x, direction.z));
		double pitch = Math.toDegrees(Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)));
		poseStack.mulPose(Axis.YP.rotationDegrees((float) yaw));
		poseStack.mulPose(Axis.XP.rotationDegrees((float) pitch));
		poseStack.scale(1.0f, 1.0f, (float) distance);

		VertexConsumer consumer = bufferSource.getBuffer(RenderType.solid());
		emitCylinder(consumer, poseStack, packedLight, style, WIRE_RADIUS, WIRE_SIDES, sprite);

		poseStack.popPose();
	}

	private static void renderDeflectedSpan(Vec3 startVec, Vec3 endVec, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, WireStyle style, TextureAtlasSprite sprite) {
		Vec3 direction = endVec.subtract(startVec);
		double lx = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
		if (lx < MIN_LENGTH) {
			renderStraightSpan(startVec, endVec, poseStack, bufferSource, packedLight, style, sprite);
			return;
		}

		poseStack.pushPose();
		poseStack.translate(startVec.x, startVec.y, startVec.z);

		double yaw = Math.toDegrees(Math.atan2(direction.x, direction.z));
		poseStack.mulPose(Axis.YP.rotationDegrees((float) yaw));

		double ly = direction.y;
		double alpha = WirePhysics.DEFLECTION_COEFFICIENT * (1.0 + WirePhysics.LENGTH_COEFFICIENT * lx);
		double a = lx > 0.0 ? (lx - ly / (alpha * lx)) / 2.0 : 0.0;

		VertexConsumer consumer = bufferSource.getBuffer(RenderType.solid());
		double x = 0.0;
		while (x < lx) {
			double nextX = Math.min(x + DEFLECTION_STEP, lx);
			double startY = alpha * (x * x - 2.0 * a * x);
			double endY = alpha * (nextX * nextX - 2.0 * a * nextX);

			double horizontalDistance = nextX - x;
			double verticalDistance = endY - startY;
			double actualSegmentDistance = Math.sqrt(horizontalDistance * horizontalDistance + verticalDistance * verticalDistance);

			double centerX = (x + nextX) * 0.5;
			double slope = 2.0 * alpha * (centerX - a);
			float pitchC = -((float) Math.toDegrees(Math.atan(slope)));

			poseStack.pushPose();
			poseStack.translate(0, startY, x);
			poseStack.mulPose(Axis.XP.rotationDegrees(pitchC + 90.0f));
			poseStack.scale(1.0f, (float) (actualSegmentDistance / SEGMENT_UNIT), 1.0f);

			emitCylinderSegment(consumer, poseStack, packedLight, style, WIRE_RADIUS, DEFLECTION_SIDES, sprite);

			poseStack.popPose();
			x = nextX;
		}

		poseStack.popPose();
	}

	private static void emitCylinder(VertexConsumer consumer, PoseStack poseStack, int packedLight, WireStyle style, double wireRadius, int segments, TextureAtlasSprite sprite) {
		var pose = poseStack.last();
		float u0 = sprite.getU(0);
		float u1 = sprite.getU(16);
		float v0 = sprite.getV(0);
		float v1 = sprite.getV(16);
		for (int i = 0; i < segments; i++) {
			double angle1 = (2 * Math.PI * i) / segments;
			double angle2 = (2 * Math.PI * (i + 1)) / segments;

			double x1 = Math.cos(angle1) * wireRadius;
			double y1 = Math.sin(angle1) * wireRadius;
			double x2 = Math.cos(angle2) * wireRadius;
			double y2 = Math.sin(angle2) * wireRadius;

			float nx1 = (float) (x1 / wireRadius);
			float ny1 = (float) (y1 / wireRadius);
			float nx2 = (float) (x2 / wireRadius);
			float ny2 = (float) (y2 / wireRadius);

			consumer.vertex(pose.pose(), (float) x1, (float) y1, 0).color(style.red, style.green, style.blue, style.alpha).uv(u0, v0).overlayCoords(0, 10).uv2(packedLight)
					.normal(pose.normal(), nx1, ny1, 0).endVertex();

			consumer.vertex(pose.pose(), (float) x1, (float) y1, 1).color(style.red, style.green, style.blue, style.alpha).uv(u0, v1).overlayCoords(0, 10).uv2(packedLight)
					.normal(pose.normal(), nx1, ny1, 0).endVertex();

			consumer.vertex(pose.pose(), (float) x2, (float) y2, 1).color(style.red, style.green, style.blue, style.alpha).uv(u1, v1).overlayCoords(0, 10).uv2(packedLight)
					.normal(pose.normal(), nx2, ny2, 0).endVertex();

			consumer.vertex(pose.pose(), (float) x2, (float) y2, 0).color(style.red, style.green, style.blue, style.alpha).uv(u1, v0).overlayCoords(0, 10).uv2(packedLight)
					.normal(pose.normal(), nx2, ny2, 0).endVertex();
		}
	}

	private static void emitCylinderSegment(VertexConsumer consumer, PoseStack poseStack, int packedLight, WireStyle style, double wireRadius, int segments, TextureAtlasSprite sprite) {
		var pose = poseStack.last();
		float u0 = sprite.getU(0);
		float u1 = sprite.getU(16);
		float v0 = sprite.getV(0);
		float v1 = sprite.getV(16);
		for (int i = 0; i < segments; i++) {
			double angle1 = (2 * Math.PI * i) / segments;
			double angle2 = (2 * Math.PI * (i + 1)) / segments;

			double x1 = Math.cos(angle1) * wireRadius;
			double z1 = Math.sin(angle1) * wireRadius;
			double x2 = Math.cos(angle2) * wireRadius;
			double z2 = Math.sin(angle2) * wireRadius;

			float nx1 = (float) (x1 / wireRadius);
			float nz1 = (float) (z1 / wireRadius);
			float nx2 = (float) (x2 / wireRadius);
			float nz2 = (float) (z2 / wireRadius);

			consumer.vertex(pose.pose(), (float) x1, 0, (float) z1).color(style.red, style.green, style.blue, style.alpha).uv(u0, v0).overlayCoords(0, 10).uv2(packedLight)
					.normal(pose.normal(), nx1, 0, nz1).endVertex();

			consumer.vertex(pose.pose(), (float) x1, (float) SEGMENT_UNIT, (float) z1).color(style.red, style.green, style.blue, style.alpha).uv(u0, v1).overlayCoords(0, 10).uv2(packedLight)
					.normal(pose.normal(), nx1, 0, nz1).endVertex();

			consumer.vertex(pose.pose(), (float) x2, (float) SEGMENT_UNIT, (float) z2).color(style.red, style.green, style.blue, style.alpha).uv(u1, v1).overlayCoords(0, 10).uv2(packedLight)
					.normal(pose.normal(), nx2, 0, nz2).endVertex();

			consumer.vertex(pose.pose(), (float) x2, 0, (float) z2).color(style.red, style.green, style.blue, style.alpha).uv(u1, v0).overlayCoords(0, 10).uv2(packedLight)
					.normal(pose.normal(), nx2, 0, nz2).endVertex();
		}
	}

	private static TextureAtlasSprite getWireSprite() {
		return Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(WIRE_TEXTURE);
	}

	private static Vec3 resolvePreviewEndpoint(Minecraft minecraft, Vec3 startPoint) {
		if (minecraft.player == null) return null;

		Vec3 eyePosition = minecraft.player.getEyePosition(1.0f);
		Vec3 lookDirection = minecraft.player.getLookAngle();

		if (minecraft.hitResult instanceof BlockHitResult blockHit && minecraft.level != null) {
			BlockPos hitPos = blockHit.getBlockPos();
			BlockEntity blockEntity = minecraft.level.getBlockEntity(hitPos);
			if (isWireAttachable(blockEntity)) {
				String hoveredPart = ObjRaycaster.getHoveredPart(eyePosition, lookDirection, hitPos);
				if (hoveredPart != null) {
					Vec3 fallback = ObjRaycaster.getPartCenter(hitPos, hoveredPart);
					Vec3 anchor = WireAnchorHelper.anchorOrFallback(blockEntity, hoveredPart, fallback);
					if (anchor != null && anchor.distanceToSqr(startPoint) > MIN_LENGTH * MIN_LENGTH) return anchor;
				}
			}
		}

		Vec3 playerPos = minecraft.player.position();
		Vec3 handPos = playerPos.add(0, minecraft.player.getEyeHeight() * 0.5, 0);
		handPos = handPos.add(lookDirection.scale(0.5));
		return handPos;
	}

	private static boolean isWireAttachable(BlockEntity blockEntity) {
		return blockEntity instanceof UtilityPoleBlockEntity || blockEntity instanceof ElectricCabinBlockEntity || blockEntity instanceof PowerBoxBlockEntity
				|| blockEntity instanceof WindTurbineBlockEntity;
	}
}
