package com.dooji.electricity.client;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ElectricityClientConfig {
	private static final ForgeConfigSpec SPEC_INTERNAL;
	private static final ForgeConfigSpec.BooleanValue PREVIEW_SNAPPING;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		builder.comment("Electricity Client Configuration").push("client");
		PREVIEW_SNAPPING = builder.comment("If true, wire previews snap to the exact insulator positions.").define("previewSnapping", false);
		builder.pop();
		SPEC_INTERNAL = builder.build();
	}

	private ElectricityClientConfig() {
	}

	public static ForgeConfigSpec spec() {
		return SPEC_INTERNAL;
	}

	public static boolean previewSnappingEnabled() {
		return PREVIEW_SNAPPING.get();
	}
}
