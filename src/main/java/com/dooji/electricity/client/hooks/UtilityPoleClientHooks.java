package com.dooji.electricity.client.hooks;

import com.dooji.electricity.client.UtilityPoleConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class UtilityPoleClientHooks {
	private UtilityPoleClientHooks() {
	}

	public static void openConfigScreen(BlockPos pos) {
		Minecraft.getInstance().setScreen(new UtilityPoleConfigScreen(pos));
	}
}
