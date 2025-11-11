package com.dooji.electricity.client.render.obj;

import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT) @FunctionalInterface
public interface FacingRotationFunction {
	float rotation(Direction facing);
}
