package com.dooji.electricity.compat.computercraft;

import com.dooji.electricity.api.power.TurbineTelemetry;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import dan200.computercraft.api.ForgeComputerCraftAPI;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Every reference to a CC:Tweaked class in this mod lives here, reached only from
 * {@link ComputerCraftBridge} after it has confirmed the mod is loaded.
 */
public final class CCTweakedPeripherals {
	private CCTweakedPeripherals() {
	}

	public static void register() {
		ForgeComputerCraftAPI.registerPeripheralProvider((level, pos, side) -> {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof WindTurbineBlockEntity turbine) {
				return LazyOptional.of(() -> new WindTurbinePeripheral(turbine));
			}

			return LazyOptional.empty();
		});
	}

	/**
	 * The turbine as seen from Lua.
	 *
	 * Two families of method live side by side. The handful of annotated ones carry
	 * the names Mekanism uses on its own generators, so a program written against a
	 * Mekanism generator reads this turbine unchanged. Everything else is generated
	 * from the telemetry tag list through {@link IDynamicPeripheral}, which is why
	 * adding a tag costs nothing here.
	 *
	 * Reads come off the computer thread, never the server thread. That is safe
	 * because every value is served from the immutable snapshot the turbine
	 * publishes once per tick, so a call can never observe a half-updated turbine
	 * and never has to wait for a tick boundary to answer.
	 */
	public static final class WindTurbinePeripheral implements IDynamicPeripheral {
		/** Tag order is fixed by the telemetry class, so index-to-tag stays stable. */
		private static final String[] TAGS = TurbineTelemetry.kinds().keySet().toArray(String[]::new);
		private static final String[] METHOD_NAMES = buildMethodNames();

		private final WindTurbineBlockEntity turbine;

		private WindTurbinePeripheral(WindTurbineBlockEntity turbine) {
			this.turbine = turbine;
		}

		@Override
		public String getType() {
			return "electricity_wind_turbine";
		}

		@Override
		public boolean equals(@Nullable IPeripheral other) {
			return other instanceof WindTurbinePeripheral peripheral && peripheral.turbine == turbine;
		}

		@Override
		public Object getTarget() {
			return turbine;
		}

		// ---- names shared with Mekanism's generators ----

		/**
		 * Joules produced in the last tick. Mekanism's own generators answer the same
		 * question under the same name, in the same unit.
		 */
		@LuaFunction
		public final double getProductionRate() {
			return turbine.getGrossJoulesPerTick();
		}

		/** Ceiling on what this generator will hand out per tick, in Joules. */
		@LuaFunction
		public final double getMaxOutput() {
			return turbine.getMaxJoulesPerTick();
		}

		/** Joules still unclaimed in this tick's budget. */
		@LuaFunction
		public final double getEnergy() {
			return turbine.getAvailableJoules();
		}

		@LuaFunction
		public final double getMaxEnergy() {
			return turbine.getMaxJoulesPerTick();
		}

		/** Always zero: the turbine is a source and accepts nothing. */
		@LuaFunction
		public final double getEnergyNeeded() {
			return 0.0;
		}

		@LuaFunction
		public final double getEnergyFilledPercentage() {
			double max = turbine.getMaxJoulesPerTick();
			return max <= 0.0 ? 0.0 : turbine.getAvailableJoules() / max;
		}

		/**
		 * Present for parity with Mekanism's Wind Generator, which can be barred from
		 * generating in configured dimensions. This mod has no such list, so the
		 * answer is always false.
		 */
		@LuaFunction
		public final boolean isBlacklistedDimension() {
			return false;
		}

		// ---- telemetry ----

		/** Every signal at once, as a table keyed by tag. One tick-consistent snapshot. */
		@LuaFunction
		public final Map<String, Object> getTelemetry() {
			return turbine.getTelemetry().values();
		}

		/**
		 * Which tags are instrument readings and which are invented, as a table of tag
		 * to "MEASURED", "DERIVED" or "SIMULATED". Worth checking before treating a
		 * number as ground truth: the temperatures and pressures track load and
		 * ambient realistically but are not modelled by the mod.
		 */
		@LuaFunction
		public final Map<String, Object> getTelemetryKinds() {
			Map<String, Object> kinds = new LinkedHashMap<>();
			TurbineTelemetry.kinds().forEach((tag, kind) -> kinds.put(tag, kind.name()));
			return kinds;
		}

		// ---- one generated getter per tag ----

		@Override
		public String[] getMethodNames() {
			return METHOD_NAMES;
		}

		@Override
		public MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments arguments) {
			if (method < 0 || method >= TAGS.length) return MethodResult.of();

			return MethodResult.of(turbine.getTelemetry().get(TAGS[method]));
		}

		private static String[] buildMethodNames() {
			String[] names = new String[TAGS.length];
			for (int i = 0; i < TAGS.length; i++) {
				names[i] = "get" + Character.toUpperCase(TAGS[i].charAt(0)) + TAGS[i].substring(1);
			}

			return names;
		}
	}
}
