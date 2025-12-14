package com.dooji.electricity.client.render.obj;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// OBJ pipeline code will be migrated to Renderix
@OnlyIn(Dist.CLIENT)
public record ObjRenderContext(ObjModel model, ResourceLocation texture, int packedLight) {
}
