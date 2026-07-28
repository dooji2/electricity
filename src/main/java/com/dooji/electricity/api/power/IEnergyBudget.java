package com.dooji.electricity.api.power;

/**
 * A per-tick pool of energy (in Joules) that a generator offers to the energy
 * systems of other mods.
 *
 * The Electricity wire network does not conserve energy: it reads a generator's
 * output every tick and distributes it without ever debiting the generator. That
 * works as long as the wires are the only consumer, but the moment a foreign
 * cable can pull from the same generator the two would each get the full amount.
 * A budget closes that hole. Generation fills the budget once per tick, foreign
 * cables claim from it, and the wire network is handed whatever is left, so a
 * Joule is only ever spent once.
 *
 * Nothing carries over between ticks, so there is no stored energy to persist.
 */
public interface IEnergyBudget {
	/**
	 * @return the Joules still unclaimed this tick.
	 */
	double getAvailableJoules();

	/**
	 * @return the largest budget this source can offer within a single tick. This
	 *         is a throughput cap on the foreign-cable side only; it does not
	 *         limit what the wire network receives.
	 */
	double getMaxJoulesPerTick();

	/**
	 * Claims up to {@code joules} from this tick's budget.
	 *
	 * @param joules
	 * the amount requested
	 * @param simulate
	 * when true, report what would be claimed without actually claiming it
	 * @return the amount actually claimed, never more than {@code joules} nor more
	 *         than {@link #getAvailableJoules()}
	 */
	double claimJoules(double joules, boolean simulate);
}
