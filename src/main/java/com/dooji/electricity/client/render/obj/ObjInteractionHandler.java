package com.dooji.electricity.client.render.obj;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// OBJ pipeline code will be migrated to Renderix
@OnlyIn(Dist.CLIENT)
public interface ObjInteractionHandler {
	void onPartInteract(String partName, BlockPos blockPos, Level level);
}
