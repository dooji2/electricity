package com.dooji.electricity.api.power;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

public final class ElectricityCapabilities {
	public static final Capability<IElectricPowerConsumer> ELECTRIC_CONSUMER = CapabilityManager.get(new CapabilityToken<>() {
	});

	private ElectricityCapabilities() {
	}

	public static void register(RegisterCapabilitiesEvent event) {
		event.register(IElectricPowerConsumer.class);
	}
}
