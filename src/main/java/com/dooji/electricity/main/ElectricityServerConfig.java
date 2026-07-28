package com.dooji.electricity.main;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ElectricityServerConfig {
	private static final ForgeConfigSpec SERVER_SPEC_INTERNAL;
	private static final ForgeConfigSpec.IntValue POWER_BOX_RADIUS;
	private static final ForgeConfigSpec.BooleanValue EXTERNAL_ENERGY_ENABLED;
	private static final ForgeConfigSpec.DoubleValue TURBINE_MAX_JOULES_PER_TICK;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		builder.push("power");
		POWER_BOX_RADIUS = builder.comment("Radius (in blocks) of power field around a Power Box").defineInRange("powerBoxRadius", 5, 1, 16);
		builder.pop();
		builder.push("energy");
		EXTERNAL_ENERGY_ENABLED = builder.comment("Let generators feed the energy systems of other mods (Mekanism Joules and Forge Energy) directly").define("externalEnergyEnabled", true);
		TURBINE_MAX_JOULES_PER_TICK = builder.comment(
				"Cap on the Joules per tick a Wind Turbine will hand to another mod's cables.",
				"At 125 J per kW an uncapped turbine peaks near 9844 J/t, about 20x a Mekanism Wind Generator,",
				"so this is capped to Mekanism's own maximum by default. Whatever is not taken stays on the wire network."
		).defineInRange("turbineMaxJoulesPerTick", 480.0, 0.0, 1.0e9);
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

	public static boolean externalEnergyEnabled() {
		return EXTERNAL_ENERGY_ENABLED.get();
	}

	public static double turbineMaxJoulesPerTick() {
		return TURBINE_MAX_JOULES_PER_TICK.get();
	}
}
