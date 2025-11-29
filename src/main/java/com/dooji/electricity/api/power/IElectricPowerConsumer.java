package com.dooji.electricity.api.power;

/**
 * An interface that allows blocks/entities to request power from the
 * Electricity network, all values are in kW.
 */
public interface IElectricPowerConsumer {
	/**
	 * @return the amount of power (in kW) the device would like to receive while
	 *         operating at full efficiency.
	 */
	double getRequiredPower();

	/**
	 * @return the minimum amount of power (in kW) required for the device to
	 *         operate safely. Defaults to the full requirement but can be
	 *         overridden to model partial operation or flickering states.
	 */
	default double getMinimumOperationalPower() {
		return getRequiredPower();
	}

	/**
	 * Called once per tick for every consumer that sits inside an active power
	 * field.
	 *
	 * @param deliveredPower
	 * amount of power actually provided (in kW)
	 * @param meetsRequirement
	 * true when {@code deliveredPower >= getMinimumOperationalPower()}
	 * @param event
	 * contextual power quality information (surges, brownouts, disconnects)
	 */
	void onPowerSupplied(double deliveredPower, boolean meetsRequirement, PowerDeliveryEvent event);
}
