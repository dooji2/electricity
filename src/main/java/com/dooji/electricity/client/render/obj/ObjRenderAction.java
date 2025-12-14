package com.dooji.electricity.client.render.obj;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// OBJ pipeline code will be migrated to Renderix
@OnlyIn(Dist.CLIENT) @FunctionalInterface
public interface ObjRenderAction {
	void render(ObjRenderContext context, PoseStack poseStack, MultiBufferSource bufferSource);
}
