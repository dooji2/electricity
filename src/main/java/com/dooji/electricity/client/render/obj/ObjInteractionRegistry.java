package com.dooji.electricity.client.render.obj;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// OBJ pipeline code will be migrated to Renderix
@OnlyIn(Dist.CLIENT)
public class ObjInteractionRegistry {
	private static final Map<Block, InteractionData> DATA = new HashMap<>();

	private record InteractionData(Map<String, ObjInteractionHandler> handlers, Set<String> parts) {
	}

	public static void register(Block block, String partName, ObjInteractionHandler handler) {
		InteractionData data = DATA.computeIfAbsent(block, key -> new InteractionData(new HashMap<>(), new HashSet<>()));
		if (handler != null) {
			data.handlers().put(partName, handler);
		}
		data.parts().add(partName);
	}

	public static ObjInteractionHandler getHandler(Block block, String partName) {
		InteractionData data = DATA.get(block);
		return data != null ? data.handlers().get(partName) : null;
	}

	public static Set<String> getInteractiveParts(Block block) {
		InteractionData data = DATA.get(block);
		return data != null ? Collections.unmodifiableSet(data.parts()) : Collections.emptySet();
	}

	public static boolean isInteractivePart(Block block, String partName) {
		InteractionData data = DATA.get(block);
		return data != null && data.parts().contains(partName);
	}
}
