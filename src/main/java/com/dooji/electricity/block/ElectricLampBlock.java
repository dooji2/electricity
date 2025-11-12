package com.dooji.electricity.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ElectricLampBlock extends Block implements EntityBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final EnumProperty<LampState> GLOW_STATE = EnumProperty.create("glow_state", LampState.class);
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

	public ElectricLampBlock(Properties properties) {
		super(properties.lightLevel(state -> state.getValue(GLOW_STATE).getLightLevel()).sound(SoundType.GLASS));
		this.registerDefaultState(this.defaultBlockState().setValue(LIT, Boolean.FALSE).setValue(GLOW_STATE, LampState.OFF));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT, GLOW_STATE);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Nullable @Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ElectricLampBlockEntity(pos, state);
	}

	@Nullable @Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof ElectricLampBlockEntity lamp) {
				lamp.serverTick();
			}
		};
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
		if (!level.isClientSide) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof ElectricLampBlockEntity lamp) {
				lamp.updateLampState();
			}
		}

		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
	}

	@Override
	public boolean isSignalSource(BlockState state) {
		return false;
	}

	@Override
	public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return 0;
	}

	@Override
	public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return 0;
	}

	public enum LampState implements StringRepresentable {
		OFF("off", 0, false),
		DIM("dim", 5, true),
		WARM("warm", 10, true),
		BRIGHT("bright", 13, true),
		OVERDRIVE("overdrive", 15, true),
		BURNT("burnt", 0, false);

		private final String name;
		private final int lightLevel;
		private final boolean emits;

		LampState(String name, int lightLevel, boolean emits) {
			this.name = name;
			this.lightLevel = lightLevel;
			this.emits = emits;
		}

		public int getLightLevel() {
			return lightLevel;
		}

		public boolean isEmitting() {
			return emits;
		}

		@Override
		public String getSerializedName() {
			return name;
		}

		public static LampState fromName(String name) {
			for (LampState state : values()) {
				if (state.name.equals(name)) return state;
			}
			return OFF;
		}
	}
}
