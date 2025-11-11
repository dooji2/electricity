package com.dooji.electricity.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class TrackedBlockEntities {
	private static final Set<BlockEntity> TRACKED = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private TrackedBlockEntities() {
	}

	public static void track(BlockEntity blockEntity) {
		if (blockEntity != null) {
			TRACKED.add(blockEntity);
		}
	}

	public static void untrack(BlockEntity blockEntity) {
		if (blockEntity != null) {
			TRACKED.remove(blockEntity);
		}
	}

	public static Iterable<BlockEntity> all() {
		return TRACKED;
	}

	public static void clear() {
		TRACKED.clear();
	}

	public static <T extends BlockEntity> Iterable<T> ofType(Class<T> type) {
		if (type == null) return Collections.emptyList();
		var matches = new ArrayList<T>();
		for (BlockEntity blockEntity : TRACKED) {
			if (type.isInstance(blockEntity)) {
				matches.add(type.cast(blockEntity));
			}
		}
		return matches;
	}
}
