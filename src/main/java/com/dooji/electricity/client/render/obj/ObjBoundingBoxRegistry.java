package com.dooji.electricity.client.render.obj;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import org.joml.Vector3f;

// OBJ pipeline code will be migrated to Renderix
public class ObjBoundingBoxRegistry {
	private static final Map<Block, Map<String, ObjModel.BoundingBox>> BOXES = new HashMap<>();

	public static void registerBoundingBoxes(Block block, Map<String, ObjModel.BoundingBox> boxes) {
		BOXES.put(block, boxes);
	}

	public static ObjModel.BoundingBox getBoundingBox(Block block, String groupName) {
		Map<String, ObjModel.BoundingBox> blockBoxes = BOXES.get(block);
		return blockBoxes != null ? blockBoxes.get(groupName) : null;
	}

	public static Map<String, ObjModel.BoundingBox> getAllBoundingBoxes(Block block) {
		Map<String, ObjModel.BoundingBox> blockBoxes = BOXES.get(block);
		return blockBoxes != null ? Collections.unmodifiableMap(blockBoxes) : Collections.emptyMap();
	}

	public static Vector3f getCenter(Block block, String groupName) {
		ObjModel.BoundingBox bbox = getBoundingBox(block, groupName);
		return bbox != null ? bbox.center : null;
	}

	public static void clear() {
		BOXES.clear();
	}

	public static Vector3f getCenterSafe(Block block, String groupName) {
		try {
			return getCenter(block, groupName);
		} catch (Exception e) {
			return null;
		}
	}
}
