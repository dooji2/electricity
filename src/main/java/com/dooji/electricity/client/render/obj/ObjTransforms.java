package com.dooji.electricity.client.render.obj;

import com.dooji.electricity.block.UtilityPoleBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// OBJ pipeline code will be migrated to Renderix
@OnlyIn(Dist.CLIENT)
public final class ObjTransforms {
	private ObjTransforms() {
	}

	public record Transform(float offsetX, float offsetY, float offsetZ, float yaw, float pitch) {
	}

	public static Transform resolve(BlockEntity blockEntity) {
		if (blockEntity instanceof UtilityPoleBlockEntity entity) {
			return new Transform(entity.getOffsetX(), entity.getOffsetY(), entity.getOffsetZ(), (float) Math.toDegrees(entity.getYaw()), (float) Math.toDegrees(entity.getPitch()));
		}

		return new Transform(0f, 0f, 0f, 0f, 0f);
	}
}
