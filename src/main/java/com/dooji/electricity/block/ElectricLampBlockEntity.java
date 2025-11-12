package com.dooji.electricity.block;

import com.dooji.electricity.api.power.ElectricityCapabilities;
import com.dooji.electricity.api.power.IElectricPowerConsumer;
import com.dooji.electricity.block.ElectricLampBlock.LampState;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.power.PowerFieldManager;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

public class ElectricLampBlockEntity extends BlockEntity implements IElectricPowerConsumer {
	private static final double REQUIRED_POWER = 0.2;
	private static final double MINIMUM_POWER = 0.15;
	private static final double POWER_FILTER = 0.25;
	private static final double POWER_EPSILON = 1.0E-4;
	private static final double OVERDRIVE_MULTIPLIER = 1.25;
	private static final double REGULATED_POWER_LIMIT = REQUIRED_POWER * 1.35;
	private static final double THERMAL_RESPONSE = 0.18;
	private static final double COOLING_RATE = 0.02;
	private static final double OVERHEAT_THRESHOLD = 1.1;
	private static final double MELT_THRESHOLD = 1.45;
	private static final double DAMAGE_PER_TICK = 0.02;
	private static final double DAMAGE_RECOVERY = 0.015;

	private boolean capabilityPowered = false;
	private boolean burnedOut = false;
	private double deliveredPower = 0.0;
	private double filteredPower = 0.0;
	private double filamentTemperature = 0.0;
	private double overdriveDamage = 0.0;
	private LampState visualState = LampState.OFF;
	private long lastPowerTick = -1L;
	private final LazyOptional<IElectricPowerConsumer> consumerCapability = LazyOptional.of(() -> this);

	public ElectricLampBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.ELECTRIC_LAMP_BLOCK_ENTITY.get(), pos, state);
		if (state.hasProperty(ElectricLampBlock.GLOW_STATE)) {
			this.visualState = state.getValue(ElectricLampBlock.GLOW_STATE);
		}
	}

	@Override
	public double getRequiredPower() {
		return REQUIRED_POWER;
	}

	@Override
	public double getMinimumOperationalPower() {
		return MINIMUM_POWER;
	}

	@Override
	public void onPowerSupplied(double deliveredPower, boolean meetsRequirement) {
		if (burnedOut) return;

		this.deliveredPower = Math.max(deliveredPower, 0.0);
		this.capabilityPowered = this.deliveredPower > 0.0;
		if (level != null) {
			this.lastPowerTick = level.getGameTime();
		}
	}

	public void serverTick() {
		if (level == null || level.isClientSide) return;

		boolean capPowered = isCapabilityPowerActive();
		if (!capPowered && capabilityPowered) {
			capabilityPowered = false;
			deliveredPower = 0.0;
		}

		double fieldPower = PowerFieldManager.getPowerAt(worldPosition);
		double rawPower = burnedOut ? 0.0 : Math.max(fieldPower, capPowered ? deliveredPower : 0.0);
		double regulatedPower = Math.min(rawPower, REGULATED_POWER_LIMIT);

		filteredPower += (regulatedPower - filteredPower) * POWER_FILTER;
		if (filteredPower < POWER_EPSILON) {
			filteredPower = 0.0;
		}

		if (!burnedOut) {
			updateThermalState(regulatedPower);
		}

		LampState nextState = selectState();
		if (burnedOut) {
			nextState = LampState.BURNT;
		}

		if (nextState != visualState || getCurrentLampState() != nextState) {
			visualState = nextState;
			applyLampState(nextState);
		} else if (level.getGameTime() % 40L == 0) {
			applyLampState(nextState);
		}
	}

	private boolean isCapabilityPowerActive() {
		if (!capabilityPowered || level == null) return false;

		long age = level.getGameTime() - lastPowerTick;
		return age <= 2;
	}

	private void updateThermalState(double regulatedPower) {
		double targetTemp = Math.max(0.0, regulatedPower / REQUIRED_POWER);
		if (targetTemp >= filamentTemperature) {
			filamentTemperature += (targetTemp - filamentTemperature) * THERMAL_RESPONSE;
		} else {
			filamentTemperature -= Math.min(filamentTemperature - targetTemp, COOLING_RATE);
		}
		filamentTemperature = Math.max(0.0, filamentTemperature);

		if (filamentTemperature <= OVERHEAT_THRESHOLD) {
			overdriveDamage = Math.max(0.0, overdriveDamage - DAMAGE_RECOVERY);
			return;
		}

		double excess = filamentTemperature - OVERHEAT_THRESHOLD;
		double severity = Mth.clamp(excess / (MELT_THRESHOLD - OVERHEAT_THRESHOLD), 0.0, 1.0);
		overdriveDamage = Math.min(1.0, overdriveDamage + severity * DAMAGE_PER_TICK);
		if (filamentTemperature >= MELT_THRESHOLD || overdriveDamage >= 1.0) {
			burnedOut = true;
			deliveredPower = 0.0;
			filteredPower = 0.0;
			filamentTemperature = 0.0;
		}
	}

	private LampState selectState() {
		if (burnedOut || level == null) return LampState.BURNT;

		double normalizedPower = filteredPower / REQUIRED_POWER;
		double ratio = Math.max(normalizedPower, filamentTemperature);

		if (ratio <= MINIMUM_POWER / REQUIRED_POWER * 0.4) {
			return LampState.OFF;
		}

		if (ratio < 0.65) {
			return shouldFlicker(ratio) ? LampState.OFF : LampState.DIM;
		}

		if (ratio < 1.0) {
			return LampState.WARM;
		}

		if (ratio < OVERDRIVE_MULTIPLIER) {
			return LampState.BRIGHT;
		}

		return LampState.OVERDRIVE;
	}

	private boolean shouldFlicker(double ratio) {
		if (level == null) return false;

		double instability = Mth.clamp(0.45 - ratio * 0.4, 0.1, 0.4);
		long hash = worldPosition.asLong() * 31L + level.getGameTime();
		double noise = (double) (hash & 0xFFFFL) / 0xFFFFL;
		return noise < instability;
	}

	private LampState getCurrentLampState() {
		BlockState state = getBlockState();
		if (state.hasProperty(ElectricLampBlock.GLOW_STATE)) {
			return state.getValue(ElectricLampBlock.GLOW_STATE);
		}

		return LampState.OFF;
	}

	private void applyLampState(LampState desired) {
		if (level == null) return;
		BlockState currentState = getBlockState();
		if (!(currentState.getBlock() instanceof ElectricLampBlock)) return;

		boolean lit = desired.isEmitting() && !burnedOut;
		BlockState updated = currentState.setValue(ElectricLampBlock.LIT, lit).setValue(ElectricLampBlock.GLOW_STATE, desired);
		if (!updated.equals(currentState)) {
			level.setBlock(worldPosition, updated, Block.UPDATE_ALL);
		}
	}

	void updateLampState() {
		applyLampState(visualState);
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);

		tag.putDouble("deliveredPower", deliveredPower);
		tag.putDouble("filteredPower", filteredPower);
		tag.putDouble("filamentTemperature", filamentTemperature);
		tag.putDouble("overdriveDamage", overdriveDamage);
		tag.putBoolean("capabilityPowered", capabilityPowered);
		tag.putBoolean("burnedOut", burnedOut);
		tag.putLong("lastPowerTick", lastPowerTick);
		tag.putString("glowState", visualState.getSerializedName());
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		deliveredPower = tag.getDouble("deliveredPower");
		filteredPower = tag.getDouble("filteredPower");
		filamentTemperature = tag.getDouble("filamentTemperature");
		overdriveDamage = tag.getDouble("overdriveDamage");
		capabilityPowered = tag.getBoolean("capabilityPowered");
		burnedOut = tag.getBoolean("burnedOut");
		lastPowerTick = tag.getLong("lastPowerTick");

		if (tag.contains("glowState")) {
			visualState = LampState.fromName(tag.getString("glowState"));
		} else {
			visualState = LampState.OFF;
		}
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if (level != null) {
			visualState = getCurrentLampState();
		}

		updateLampState();
	}

	@Nonnull @Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
		if (cap == ElectricityCapabilities.ELECTRIC_CONSUMER) return consumerCapability.cast();

		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		consumerCapability.invalidate();
	}
}
