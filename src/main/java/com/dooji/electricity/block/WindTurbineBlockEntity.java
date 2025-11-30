package com.dooji.electricity.block;

import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.weather.GlobalWeatherManager;
import com.dooji.electricity.main.weather.WeatherSnapshot;
import com.dooji.electricity.wire.InsulatorIdRegistry;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

public class WindTurbineBlockEntity extends BlockEntity {
	private Vec3[] wirePositions = new Vec3[1];
	private int[] insulatorIds = new int[1];

	private float rotationSpeed1 = 0.0f;
	private float rotationSpeed2 = 0.0f;
	private float rotation1 = 0.0f;
	private float rotation2 = 0.0f;

	private double generatedPower = 0.0;
	private double currentPower = 0.0;
	private static final Vec3 DEFAULT_INSULATOR_OFFSET = new Vec3(0.0, 9.5, -0.5);

	private float lastEffectiveWindSpeed = 0.0f;
	private float lastAlignedWindSpeed = 0.0f;
	private float windDirection = 0.0f;
	private double turbulence = 0.0;
	private static final float CUTOFF_RESET_SPEED = 20.0f;
	private boolean cutOutActive = false;
	private boolean yawInitialized = false;
	private float yaw = 0.0f;
	private float lastSentYaw = Float.NaN;
	private long lastSyncTick = 0L;
	private static final float CUT_IN_SPEED = 3.0f;
	private static final float RATED_SPEED = 12.0f;
	private static final float CUTOFF_SPEED = 22.0f;
	private static final float YAW_STEP = 0.25f;
	private static final float YAW_DEADBAND = 7.5f;

	public WindTurbineBlockEntity(BlockPos pos, BlockState state) {
		super(getBlockEntityType(), pos, state);
		initializeWirePositions();
		generateInsulatorIds();
	}

	private static BlockEntityType<WindTurbineBlockEntity> getBlockEntityType() {
		return Electricity.WIND_TURBINE_BLOCK_ENTITY.get();
	}

	private void initializeWirePositions() {
		for (int i = 0; i < wirePositions.length; i++) {
			wirePositions[i] = calculateOrientedInsulatorCenter(i);
		}
	}

	private void generateInsulatorIds() {
		for (int i = 0; i < insulatorIds.length; i++) {
			insulatorIds[i] = InsulatorIdRegistry.claimId();
		}
	}

	public static void removeInsulatorIds(int[] ids) {
		InsulatorIdRegistry.releaseIds(ids);
	}

	public Vec3 getWirePosition(int index) {
		if (index >= 0 && index < wirePositions.length) return wirePositions[index];

		return null;
	}

	public void setWirePosition(int index, Vec3 position) {
		if (index >= 0 && index < wirePositions.length) {
			wirePositions[index] = position;
		}
	}

	public int getInsulatorId(int index) {
		if (index >= 0 && index < insulatorIds.length) return insulatorIds[index];

		return -1;
	}

	public int[] getInsulatorIds() {
		return insulatorIds.clone();
	}

	public float getRotation1() {
		return rotation1;
	}

	public float getRotation2() {
		return rotation2;
	}

	public float getWindSpeed() {
		return lastEffectiveWindSpeed;
	}

	public float getWindDirection() {
		return windDirection;
	}

	public double getGeneratedPower() {
		updateGeneratedPower();
		return generatedPower;
	}

	public boolean isSurging() {
		return turbulence >= 0.35 && lastEffectiveWindSpeed < CUTOFF_SPEED && !cutOutActive;
	}

	public double getCurrentPower() {
		return currentPower;
	}

	public void setCurrentPower(double power) {
		this.currentPower = power;
	}

	private void updateGeneratedPower() {
		float effectiveWindSpeed = Math.max(0.0f, lastAlignedWindSpeed);
		if (cutOutActive || effectiveWindSpeed < CUT_IN_SPEED || effectiveWindSpeed >= CUTOFF_SPEED) {
			generatedPower = 0.0;
			return;
		}

		float capped = Math.min(effectiveWindSpeed, RATED_SPEED);
		double normalized = Math.min(1.0, capped / 16.0f);
		double energyFactor = normalized * normalized;
		generatedPower = 140.0 * energyFactor;
	}

	public Vec3 calculateOrientedInsulatorCenter(int index) {
		if (index != 0) return null;

		String groupName = "insulator_Plastic";
		var boundingBox = ObjBoundingBoxRegistry.getBoundingBox(Electricity.WIND_TURBINE_BLOCK.get(), groupName);
		Vec3 localCenter;
		if (boundingBox != null) {
			Vector3f center = boundingBox.center;
			localCenter = new Vec3(center.x(), center.y(), center.z());
		} else {
			localCenter = DEFAULT_INSULATOR_OFFSET;
		}

		Direction facing = getBlockState().getValue(WindTurbineBlock.FACING);
		Vec3 rotatedCenter = rotateVector(localCenter, facing);
		return Vec3.atLowerCornerOf(getBlockPos()).add(0.5, 0, 0.5).add(rotatedCenter);
	}

	private Vec3 rotateVector(Vec3 vector, Direction facing) {
		float facingRotation = switch (facing) {
			case EAST -> 90.0f;
			case SOUTH -> 0.0f;
			case WEST -> 270.0f;
			default -> 180.0f;
		};

		double radians = Math.toRadians(facingRotation);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);

		double newX = vector.x * cos - vector.z * sin;
		double newZ = vector.x * sin + vector.z * cos;

		return new Vec3(newX, vector.y, newZ);
	}

	public void tick() {
		updateWirePositions();
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			InsulatorLookup.register(this);
			WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
		});

		if (level == null) return;

		if (level.isClientSide) {
			clientVisualTick();
			return;
		}

		WeatherSnapshot weather = GlobalWeatherManager.get((net.minecraft.server.level.ServerLevel) level).sample(worldPosition);
		float sustained = (float) weather.windSpeed();
		float gust = (float) weather.gustSpeed();
		float blend = Mth.clamp((float) weather.turbulence(), 0.0f, 1.0f);

		lastEffectiveWindSpeed = Mth.lerp(blend, sustained, gust);
		windDirection = weather.direction();
		turbulence = weather.turbulence();

		updateYaw();
		float alignment = alignmentFactor();
		lastAlignedWindSpeed = lastEffectiveWindSpeed * alignment;

		if (lastEffectiveWindSpeed >= CUTOFF_SPEED) {
			cutOutActive = true;
		} else if (cutOutActive && lastEffectiveWindSpeed <= CUTOFF_RESET_SPEED) {
			cutOutActive = false;
		}

		updateRotorSpeeds(lastAlignedWindSpeed, turbulence, cutOutActive);
		updateGeneratedPower();

		maybeSync();
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);

		if (tag.contains("wirePositions")) {
			ListTag positionsList = tag.getList("wirePositions", 10);
			for (int i = 0; i < Math.min(positionsList.size(), wirePositions.length); i++) {
				CompoundTag posTag = positionsList.getCompound(i);
				wirePositions[i] = new Vec3(posTag.getDouble("x"), posTag.getDouble("y"), posTag.getDouble("z"));
			}
		}

		if (tag.contains("insulatorIds")) {
			ListTag insulatorIdsList = tag.getList("insulatorIds", 3);
			for (int i = 0; i < Math.min(insulatorIdsList.size(), insulatorIds.length); i++) {
				insulatorIds[i] = insulatorIdsList.getInt(i);
				InsulatorIdRegistry.registerExistingId(insulatorIds[i]);
			}
		}

		rotationSpeed1 = tag.getFloat("rotationSpeed1");
		rotationSpeed2 = tag.getFloat("rotationSpeed2");
		generatedPower = tag.getDouble("generatedPower");
		currentPower = tag.getDouble("currentPower");
		lastEffectiveWindSpeed = tag.getFloat("lastEffectiveWindSpeed");
		lastAlignedWindSpeed = tag.contains("lastAlignedWindSpeed") ? tag.getFloat("lastAlignedWindSpeed") : lastEffectiveWindSpeed;
		cutOutActive = tag.contains("cutOutActive") && tag.getBoolean("cutOutActive");
		yawInitialized = tag.contains("yaw");
		yaw = yawInitialized ? tag.getFloat("yaw") : yaw;

		windDirection = tag.getFloat("windDirection");
		turbulence = tag.contains("turbulence") ? tag.getDouble("turbulence") : 0.0;

		updateWirePositions();
	}

	private void updateWirePositions() {
		for (int i = 0; i < wirePositions.length; i++) {
			Vec3 calculatedPos = calculateOrientedInsulatorCenter(i);
			if (calculatedPos != null) {
				wirePositions[i] = calculatedPos;
			}
		}
	}

	@Override
	protected void saveAdditional(@Nonnull CompoundTag tag) {
		super.saveAdditional(tag);
		ensureYawInitialized();

		ListTag positionsList = new ListTag();
		for (Vec3 pos : wirePositions) {
			CompoundTag posTag = new CompoundTag();
			posTag.putDouble("x", pos.x);
			posTag.putDouble("y", pos.y);
			posTag.putDouble("z", pos.z);
			positionsList.add(posTag);
		}

		tag.put("wirePositions", positionsList);

		ListTag insulatorIdsList = new ListTag();
		for (int id : insulatorIds) {
			insulatorIdsList.add(IntTag.valueOf(id));
		}

		tag.put("insulatorIds", insulatorIdsList);

		tag.putFloat("rotationSpeed1", rotationSpeed1);
		tag.putFloat("rotationSpeed2", rotationSpeed2);
		tag.putDouble("generatedPower", generatedPower);
		tag.putDouble("currentPower", currentPower);
		tag.putFloat("lastEffectiveWindSpeed", lastEffectiveWindSpeed);
		tag.putFloat("lastAlignedWindSpeed", lastAlignedWindSpeed);
		tag.putBoolean("cutOutActive", cutOutActive);
		tag.putFloat("yaw", yaw);
		tag.putFloat("windDirection", windDirection);
		tag.putDouble("turbulence", turbulence);
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		load(tag);
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = new CompoundTag();
		saveAdditional(tag);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
				TrackedBlockEntities.track(this);
				InsulatorLookup.register(this);
				WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
			});
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		InsulatorIdRegistry.releaseIds(this.getInsulatorIds());
		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
				TrackedBlockEntities.untrack(this);
				InsulatorLookup.unregister(this.getInsulatorIds());
				WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
			});
		}
	}

	private void syncStateToClients() {
		if (level == null || level.isClientSide) return;
		setChanged();
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	private void updateRotorSpeeds(float effectiveWindSpeed, double turbulence, boolean cutOut) {
		float maxRotationSpeed = Math.min(12.0f, 6.0f + effectiveWindSpeed * 0.5f);
		float rotationScale = Mth.lerp((float) Mth.clamp(turbulence, 0.0, 1.0), 0.45f, 0.8f);
		float targetRotationSpeed = cutOut ? 0.0f : Math.min(maxRotationSpeed, effectiveWindSpeed * rotationScale);

		float rotationAcceleration = cutOut ? 0.12f : 0.05f;
		float rotationDiff1 = targetRotationSpeed - rotationSpeed1;
		float rotationDiff2 = targetRotationSpeed - rotationSpeed2;

		if (Math.abs(rotationDiff1) > 0.001f) {
			rotationSpeed1 += rotationDiff1 * rotationAcceleration;
		}

		if (Math.abs(rotationDiff2) > 0.001f) {
			rotationSpeed2 += rotationDiff2 * rotationAcceleration;
		}

		if (cutOut) {
			if (Math.abs(rotationSpeed1) < 0.01f) rotationSpeed1 = 0.0f;
			if (Math.abs(rotationSpeed2) < 0.01f) rotationSpeed2 = 0.0f;
		}
	}

	private void clientVisualTick() {
		float effectiveSpeed = lastAlignedWindSpeed;
		float currentTime = level.getGameTime() + level.getGameTime() * 0.05f;
		advanceRotations(currentTime, effectiveSpeed);
	}

	private void advanceRotations(float currentTime, float effectiveSpeed) {
		float variation1 = 1.0f + (float) Math.sin(currentTime * 0.05) * 0.1f;
		float variation2 = 1.0f + (float) Math.cos(currentTime * 0.07) * 0.1f;

		float appliedSpeed1 = rotationSpeed1;
		float appliedSpeed2 = rotationSpeed2;
		if (!cutOutActive) {
			if (rotationSpeed1 == 0.0f && effectiveSpeed > 0.0f) {
				appliedSpeed1 = Math.min(12.0f, effectiveSpeed * 0.6f);
			}

			if (rotationSpeed2 == 0.0f && effectiveSpeed > 0.0f) {
				appliedSpeed2 = appliedSpeed1;
			}
		}

		rotation1 = (rotation1 + appliedSpeed1 * variation1) % 360.0f;
		rotation2 = (rotation2 + appliedSpeed2 * variation2) % 360.0f;
		if (rotation1 < 0) rotation1 += 360.0f;
		if (rotation2 < 0) rotation2 += 360.0f;
	}

	private void updateYaw() {
		if (!yawInitialized) {
			Direction facing = getBlockState().getValue(WindTurbineBlock.FACING);
			yaw = baseFacingYaw(facing);
			yawInitialized = true;
		}

		float target = windDirection;
		float delta = Mth.wrapDegrees(target - yaw);
		if (Math.abs(delta) <= YAW_DEADBAND) return;
		float step = Mth.clamp(delta, -YAW_STEP, YAW_STEP);
		yaw = Mth.wrapDegrees(yaw + step);
	}

	private float alignmentFactor() {
		float delta = Math.abs(Mth.wrapDegrees(windDirection - yaw));
		if (delta >= 90.0f) return 0.0f;
		float cos = (float) Math.cos(Math.toRadians(delta));
		if (cos <= 0.0f) return 0.0f;
		return cos * cos * cos;
	}

	private static float baseFacingYaw(Direction facing) {
		return switch (facing) {
			case EAST -> 90.0f;
			case SOUTH -> 0.0f;
			case WEST -> 270.0f;
			default -> 180.0f;
		};
	}

	public float getYaw() {
		ensureYawInitialized();
		return yaw;
	}

	private void maybeSync() {
		if (level == null || level.isClientSide) return;
		long gameTime = level.getGameTime();
		float delta = Float.isNaN(lastSentYaw) ? Float.MAX_VALUE : Math.abs(Mth.wrapDegrees(yaw - lastSentYaw));
		boolean shouldSync = delta > 1.0f || gameTime - lastSyncTick >= 10;

		if (shouldSync) {
			lastSentYaw = yaw;
			lastSyncTick = gameTime;
			syncStateToClients();
		}
	}

	private void ensureYawInitialized() {
		if (yawInitialized) return;
		Direction facing = getBlockState().getValue(WindTurbineBlock.FACING);
		yaw = baseFacingYaw(facing);
		yawInitialized = true;
	}
}
