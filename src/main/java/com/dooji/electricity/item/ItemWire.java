package com.dooji.electricity.item;

import com.dooji.electricity.client.hooks.WireClientHooks;
import com.dooji.electricity.main.Electricity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public class ItemWire extends Item {
	public ItemWire() {
		super(new Properties().stacksTo(64));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getLevel().isClientSide) return DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> WireClientHooks.handleUseOn(context));
		return Electricity.wireManager.handleWireUse(context);
	}
}
