package com.dooji.electricity.block;

import com.dooji.electricity.api.power.ElectricityCapabilities;
import com.dooji.electricity.api.power.IElectricPowerConsumer;
import com.dooji.electricity.client.TrackedBlockEntities;
import com.dooji.electricity.client.render.obj.ObjBoundingBoxRegistry;
import com.dooji.electricity.client.render.obj.ObjModel;
import com.dooji.electricity.client.wire.InsulatorLookup;
import com.dooji.electricity.client.wire.WireManagerClient;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.wire.InsulatorIdRegistry;
import com.dooji.electricity.power.PowerFieldManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

public class PowerBoxBlockEntity extends BlockEntity {
	private Vec3[] wirePositions = new Vec3[1];
	private int[] insulatorIds = new int[1];

	private double currentPower = 0.0;
	private static final int POWER_RADIUS = 5;
	private static final double POWER_THRESHOLD = 0.1;
	private static final String[] POWER_PROPERTY_NAMES = {"powered", "lit"};
	private final Set<BlockPos> poweredBlocks = new HashSet<>();
	private boolean powerFieldActive = false;

	public PowerBoxBlockEntity(BlockPos pos, BlockState state) {
		super(getBlockEntityType(), pos, state);
		initializeWirePositions();
		generateInsulatorIds();
	}

	private static BlockEntityType<PowerBoxBlockEntity> getBlockEntityType() {
		if (Electricity.POWER_BOX_BLOCK_ENTITY != null) return Electricity.POWER_BOX_BLOCK_ENTITY.get();

		return null;
	}

	private void initializeWirePositions() {
		for (int i = 0; i < wirePositions.length; i++) {
			wirePositions[i] = null;
		}
	}

	private void generateInsulatorIds() {
		for (int i = 0; i < insulatorIds.length; i++) {
			if (insulatorIds[i] == 0) {
				insulatorIds[i] = InsulatorIdRegistry.claimId();
			}
		}
	}

	public static void removeInsulatorIds(int[] ids) {
		InsulatorIdRegistry.releaseIds(ids);
	}

	public Vec3 getWirePosition(int index) {
		if (index < 0 || index >= wirePositions.length) return null;

		return wirePositions[index];
	}

	public void setWirePosition(int index, Vec3 position) {
		if (index >= 0 && index < wirePositions.length) {
			wirePositions[index] = position;
			setChanged();
		}
	}

	public int getInsulatorId(int index) {
		if (index < 0 || index >= insulatorIds.length) return -1;

		return insulatorIds[index];
	}

	public int[] getInsulatorIds() {
		return insulatorIds.clone();
	}

	public double getCurrentPower() {
		return currentPower;
	}

	public void setCurrentPower(double power) {
		this.currentPower = power;
	}

	public Vec3 calculateOrientedInsulatorCenter(int index) {
		if (index < 0 || index >= 1) return null;

		String[] insulatorGroups = {"insulator_Material"};

		String groupName = insulatorGroups[index];
		ObjModel.BoundingBox boundingBox = ObjBoundingBoxRegistry.getBoundingBox(Electricity.POWER_BOX_BLOCK.get(), groupName);

		if (boundingBox == null) return null;

		Vector3f center = boundingBox.center;
		Vec3 localCenter = new Vec3(center.x, center.y, center.z);

		Direction facing = getBlockState().getValue(PowerBoxBlock.FACING);
		Vec3 rotatedCenter = rotateVector(localCenter, facing);

		Vec3 worldOffset = Vec3.atLowerCornerOf(getBlockPos()).add(0.5, 0, 0.5);
		return worldOffset.add(rotatedCenter);
	}

	private Vec3 rotateVector(Vec3 vector, Direction facing) {
		float facingRotation = switch (facing) {
			case EAST -> 0.0f;
			case SOUTH -> 270.0f;
			case WEST -> 180.0f;
			default -> 90.0f;
		};

		if (facingRotation == 0) return vector;

		float cosYaw = (float) Math.cos(Math.toRadians(facingRotation));
		float sinYaw = (float) Math.sin(Math.toRadians(facingRotation));
		float x = (float) (vector.x * cosYaw + vector.z * sinYaw);
		float z = (float) (-vector.x * sinYaw + vector.z * cosYaw);

		return new Vec3(x, vector.y, z);
	}

	public void tick() {
		updateWirePositions();
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			InsulatorLookup.register(this);
			WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
		});

		if (level == null || level.isClientSide()) return;

		boolean shouldActivate = currentPower > POWER_THRESHOLD;
		if (shouldActivate && !powerFieldActive) {
			powerFieldActive = true;
			refreshPowerField();
		} else if (!shouldActivate && powerFieldActive) {
			deactivatePowerField();
		} else if (shouldActivate) {
			refreshPowerField();
		}
	}

	private void updateWirePositions() {
		for (int i = 0; i < wirePositions.length; i++) {
			Vec3 calculatedPos = calculateOrientedInsulatorCenter(i);
			if (calculatedPos != null) {
				wirePositions[i] = calculatedPos;
			}
		}
	}

	private void refreshPowerField() {
		if (level == null) return;
		PowerFieldManager.clearSource(worldPosition);
		Set<BlockPos> newlyPowered = new HashSet<>();
		var consumers = new ArrayList<PowerConsumerTarget>();

		iteratePowerFieldPositions(pos -> {
			BlockPos immutablePos = pos.immutable();
			if (applyPowerToBlock(immutablePos, true)) {
				newlyPowered.add(immutablePos);
			}

			PowerFieldManager.markPowered(worldPosition, immutablePos, currentPower);

			BlockEntity blockEntity = level.getBlockEntity(immutablePos);
			if (blockEntity != null) {
				blockEntity.getCapability(ElectricityCapabilities.ELECTRIC_CONSUMER, null).ifPresent(consumer -> {
					consumers.add(new PowerConsumerTarget(immutablePos, consumer));
				});
			}
		});

		distributePowerToConsumers(consumers);

		poweredBlocks.clear();
		poweredBlocks.addAll(newlyPowered);
	}

	private void deactivatePowerField() {
		if (level == null) return;

		PowerFieldManager.clearSource(worldPosition);
		for (BlockPos pos : poweredBlocks) {
			applyPowerToBlock(pos, false);
		}

		notifyConsumersOfShutdown();
		poweredBlocks.clear();
		powerFieldActive = false;
	}

	private void iteratePowerFieldPositions(Consumer<BlockPos.MutableBlockPos> consumer) {
		BlockPos origin = getBlockPos();
		MutableBlockPos mutable = new MutableBlockPos();
		int radiusSq = POWER_RADIUS * POWER_RADIUS;

		for (int dx = -POWER_RADIUS; dx <= POWER_RADIUS; dx++) {
			for (int dy = -POWER_RADIUS; dy <= POWER_RADIUS; dy++) {
				for (int dz = -POWER_RADIUS; dz <= POWER_RADIUS; dz++) {
					int distSq = dx * dx + dy * dy + dz * dz;

					if (distSq > radiusSq) continue;
					if (dx == 0 && dy == 0 && dz == 0) continue;

					mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					consumer.accept(mutable);
				}
			}
		}
	}

	private boolean applyPowerToBlock(BlockPos pos, boolean powered) {
		if (level == null) return false;
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) return false;

		BooleanProperty property = findPowerProperty(state);
		if (property == null || state.getValue(property) == powered) return property != null;

		BlockState newState = state.setValue(property, powered);
		level.setBlock(pos, newState, Block.UPDATE_ALL);
		return true;
	}

	private void distributePowerToConsumers(List<PowerConsumerTarget> consumers) {
		if (consumers.isEmpty() || level == null) return;

		double totalRequired = 0.0;
		for (PowerConsumerTarget target : consumers) {
			totalRequired += Math.max(0.0, target.requiredPower());
		}

		double available = Math.max(0.0, currentPower);
		for (PowerConsumerTarget target : consumers) {
			double required = Math.max(0.0, target.requiredPower());
			double delivered = totalRequired <= 0.0 ? available : available * (required / totalRequired);
			boolean meets = delivered >= target.consumer().getMinimumOperationalPower();
			target.consumer().onPowerSupplied(delivered, meets);
			PowerFieldManager.markPowered(worldPosition, target.position(), delivered);
		}
	}

	private void notifyConsumersOfShutdown() {
		if (level == null) return;
		iteratePowerFieldPositions(pos -> {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity != null) {
				blockEntity.getCapability(ElectricityCapabilities.ELECTRIC_CONSUMER, null).ifPresent(consumer -> consumer.onPowerSupplied(0.0, false));
			}
		});
	}

	private record PowerConsumerTarget(BlockPos position, IElectricPowerConsumer consumer, double requiredPower) {
		PowerConsumerTarget(BlockPos position, IElectricPowerConsumer consumer) {
			this(position, consumer, consumer.getRequiredPower());
		}
	}

	private BooleanProperty findPowerProperty(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property instanceof BooleanProperty boolProp) {
				for (String target : POWER_PROPERTY_NAMES) {
					if (property.getName().equalsIgnoreCase(target)) return boolProp;
				}
			}
		}

		return null;
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);

		if (tag.contains("wirePositions", Tag.TAG_LIST)) {
			ListTag wirePositionsList = tag.getList("wirePositions", Tag.TAG_COMPOUND);
			for (int i = 0; i < Math.min(wirePositionsList.size(), wirePositions.length); i++) {
				CompoundTag posTag = wirePositionsList.getCompound(i);
				if (posTag.contains("x") && posTag.contains("y") && posTag.contains("z")) {
					wirePositions[i] = new Vec3(posTag.getDouble("x"), posTag.getDouble("y"), posTag.getDouble("z"));
				}
			}
		}

		if (tag.contains("insulatorIds", Tag.TAG_LIST)) {
			ListTag insulatorIdsList = tag.getList("insulatorIds", Tag.TAG_INT);
			for (int i = 0; i < Math.min(insulatorIdsList.size(), insulatorIds.length); i++) {
				insulatorIds[i] = insulatorIdsList.getInt(i);
				InsulatorIdRegistry.registerExistingId(insulatorIds[i]);
			}
		} else {
			generateInsulatorIds();
		}

		if (tag.contains("currentPower")) {
			currentPower = tag.getDouble("currentPower");
		}

		updateWirePositions();
	}

	@Override
	protected void saveAdditional(@Nonnull CompoundTag tag) {
		super.saveAdditional(tag);

		ListTag wirePositionsList = new ListTag();
		for (Vec3 pos : wirePositions) {
			if (pos != null) {
				CompoundTag posTag = new CompoundTag();
				posTag.putDouble("x", pos.x);
				posTag.putDouble("y", pos.y);
				posTag.putDouble("z", pos.z);
				wirePositionsList.add(posTag);
			} else {
				wirePositionsList.add(new CompoundTag());
			}
		}

		tag.put("wirePositions", wirePositionsList);

		ListTag insulatorIdsList = new ListTag();
		for (int id : insulatorIds) {
			insulatorIdsList.add(IntTag.valueOf(id));
		}

		tag.put("insulatorIds", insulatorIdsList);
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
		if (powerFieldActive && level != null && !level.isClientSide()) {
			deactivatePowerField();
		}

		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
				TrackedBlockEntities.untrack(this);
				InsulatorLookup.unregister(this.getInsulatorIds());
				WireManagerClient.invalidateInsulatorCache(this.getInsulatorIds());
			});
		}
	}
}
