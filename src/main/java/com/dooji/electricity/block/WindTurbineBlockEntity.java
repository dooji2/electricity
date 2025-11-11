package com.dooji.electricity.block;

import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.wire.InsulatorIdRegistry;
import java.util.Random;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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

	private float windSpeed = 0.0f;
	private float targetWindSpeed = 0.0f;
	private float windDirection = 0.0f;
	private float windTurbulence = 0.0f;
	private float rotationSpeed1 = 0.0f;
	private float rotationSpeed2 = 0.0f;
	private float rotation1 = 0.0f;
	private float rotation2 = 0.0f;
	private float windChangeTimer = 0.0f;
	private float windChangeDuration = 0.0f;
	private float baseWindSpeed = 0.0f;
	private float windVariation = 0.0f;

	private double generatedPower = 0.0;
	private double currentPower = 0.0;

	public WindTurbineBlockEntity(BlockPos pos, BlockState state) {
		super(getBlockEntityType(), pos, state);
		initializeWirePositions();
		generateInsulatorIds();
		initializeWindSimulation();
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

		return Vec3.ZERO;
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
		return windSpeed;
	}

	public float getWindDirection() {
		return windDirection;
	}

	public double getGeneratedPower() {
		updateGeneratedPower();
		return generatedPower;
	}

	public double getCurrentPower() {
		return currentPower;
	}

	public void setCurrentPower(double power) {
		this.currentPower = power;
	}

	private void updateGeneratedPower() {
		float effectiveWindSpeed = windSpeed;
		float powerEfficiency = Math.min(1.0f, effectiveWindSpeed / 12.0f);
		float basePower = 100.0f;
		generatedPower = basePower * powerEfficiency;
	}

	public Vec3 calculateOrientedInsulatorCenter(int index) {
		if (index != 0) return Vec3.ZERO;

		String groupName = "insulator_Plastic";
		var boundingBox = ObjBoundingBoxRegistry.getBoundingBox(Electricity.WIND_TURBINE_BLOCK.get(), groupName);
		if (boundingBox == null) return Vec3.ZERO;

		Vector3f center = boundingBox.center;
		Direction facing = getBlockState().getValue(WindTurbineBlock.FACING);
		Vec3 rotatedCenter = rotateVector(new Vec3(center.x(), center.y(), center.z()), facing);
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

	private void initializeWindSimulation() {
		long seed = getBlockPos().asLong();
		Random random = new Random(seed);

		baseWindSpeed = 3.0f + random.nextFloat() * 4.0f;
		windVariation = 1.0f + random.nextFloat() * 2.0f;
		windDirection = random.nextFloat() * 360.0f;
		windTurbulence = 0.1f + random.nextFloat() * 0.3f;

		targetWindSpeed = baseWindSpeed;
		windSpeed = baseWindSpeed;
		windChangeDuration = 5.0f + random.nextFloat() * 10.0f;
	}

	private void updateWindSimulation() {
		if (level == null) return;

		// run wind simulation on both client and server for now
		// TODO: only run on server and sync to client
		// TODO: add weather zones

		float currentTime = level.getGameTime() + level.getGameTime() * 0.05f;
		windChangeTimer += 0.05f;

		if (windChangeTimer >= windChangeDuration) {
			Random random = new Random((long) (currentTime + getBlockPos().asLong()));

			float newBaseWind = baseWindSpeed + (random.nextFloat() - 0.5f) * windVariation;
			newBaseWind = Math.max(0.1f, Math.min(8.0f, newBaseWind));

			targetWindSpeed = newBaseWind;
			windDirection += (random.nextFloat() - 0.5f) * 30.0f;
			windDirection = windDirection % 360.0f;
			if (windDirection < 0) windDirection += 360.0f;

			windChangeDuration = 3.0f + random.nextFloat() * 12.0f;
			windChangeTimer = 0.0f;
		}

		float windSpeedDiff = targetWindSpeed - windSpeed;
		float windAcceleration = 0.02f;

		if (Math.abs(windSpeedDiff) > 0.01f) {
			float t = Math.min(1.0f, windAcceleration);
			t = t * t * (3.0f - 2.0f * t);

			windSpeed += windSpeedDiff * t;
		} else {
			windSpeed = targetWindSpeed;
		}

		float turbulence = (float) Math.sin(currentTime * 0.1) * windTurbulence;
		float effectiveWindSpeed = windSpeed + turbulence;
		effectiveWindSpeed = Math.max(0.0f, effectiveWindSpeed);

		float maxRotationSpeed = 5.0f;
		float windEfficiency = Math.min(1.0f, effectiveWindSpeed / 4.0f);

		float targetRotationSpeed = windEfficiency * maxRotationSpeed;

		float rotationAcceleration = 0.05f;
		float rotationDiff1 = targetRotationSpeed - rotationSpeed1;
		float rotationDiff2 = targetRotationSpeed - rotationSpeed2;

		if (Math.abs(rotationDiff1) > 0.001f) {
			rotationSpeed1 += rotationDiff1 * rotationAcceleration;
		}

		if (Math.abs(rotationDiff2) > 0.001f) {
			rotationSpeed2 += rotationDiff2 * rotationAcceleration;
		}

		float variation1 = 1.0f + (float) Math.sin(currentTime * 0.05) * 0.1f;
		float variation2 = 1.0f + (float) Math.cos(currentTime * 0.07) * 0.1f;

		rotation1 += rotationSpeed1 * variation1;
		rotation2 += rotationSpeed2 * variation2;

		rotation1 = rotation1 % 360.0f;
		rotation2 = rotation2 % 360.0f;
		if (rotation1 < 0) rotation1 += 360.0f;
		if (rotation2 < 0) rotation2 += 360.0f;
	}

	public void tick() {
		updateWindSimulation();
		updateGeneratedPower();
		updateWirePositions();
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			InsulatorLookup.register(this);
			WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
		});
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

		windSpeed = tag.getFloat("windSpeed");
		targetWindSpeed = tag.getFloat("targetWindSpeed");
		windDirection = tag.getFloat("windDirection");
		windTurbulence = tag.getFloat("windTurbulence");
		rotationSpeed1 = tag.getFloat("rotationSpeed1");
		rotationSpeed2 = tag.getFloat("rotationSpeed2");
		rotation1 = tag.getFloat("rotation1");
		rotation2 = tag.getFloat("rotation2");
		windChangeTimer = tag.getFloat("windChangeTimer");
		windChangeDuration = tag.getFloat("windChangeDuration");
		baseWindSpeed = tag.getFloat("baseWindSpeed");
		windVariation = tag.getFloat("windVariation");
		generatedPower = tag.getDouble("generatedPower");
		currentPower = tag.getDouble("currentPower");

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

		tag.putFloat("windSpeed", windSpeed);
		tag.putFloat("targetWindSpeed", targetWindSpeed);
		tag.putFloat("windDirection", windDirection);
		tag.putFloat("windTurbulence", windTurbulence);
		tag.putFloat("rotationSpeed1", rotationSpeed1);
		tag.putFloat("rotationSpeed2", rotationSpeed2);
		tag.putFloat("rotation1", rotation1);
		tag.putFloat("rotation2", rotation2);
		tag.putFloat("windChangeTimer", windChangeTimer);
		tag.putFloat("windChangeDuration", windChangeDuration);
		tag.putFloat("baseWindSpeed", baseWindSpeed);
		tag.putFloat("windVariation", windVariation);
		tag.putDouble("generatedPower", generatedPower);
		tag.putDouble("currentPower", currentPower);
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
}
