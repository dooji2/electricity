package com.dooji.electricity.client.render.obj;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// OBJ pipeline code will be migrated to Renderix
@OnlyIn(Dist.CLIENT)
public class ObjBlockRegistry {
	private static final Map<Block, Entry> ENTRIES = new HashMap<>();

	private record Entry(ResourceLocation model, ResourceLocation texture) {
	}

	public static void register(Block block, ResourceLocation modelLocation, ResourceLocation textureLocation) {
		ENTRIES.put(block, new Entry(modelLocation, textureLocation));
	}

	public static ResourceLocation getModelLocation(Block block) {
		Entry entry = ENTRIES.get(block);
		return entry != null ? entry.model() : null;
	}

	public static ResourceLocation getTextureLocation(Block block) {
		Entry entry = ENTRIES.get(block);
		return entry != null ? entry.texture() : null;
	}

	public static boolean hasModel(Block block) {
		return ENTRIES.containsKey(block);
	}
}
