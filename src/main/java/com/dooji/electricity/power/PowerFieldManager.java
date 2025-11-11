package com.dooji.electricity.power;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

public final class PowerFieldManager {
	private static final Map<BlockPos, Double> TARGET_POWER = new ConcurrentHashMap<>();
	private static final Map<BlockPos, Map<BlockPos, Double>> SOURCE_TARGETS = new ConcurrentHashMap<>();
	private static final Map<BlockPos, Map<BlockPos, Double>> TARGET_SOURCES = new ConcurrentHashMap<>();

	private PowerFieldManager() {
	}

	public static void markPowered(BlockPos source, BlockPos target, double power) {
		if (source == null || target == null) return;
		BlockPos immutableSource = source.immutable();
		BlockPos immutableTarget = target.immutable();

		SOURCE_TARGETS.computeIfAbsent(immutableSource, key -> new ConcurrentHashMap<>()).put(immutableTarget, power);
		TARGET_SOURCES.computeIfAbsent(immutableTarget, key -> new ConcurrentHashMap<>()).put(immutableSource, power);

		TARGET_POWER.put(immutableTarget, computeMaxPower(immutableTarget));
	}

	public static void clearSource(BlockPos source) {
		if (source == null) return;
		BlockPos immutableSource = source.immutable();
		Map<BlockPos, Double> targets = SOURCE_TARGETS.remove(immutableSource);
		if (targets == null) return;
		for (BlockPos target : targets.keySet()) {
			Map<BlockPos, Double> contributions = TARGET_SOURCES.get(target);
			if (contributions == null) continue;
			contributions.remove(immutableSource);
			if (contributions.isEmpty()) {
				TARGET_SOURCES.remove(target);
				TARGET_POWER.remove(target);
			} else {
				TARGET_POWER.put(target, computeMaxPower(target));
			}
		}
	}

	public static double getPowerAt(BlockPos target) {
		if (target == null) return 0.0;
		return TARGET_POWER.getOrDefault(target, 0.0);
	}

	private static double computeMaxPower(BlockPos target) {
		Map<BlockPos, Double> contributions = TARGET_SOURCES.get(target);
		if (contributions == null || contributions.isEmpty()) return 0.0;
		double max = 0.0;
		for (double value : contributions.values()) {
			if (value > max) {
				max = value;
			}
		}
		return max;
	}
}
