package com.dooji.electricity.client.render.obj;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class ObjRenderer {
	public static void render(ObjModel model, PoseStack poseStack, MultiBufferSource bufferSource, ResourceLocation texturePrefix, int packedLight) {
		if (model == null) return;

		for (ObjModel.ObjGroup group : model.groups.values()) {
			renderGroup(group, poseStack, bufferSource, model, texturePrefix, packedLight);
		}
	}

	public static void renderGroup(ObjModel.ObjGroup group, PoseStack poseStack, MultiBufferSource bufferSource, ObjModel model, ResourceLocation texturePrefix, int packedLight) {
		ObjModel.ObjMaterial material = model.materials.get(group.materialName);

		VertexConsumer vertexBuffer;
		if (material != null && material.texture != null) {
			vertexBuffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(material.texture));
		} else {
			vertexBuffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texturePrefix));
		}

		renderMeshWithColor(group, vertexBuffer, poseStack, packedLight);
	}

	private static void renderMeshWithColor(ObjModel.ObjGroup group, VertexConsumer consumer, PoseStack poseStack, int packedLight) {
		var vertices = group.vertices;
		var normals = group.normals;
		var uvs = group.texCoords;

		var pose = poseStack.last();
		var matrix = pose.pose();
		var normalMatrix = pose.normal();

		for (int i = 0; i < vertices.size(); i++) {
			var pos = vertices.get(i);
			var normal = normals.get(i);
			float u = uvs.get(i * 2);
			float v = uvs.get(i * 2 + 1);

			var transformedNormal = new Vector3f(normal);
			normalMatrix.transform(transformedNormal);
			transformedNormal.normalize().mul(-1.0f);

			consumer.vertex(matrix, pos.x, pos.y, pos.z).color(255, 255, 255, 255).uv(u, v).overlayCoords(0, 10).uv2(packedLight)
					.normal(transformedNormal.x, transformedNormal.y, transformedNormal.z).endVertex();
		}
	}
}
