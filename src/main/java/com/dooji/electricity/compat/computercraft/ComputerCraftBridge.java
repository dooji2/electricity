package com.dooji.electricity.compat.computercraft;

import net.minecraftforge.fml.ModList;

/**
 * Guarded entry point for the ComputerCraft integration.
 *
 * Same arrangement as the Mekanism side: this class names no CC type, so it stays
 * loadable without CC:Tweaked installed, and {@link CCTweakedPeripherals} is only
 * reached once {@link #isLoaded()} has confirmed the mod is there.
 */
public final class ComputerCraftBridge {
	private static Boolean loaded;

	private ComputerCraftBridge() {
	}

	public static boolean isLoaded() {
		if (loaded == null) {
			loaded = ModList.get().isLoaded("computercraft");
		}

		return loaded;
	}

	/**
	 * Registers the turbine as a ComputerCraft peripheral. Call once from common
	 * setup; does nothing when CC:Tweaked is absent.
	 *
	 * Registering a provider rather than exposing a capability is what makes wired
	 * modems work: a modem placed against the turbine finds the peripheral through
	 * the same lookup, so the turbine appears on a network cable without any extra
	 * handling on our side.
	 */
	public static void register() {
		if (!isLoaded()) return;

		CCTweakedPeripherals.register();
	}
}
