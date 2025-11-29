package com.dooji.electricity.main;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ElectricityServerConfig {
	private static final ForgeConfigSpec SERVER_SPEC_INTERNAL;
	private static final ForgeConfigSpec.IntValue POWER_BOX_RADIUS;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		builder.push("power");
		POWER_BOX_RADIUS = builder.comment("Radius (in blocks) of power field around a Power Box").defineInRange("powerBoxRadius", 5, 1, 16);
		builder.pop();
		SERVER_SPEC_INTERNAL = builder.build();
	}

	private ElectricityServerConfig() {
	}

	public static ForgeConfigSpec spec() {
		return SERVER_SPEC_INTERNAL;
	}

	public static int powerBoxRadius() {
		return POWER_BOX_RADIUS.get();
	}
}
