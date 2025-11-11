package com.dooji.electricity.main.network.payloads;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record PowerUpdatePayload(BlockPos blockPos, double power) {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("electricity", "power_update");

	public PowerUpdatePayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), buf.readDouble());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeBlockPos(blockPos);
		buf.writeDouble(power);
	}

	public static PowerUpdatePayload read(FriendlyByteBuf buf) {
		return new PowerUpdatePayload(buf);
	}
}
