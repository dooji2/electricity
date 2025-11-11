package com.dooji.electricity.main.network.payloads;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record CreateWireFromInsulatorsPayload(int startInsulatorId, int endInsulatorId, BlockPos startBlockPos, BlockPos endBlockPos, String startBlockType, String endBlockType, String startPowerType,
		String endPowerType) {
	public static void write(CreateWireFromInsulatorsPayload message, FriendlyByteBuf buf) {
		buf.writeInt(message.startInsulatorId);
		buf.writeInt(message.endInsulatorId);
		buf.writeBlockPos(message.startBlockPos);
		buf.writeBlockPos(message.endBlockPos);
		buf.writeUtf(message.startBlockType);
		buf.writeUtf(message.endBlockType);
		buf.writeUtf(message.startPowerType);
		buf.writeUtf(message.endPowerType);
	}

	public static CreateWireFromInsulatorsPayload read(FriendlyByteBuf buf) {
		int startInsulatorId = buf.readInt();
		int endInsulatorId = buf.readInt();

		BlockPos startBlockPos = buf.readBlockPos();
		BlockPos endBlockPos = buf.readBlockPos();
		String startBlockType = buf.readUtf();
		String endBlockType = buf.readUtf();
		String startPowerType = buf.readUtf();
		String endPowerType = buf.readUtf();

		return new CreateWireFromInsulatorsPayload(startInsulatorId, endInsulatorId, startBlockPos, endBlockPos, startBlockType, endBlockType, startPowerType, endPowerType);
	}
}
