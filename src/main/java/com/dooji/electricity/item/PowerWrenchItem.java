package com.dooji.electricity.item;

import com.dooji.electricity.client.hooks.PowerWrenchClientHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import javax.annotation.Nullable;
import java.util.List;

public class PowerWrenchItem extends Item {
	private static final double DEFAULT_REACH = 16.0;

	public PowerWrenchItem() {
		super(new Item.Properties().stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide) return DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> PowerWrenchClientHooks.handleUse(level, player, hand, DEFAULT_REACH));
		return InteractionResultHolder.pass(stack);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player != null && level.isClientSide) return DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> PowerWrenchClientHooks.handleUseOn(context, DEFAULT_REACH));
		return InteractionResult.PASS;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.electricity.power_wrench").withStyle(ChatFormatting.GRAY));
	}
}
