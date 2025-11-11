package com.dooji.electricity.main;

import com.dooji.electricity.api.power.ElectricityCapabilities;
import com.dooji.electricity.block.ElectricCabinBlock;
import com.dooji.electricity.block.ElectricCabinBlockEntity;
import com.dooji.electricity.block.ElectricLampBlock;
import com.dooji.electricity.block.ElectricLampBlockEntity;
import com.dooji.electricity.block.PowerBoxBlock;
import com.dooji.electricity.block.PowerBoxBlockEntity;
import com.dooji.electricity.block.UtilityPoleBlock;
import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.WindTurbineBlock;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.item.ItemWire;
import com.dooji.electricity.item.PowerWrenchItem;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.power.PowerNetwork;
import com.dooji.electricity.main.wire.WireManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Electricity.MOD_ID)
public class Electricity {
	public static final String MOD_ID = "electricity";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
	public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);

	public static final RegistryObject<Block> UTILITY_POLE_BLOCK = BLOCKS.register("utility_pole", () -> new UtilityPoleBlock(Block.Properties.of().strength(2.0f, 10.0f).noOcclusion()));

	public static final RegistryObject<Block> ELECTRIC_CABIN_BLOCK = BLOCKS.register("electric_cabin", () -> new ElectricCabinBlock(Block.Properties.of().strength(2.0f, 10.0f).noOcclusion()));

	public static final RegistryObject<Block> POWER_BOX_BLOCK = BLOCKS.register("power_box", () -> new PowerBoxBlock(Block.Properties.of().strength(2.0f, 10.0f).noOcclusion()));

	public static final RegistryObject<Block> WIND_TURBINE_BLOCK = BLOCKS.register("wind_turbine", () -> new WindTurbineBlock(Block.Properties.of().strength(2.0f, 10.0f).noOcclusion()));

	public static final RegistryObject<Block> ELECTRIC_LAMP_BLOCK = BLOCKS.register("electric_lamp", () -> new ElectricLampBlock(Block.Properties.of().strength(0.3f).noOcclusion()));

	public static final RegistryObject<Item> WIRE_ITEM = ITEMS.register("wire", ItemWire::new);
	public static final RegistryObject<Item> POWER_WRENCH_ITEM = ITEMS.register("power_wrench", PowerWrenchItem::new);
	public static final RegistryObject<Item> UTILITY_POLE_ITEM = ITEMS.register("utility_pole", () -> new BlockItem(UTILITY_POLE_BLOCK.get(), new Item.Properties()));
	public static final RegistryObject<Item> ELECTRIC_CABIN_ITEM = ITEMS.register("electric_cabin", () -> new BlockItem(ELECTRIC_CABIN_BLOCK.get(), new Item.Properties()));
	public static final RegistryObject<Item> POWER_BOX_ITEM = ITEMS.register("power_box", () -> new BlockItem(POWER_BOX_BLOCK.get(), new Item.Properties()));
	public static final RegistryObject<Item> WIND_TURBINE_ITEM = ITEMS.register("wind_turbine", () -> new BlockItem(WIND_TURBINE_BLOCK.get(), new Item.Properties()));
	public static final RegistryObject<Item> ELECTRIC_LAMP_ITEM = ITEMS.register("electric_lamp", () -> new BlockItem(ELECTRIC_LAMP_BLOCK.get(), new Item.Properties()));

	public static final RegistryObject<CreativeModeTab> ELECTRICITY_TAB = CREATIVE_TABS.register("main",
			() -> CreativeModeTab.builder().title(Component.translatable("itemGroup." + MOD_ID + ".main")).icon(() -> new ItemStack(WIRE_ITEM.get())).displayItems((parameters, output) -> {
				output.accept(POWER_WRENCH_ITEM.get());
				output.accept(WIRE_ITEM.get());
				output.accept(UTILITY_POLE_ITEM.get());
				output.accept(ELECTRIC_CABIN_ITEM.get());
				output.accept(POWER_BOX_ITEM.get());
				output.accept(WIND_TURBINE_ITEM.get());
				output.accept(ELECTRIC_LAMP_ITEM.get());
			}).build());

	public static RegistryObject<BlockEntityType<UtilityPoleBlockEntity>> UTILITY_POLE_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<ElectricCabinBlockEntity>> ELECTRIC_CABIN_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<PowerBoxBlockEntity>> POWER_BOX_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<WindTurbineBlockEntity>> WIND_TURBINE_BLOCK_ENTITY;
	public static RegistryObject<BlockEntityType<ElectricLampBlockEntity>> ELECTRIC_LAMP_BLOCK_ENTITY;

	public static final WireManager wireManager = new WireManager();
	public static PowerNetwork powerNetwork;

	public Electricity() {
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		BLOCKS.register(modEventBus);
		ITEMS.register(modEventBus);
		CREATIVE_TABS.register(modEventBus);

		UTILITY_POLE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("utility_pole", () -> BlockEntityType.Builder.of(UtilityPoleBlockEntity::new, UTILITY_POLE_BLOCK.get()).build(null));

		ELECTRIC_CABIN_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("electric_cabin", () -> BlockEntityType.Builder.of(ElectricCabinBlockEntity::new, ELECTRIC_CABIN_BLOCK.get()).build(null));

		POWER_BOX_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("power_box", () -> BlockEntityType.Builder.of(PowerBoxBlockEntity::new, POWER_BOX_BLOCK.get()).build(null));

		WIND_TURBINE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("wind_turbine", () -> BlockEntityType.Builder.of(WindTurbineBlockEntity::new, WIND_TURBINE_BLOCK.get()).build(null));

		ELECTRIC_LAMP_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("electric_lamp", () -> BlockEntityType.Builder.of(ElectricLampBlockEntity::new, ELECTRIC_LAMP_BLOCK.get()).build(null));

		BLOCK_ENTITY_TYPES.register(modEventBus);
		LOGGER.info("Registered {} items, {} blocks, and {} block entity types", ITEMS.getEntries().size(), BLOCKS.getEntries().size(), BLOCK_ENTITY_TYPES.getEntries().size());

		modEventBus.addListener(this::commonSetup);
		modEventBus.addListener(ElectricityCapabilities::register);
		MinecraftForge.EVENT_BUS.register(this);
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
		event.enqueueWork(ElectricityNetworking::init);
	}

	@SubscribeEvent
	public void onServerStarted(ServerStartedEvent event) {
		LOGGER.info("Electricity mod initialized on server");

		var overworld = event.getServer().overworld();
		if (overworld instanceof ServerLevel) {
			ServerLevel serverLevel = (ServerLevel) overworld;
			wireManager.loadFromWorld(serverLevel);
			powerNetwork = new PowerNetwork(serverLevel, wireManager);
		}
	}

	@SubscribeEvent
	public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			wireManager.sendAllWiresToPlayer(player);
		}
	}

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.END && powerNetwork != null) {
			powerNetwork.updatePowerNetwork();
		}
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		var overworld = event.getServer().overworld();
		if (overworld instanceof ServerLevel) {
			ServerLevel serverLevel = (ServerLevel) overworld;
			wireManager.forceSave(serverLevel);
		}
	}
}
