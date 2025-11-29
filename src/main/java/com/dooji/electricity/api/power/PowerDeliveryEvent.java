package com.dooji.electricity.api.power;

import net.minecraft.util.Mth;

public record PowerDeliveryEvent(double surgeSeverity, int surgeDuration, boolean disconnectActive, double brownoutFactor) {
	private static final PowerDeliveryEvent NONE = new PowerDeliveryEvent(0.0, 0, false, 1.0);

	public PowerDeliveryEvent {
		surgeSeverity = Mth.clamp(surgeSeverity, 0.0, 1.0);
		surgeDuration = Math.max(0, surgeDuration);
		brownoutFactor = Mth.clamp(brownoutFactor, 0.0, 1.0);
	}

	public static PowerDeliveryEvent none() {
		return NONE;
	}
}
