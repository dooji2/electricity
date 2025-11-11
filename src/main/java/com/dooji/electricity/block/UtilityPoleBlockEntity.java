package com.dooji.electricity.block;

import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.wire.InsulatorIdRegistry;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
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

public class UtilityPoleBlockEntity extends BlockEntity {
	private Vec3[] wirePositions = new Vec3[8];
	private int[] insulatorIds = new int[8];
	private float offsetX = 0.0f;
	private float offsetY = 0.0f;
	private float offsetZ = 0.0f;
	private float yaw = 0.0f;
	private float pitch = 0.0f;

	private double currentPower = 0.0;

	public UtilityPoleBlockEntity(BlockPos pos, BlockState state) {
		super(getBlockEntityType(), pos, state);
		initializeWirePositions();
		generateInsulatorIds();
	}

	private static BlockEntityType<UtilityPoleBlockEntity> getBlockEntityType() {
		if (Electricity.UTILITY_POLE_BLOCK_ENTITY != null) return Electricity.UTILITY_POLE_BLOCK_ENTITY.get();

		return null;
	}

	private void initializeWirePositions() {
		Vec3 center = Vec3.atCenterOf(getBlockPos());

		String[] insulatorGroups = {"insulator_1_Material.023", "insulator_2_Material.009", "insulator_3_Material.016", "insulator_4_Material.001", "insulator_5_Material.051",
				"insulator_6_Material.037", "insulator_7_Material.030", "insulator_8_Material.058"};

		boolean useDynamicPositions = false;
		for (int i = 0; i < insulatorGroups.length && i < wirePositions.length; i++) {
			Vec3 orientedCenter = calculateOrientedInsulatorCenter(insulatorGroups[i]);
			if (orientedCenter != null) {
				wirePositions[i] = orientedCenter;
				useDynamicPositions = true;
			} else {
				break;
			}
		}

		if (!useDynamicPositions) {
			wirePositions[0] = center.add(0.2, 5.5, 0.2);
			wirePositions[1] = center.add(-0.2, 5.5, 0.2);
			wirePositions[2] = center.add(0.2, 5.5, -0.2);
			wirePositions[3] = center.add(-0.2, 5.5, -0.2);
			wirePositions[4] = center.add(0.2, 4.5, 0.2);
			wirePositions[5] = center.add(-0.2, 4.5, 0.2);
			wirePositions[6] = center.add(0.2, 4.5, -0.2);
			wirePositions[7] = center.add(-0.2, 4.5, -0.2);
		}
	}

	private Vec3 calculateOrientedInsulatorCenter(String insulatorGroup) {
		try {
			Vector3f localCenter = ObjBoundingBoxRegistry.getCenterSafe(getBlockState().getBlock(), insulatorGroup);
			if (localCenter == null) return null;

			Vector3f facingRotated = applyFacingRotation(localCenter);
			facingRotated.add(-offsetX, offsetY, -offsetZ);

			Vector3f rotatedCenter = applyYawPitchRotation(facingRotated, yaw, pitch);

			Vec3 worldPos = new Vec3(rotatedCenter.x, rotatedCenter.y, rotatedCenter.z);
			worldPos = worldPos.add(Vec3.atLowerCornerOf(getBlockPos())).add(0.5, 0, 0.5);

			return worldPos;
		} catch (Exception e) {
			return null;
		}
	}

	private Vector3f applyFacingRotation(Vector3f point) {
		Vector3f result = new Vector3f(point);
		var facing = getBlockState().getValue(UtilityPoleBlock.FACING);

		float facingRotation = switch (facing) {
			case EAST -> 180.0f;
			case SOUTH -> 270.0f;
			case WEST -> 0.0f;
			default -> 90.0f;
		};

		if (facingRotation != 0) {
			float cosYaw = (float) Math.cos(Math.toRadians(facingRotation));
			float sinYaw = (float) Math.sin(Math.toRadians(facingRotation));
			float x = result.x * cosYaw + result.z * sinYaw;
			float z = -result.x * sinYaw + result.z * cosYaw;
			result.x = x;
			result.z = z;
		}

		return result;
	}

	private Vector3f applyYawPitchRotation(Vector3f point, float yawRadians, float pitchRadians) {
		Vector3f result = new Vector3f(point);

		if (pitchRadians != 0) {
			float cosPitch = (float) Math.cos(-pitchRadians);
			float sinPitch = (float) Math.sin(-pitchRadians);
			float y = result.y * cosPitch - result.z * sinPitch;
			float z = result.y * sinPitch + result.z * cosPitch;
			result.y = y;
			result.z = z;
		}

		if (yawRadians != 0) {
			float cosYaw = (float) Math.cos(yawRadians);
			float sinYaw = (float) Math.sin(yawRadians);
			float x = result.x * cosYaw + result.z * sinYaw;
			float z = -result.x * sinYaw + result.z * cosYaw;
			result.x = x;
			result.z = z;
		}

		return result;
	}

	public Vec3 getWirePosition(int index) {
		if (index >= 0 && index < wirePositions.length) return wirePositions[index];
		return Vec3.atCenterOf(getBlockPos());
	}

	public float getOffsetX() {
		return offsetX;
	}

	public void setOffsetX(float offsetX) {
		this.offsetX = offsetX;
		updateWirePositions();
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
		}
	}

	public float getOffsetY() {
		return offsetY;
	}

	public void setOffsetY(float offsetY) {
		this.offsetY = offsetY;
		updateWirePositions();
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
		}
	}

	public float getOffsetZ() {
		return offsetZ;
	}

	public void setOffsetZ(float offsetZ) {
		this.offsetZ = offsetZ;
		updateWirePositions();
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
		}
	}

	public float getYaw() {
		return yaw;
	}

	public void setYaw(float yaw) {
		this.yaw = yaw;
		updateWirePositions();
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
		}
	}

	public float getPitch() {
		return pitch;
	}

	public double getCurrentPower() {
		return currentPower;
	}

	public void setCurrentPower(double power) {
		this.currentPower = power;
	}

	public void setPitch(float pitch) {
		this.pitch = pitch;
		updateWirePositions();
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
		}
	}

	private void updateWirePositions() {
		initializeWirePositions();
	}

	public ObjModel.OrientedBoundingBox getOrientedBoundingBoxForInsulator(String insulatorGroup) {
		try {
			ObjModel.BoundingBox localBbox = ObjBoundingBoxRegistry.getBoundingBox(getBlockState().getBlock(), insulatorGroup);
			if (localBbox == null) return null;

			Vector3f worldCenter = new Vector3f(getBlockPos().getX() + 0.5f, getBlockPos().getY(), getBlockPos().getZ() + 0.5f);

			Vector3f localCenter = applyFacingRotation(localBbox.center);
			worldCenter.add(localCenter);

			worldCenter.add(offsetX, offsetY, offsetZ);

			return new ObjModel.OrientedBoundingBox(worldCenter, localBbox.size, yaw, pitch);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	protected void saveAdditional(@Nonnull CompoundTag tag) {
		super.saveAdditional(tag);

		tag.putFloat("offsetX", offsetX);
		tag.putFloat("offsetY", offsetY);
		tag.putFloat("offsetZ", offsetZ);
		tag.putFloat("yaw", yaw);
		tag.putFloat("pitch", pitch);
		tag.putDouble("currentPower", currentPower);

		ListTag insulatorIdsList = new ListTag();
		for (int insulatorId : insulatorIds) {
			insulatorIdsList.add(IntTag.valueOf(insulatorId));
		}

		tag.put("insulatorIds", insulatorIdsList);
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);

		offsetX = tag.getFloat("offsetX");
		offsetY = tag.getFloat("offsetY");
		offsetZ = tag.getFloat("offsetZ");
		yaw = tag.getFloat("yaw");
		pitch = tag.getFloat("pitch");
		currentPower = tag.getDouble("currentPower");

		if (tag.contains("insulatorIds", Tag.TAG_LIST)) {
			ListTag insulatorIdsList = tag.getList("insulatorIds", Tag.TAG_INT);
			for (int i = 0; i < Math.min(insulatorIdsList.size(), insulatorIds.length); i++) {
				insulatorIds[i] = insulatorIdsList.getInt(i);
				InsulatorIdRegistry.registerExistingId(insulatorIds[i]);
			}
		} else {
			generateInsulatorIds();
		}

		updateWirePositions();
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		tag.putFloat("offsetX", offsetX);
		tag.putFloat("offsetY", offsetY);
		tag.putFloat("offsetZ", offsetZ);
		tag.putFloat("yaw", yaw);
		tag.putFloat("pitch", pitch);
		tag.putDouble("currentPower", currentPower);

		ListTag insulatorIdsList = new ListTag();
		for (int insulatorId : insulatorIds) {
			insulatorIdsList.add(IntTag.valueOf(insulatorId));
		}

		tag.put("insulatorIds", insulatorIdsList);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		super.handleUpdateTag(tag);
		offsetX = tag.getFloat("offsetX");
		offsetY = tag.getFloat("offsetY");
		offsetZ = tag.getFloat("offsetZ");
		yaw = tag.getFloat("yaw");
		pitch = tag.getFloat("pitch");
		currentPower = tag.getDouble("currentPower");

		if (tag.contains("insulatorIds", Tag.TAG_LIST)) {
			ListTag insulatorIdsList = tag.getList("insulatorIds", Tag.TAG_INT);
			for (int i = 0; i < Math.min(insulatorIdsList.size(), insulatorIds.length); i++) {
				insulatorIds[i] = insulatorIdsList.getInt(i);
				InsulatorIdRegistry.registerExistingId(insulatorIds[i]);
			}
		}

		updateWirePositions();
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			InsulatorLookup.register(this);
			WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
		});
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
		handleUpdateTag(packet.getTag());
	}

	private void generateInsulatorIds() {
		for (int i = 0; i < insulatorIds.length; i++) {
			if (insulatorIds[i] == 0) {
				insulatorIds[i] = InsulatorIdRegistry.claimId();
			}
		}
	}

	public int getInsulatorId(int index) {
		if (index >= 0 && index < insulatorIds.length) return insulatorIds[index];

		return -1;
	}

	public int[] getInsulatorIds() {
		return insulatorIds.clone();
	}

	public static void removeInsulatorIds(int[] ids) {
		InsulatorIdRegistry.releaseIds(ids);
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
