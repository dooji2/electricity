package com.dooji.electricity.main.network.payloads;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record UpdateUtilityPoleConfigPayload(BlockPos blockPos, float offsetX, float offsetY, float offsetZ, float yaw, float pitch) {
	public static void write(UpdateUtilityPoleConfigPayload msg, FriendlyByteBuf buffer) {
		buffer.writeBlockPos(msg.blockPos);
		buffer.writeFloat(msg.offsetX);
		buffer.writeFloat(msg.offsetY);
		buffer.writeFloat(msg.offsetZ);
		buffer.writeFloat(msg.yaw);
		buffer.writeFloat(msg.pitch);
	}

	public static UpdateUtilityPoleConfigPayload read(FriendlyByteBuf buffer) {
		return new UpdateUtilityPoleConfigPayload(buffer.readBlockPos(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
	}
}
