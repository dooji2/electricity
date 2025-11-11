package com.dooji.electricity.block;

import com.dooji.electricity.api.power.ElectricityCapabilities;
import com.dooji.electricity.api.power.IElectricPowerConsumer;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.power.PowerFieldManager;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

public class ElectricLampBlockEntity extends BlockEntity implements IElectricPowerConsumer {
	private static final double REQUIRED_POWER = 0.2;
	private static final double MINIMUM_POWER = 0.15;

	private boolean electricPowered = false;
	private boolean capabilityPowered = false;
	private double deliveredPower = 0.0;
	private long lastPowerTick = -1L;
	private final LazyOptional<IElectricPowerConsumer> consumerCapability = LazyOptional.of(() -> this);

	public ElectricLampBlockEntity(BlockPos pos, BlockState state) {
		super(Electricity.ELECTRIC_LAMP_BLOCK_ENTITY.get(), pos, state);
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
		this.deliveredPower = deliveredPower;
		this.capabilityPowered = meetsRequirement;
		this.electricPowered = meetsRequirement;
		if (level != null) {
			this.lastPowerTick = level.getGameTime();
		}

		updateLampState();
	}

	public void serverTick() {
		if (level == null || level.isClientSide) return;

		double fieldPower = PowerFieldManager.getPowerAt(worldPosition);
		boolean areaPowered = fieldPower >= getMinimumOperationalPower();
		boolean capPowered = isCapabilityPowerActive();

		if (areaPowered && fieldPower > deliveredPower) {
			deliveredPower = fieldPower;
		} else if (!areaPowered && !capPowered) {
			deliveredPower = 0.0;
		}

		boolean newElectricState = areaPowered || capPowered;
		if (electricPowered != newElectricState) {
			electricPowered = newElectricState;
		}

		updateLampState();
	}

	private boolean isCapabilityPowerActive() {
		if (!capabilityPowered || level == null) return false;

		long age = level.getGameTime() - lastPowerTick;
		return age <= 2;
	}

	void updateLampState() {
		if (level == null) return;

		boolean shouldBeLit = electricPowered;
		BlockState currentState = getBlockState();

		if (!(currentState.getBlock() instanceof ElectricLampBlock)) return;

		boolean isLit = currentState.getValue(ElectricLampBlock.LIT);
		if (isLit != shouldBeLit) {
			BlockState updated = currentState.setValue(ElectricLampBlock.LIT, shouldBeLit);
			level.setBlock(worldPosition, updated, Block.UPDATE_ALL);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("electricPowered", electricPowered);
		tag.putDouble("deliveredPower", deliveredPower);
		tag.putBoolean("capabilityPowered", capabilityPowered);
		tag.putLong("lastPowerTick", lastPowerTick);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		electricPowered = tag.getBoolean("electricPowered");
		deliveredPower = tag.getDouble("deliveredPower");
		capabilityPowered = tag.getBoolean("capabilityPowered");
		lastPowerTick = tag.getLong("lastPowerTick");
	}

	@Override
	public void onLoad() {
		super.onLoad();
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
