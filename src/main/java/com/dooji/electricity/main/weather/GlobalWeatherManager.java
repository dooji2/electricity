package com.dooji.electricity.main.weather;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.levelgen.Heightmap;

public final class GlobalWeatherManager {
	private static final Map<ServerLevel, GlobalWeatherManager> INSTANCES = new ConcurrentHashMap<>();
	private static final int ZONE_SIZE_CHUNKS = 6;
	private static final int TICKS_PER_STEP = 20;
	private static final int SAVE_INTERVAL = 200;
	private static final int PRUNE_INTERVAL = 400;
	private static final long PRUNE_AGE_TICKS = 24000;
	private static final long ACTIVE_AGE_TICKS = 2400;
	private static final double WIND_MIN = 0.2;
	private static final double WIND_MAX = 24.0;
	private final ServerLevel level;
	private final Map<Long, WeatherCell> cells = new ConcurrentHashMap<>();
	private int tickCounter = 0;
	private final float flowDirectionSeed;
	private float flowDirection;
	private final WeatherSavedData savedData;
	private static final ToDoubleFunction<WeatherCell> WIND_EXTRACTOR = c -> c.windSpeed;
	private static final ToDoubleFunction<WeatherCell> GUST_EXTRACTOR = WeatherCell::gustSpeed;
	private static final ToDoubleFunction<WeatherCell> TURB_EXTRACTOR = c -> c.turbulence;
	private static final ToDoubleFunction<WeatherCell> STORM_EXTRACTOR = c -> c.stormIntensity;

	private static final class WeatherCell {
		double windSpeed;
		double targetWindSpeed;
		double turbulence;
		float direction;
		double stormIntensity;
		double moistureStore;
		long lastTouched;
		final long seed;
		final int zoneX;
		final int zoneZ;
		final double phase;

		WeatherCell(long seed, int zoneX, int zoneZ, float direction, double windSpeed, double turbulence, double phase, double stormIntensity, double moistureStore) {
			this.seed = seed;
			this.zoneX = zoneX;
			this.zoneZ = zoneZ;
			this.direction = direction;
			this.windSpeed = windSpeed;
			this.targetWindSpeed = windSpeed;
			this.turbulence = turbulence;
			this.phase = phase;
			this.stormIntensity = stormIntensity;
			this.moistureStore = moistureStore;
			this.lastTouched = 0L;
		}

		double gustSpeed() {
			double gustFactor = 1.0 + turbulence * 0.9;
			return windSpeed * gustFactor;
		}
	}

	public static GlobalWeatherManager get(ServerLevel level) {
		return INSTANCES.computeIfAbsent(level, GlobalWeatherManager::new);
	}

	public static void clear(ServerLevel level) {
		INSTANCES.remove(level);
	}

	private GlobalWeatherManager(ServerLevel level) {
		this.level = level;
		XoroshiroRandomSource random = new XoroshiroRandomSource(level.getSeed() ^ 0x9E3779B97F4A7C15L);

		this.flowDirectionSeed = random.nextFloat() * 360.0f;
		this.savedData = level.getDataStorage().computeIfAbsent(WeatherSavedData::load, WeatherSavedData::new, "electricity_weather");
		this.flowDirection = savedData.flowDirection != null ? savedData.flowDirection.floatValue() : flowDirectionSeed;
		restoreSavedCells();
	}

	public void tick() {
		tickCounter++;
		if (tickCounter % TICKS_PER_STEP != 0) return;

		flowDirection = wrapDegrees(flowDirection + 0.05f * Mth.sin((level.getGameTime() + flowDirectionSeed) / 2400.0f));

		Map<Long, WeatherCell> snapshot = new HashMap<>(cells.size());
		for (Map.Entry<Long, WeatherCell> entry : cells.entrySet()) {
			WeatherCell src = entry.getValue();
			WeatherCell copy = new WeatherCell(src.seed, src.zoneX, src.zoneZ, src.direction, src.windSpeed, src.turbulence, src.phase, src.stormIntensity, src.moistureStore);
			copy.targetWindSpeed = src.targetWindSpeed;
			copy.lastTouched = src.lastTouched;
			snapshot.put(entry.getKey(), copy);
		}

		long now = level.getGameTime();
		for (WeatherCell cell : cells.values()) {
			if (now - cell.lastTouched > ACTIVE_AGE_TICKS && !zoneHasLoadedChunks(cell.zoneX, cell.zoneZ)) continue;
			updateCell(cell, snapshot);
		}

		if (tickCounter % PRUNE_INTERVAL == 0) {
			pruneInactive();
		}

		if (tickCounter % SAVE_INTERVAL == 0) {
			savedData.setFlowDirection(flowDirection);
			savedData.flushIfDirty();
		}
	}

	public WeatherSnapshot sample(BlockPos pos) {
		WeatherCell cell = getCell(pos);
		cell.lastTouched = level.getGameTime();

		return new WeatherSnapshot(cell.windSpeed, cell.gustSpeed(), cell.turbulence, cell.direction);
	}

	public Map<Long, WeatherSnapshot> snapshotZones() {
		Map<Long, WeatherSnapshot> map = new HashMap<>();
		for (Map.Entry<Long, WeatherCell> entry : cells.entrySet()) {
			WeatherCell cell = entry.getValue();
			map.put(entry.getKey(), new WeatherSnapshot(cell.windSpeed, cell.gustSpeed(), cell.turbulence, cell.direction));
		}
		return map;
	}

	private WeatherCell getCell(BlockPos pos) {
		int zoneX = Math.floorDiv(pos.getX() >> 4, ZONE_SIZE_CHUNKS);
		int zoneZ = Math.floorDiv(pos.getZ() >> 4, ZONE_SIZE_CHUNKS);
		long key = (((long) zoneX) << 32) ^ (zoneZ & 0xffffffffL);

		WeatherCell cell = cells.computeIfAbsent(key, unused -> createCell(zoneX, zoneZ));
		cell.lastTouched = level.getGameTime();
		return cell;
	}

	private WeatherCell createCell(int zoneX, int zoneZ) {
		long seed = mixSeed(zoneX, zoneZ);
		XoroshiroRandomSource random = new XoroshiroRandomSource(seed);

		float direction = random.nextFloat() * 360.0f;
		double baseWind = 3.0 + random.nextDouble() * 4.0;
		double turbulence = 0.05 + random.nextDouble() * 0.05;
		double phase = random.nextDouble() * Math.PI * 2.0;
		double stormIntensity = 0.15 + random.nextDouble() * 0.1;
		double moistureStore = 0.2 + random.nextDouble() * 0.1;

		WeatherSavedData.CellData data = savedData.get(zoneX, zoneZ);
		if (data != null) {
			WeatherCell restored = new WeatherCell(seed, zoneX, zoneZ, (float) data.direction(), data.windSpeed(), data.turbulence(), data.phase(), data.stormIntensity(), data.moistureStore());
			restored.targetWindSpeed = data.targetWindSpeed();
			restored.lastTouched = level.getGameTime();
			return restored;
		}

		WeatherCell created = new WeatherCell(seed, zoneX, zoneZ, direction, baseWind, turbulence, phase, stormIntensity, moistureStore);
		created.lastTouched = level.getGameTime();
		return created;
	}

	private void updateCell(WeatherCell cell, Map<Long, WeatherCell> snapshot) {
		BlockPos center = new BlockPos(getZoneCenter(cell.zoneX, cell.zoneZ));
		double rainForcing = samplePrecipitation(center);
		boolean thunder = level.isThundering() && rainForcing > 0.25;
		int surfaceY = center.getY();

		if (level.getChunkSource().hasChunk(center.getX() >> 4, center.getZ() >> 4)) {
			surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, center.getX(), center.getZ());
		}

		double seaLevel = level.getSeaLevel();
		double altitude = surfaceY - seaLevel;
		double altitudeBias = Mth.clamp(altitude / 70.0, -0.3, 1.2);

		BlockPos tempPos = new BlockPos(center.getX(), surfaceY, center.getZ());
		var biomeHolder = level.getBiome(tempPos);
		var climate = biomeHolder.value().getModifiedClimateSettings();
		float temperature = climate.temperatureModifier().modifyTemperature(tempPos, climate.temperature());

		double coldBias = Mth.clamp(1.0 - temperature, 0.0, 1.5);
		double heatBias = Mth.clamp(temperature - 1.05, 0.0, 1.0);

		double time = level.getGameTime() + cell.seed;
		double synoptic = 0.5 + 0.5 * Math.sin(time / 3600.0 + cell.phase);
		double pulse = 0.5 + 0.5 * Math.sin(time / 2000.0 + cell.phase * 0.7);

		double moistureInput = 0.12 + rainForcing * 0.22 + (thunder ? 0.12 : 0.0) + coldBias * 0.12 - heatBias * 0.08 + synoptic * 0.08;
		moistureInput = Mth.clamp(moistureInput, 0.0, 1.0);

		double upstream = sampleUpstreamStorm(cell);
		double neighborBlend = sampleNeighborStorm(cell, snapshot);
		float heading = flowHeading(cell.zoneX, cell.zoneZ, time);

		double advectFactor = 0.45 + Mth.clamp(cell.windSpeed / WIND_MAX, 0.0, 1.0) * 0.55;
		double advectDist = advectFactor * 0.9;

		double advectX = Math.cos(Math.toRadians(heading)) * advectDist;
		double advectZ = Math.sin(Math.toRadians(heading)) * advectDist;

		double advectedStorm = sampleField(cell.zoneX - advectX, cell.zoneZ - advectZ, snapshot, STORM_EXTRACTOR);
		double advectedWind = sampleField(cell.zoneX - advectX, cell.zoneZ - advectZ, snapshot, WIND_EXTRACTOR);
		double advectedTurb = sampleField(cell.zoneX - advectX, cell.zoneZ - advectZ, snapshot, TURB_EXTRACTOR);

		double growth = moistureInput * (0.4 + synoptic * 0.28) + upstream * 0.42 + neighborBlend * 0.28 + pulse * 0.1 + altitudeBias * 0.1;
		double decay = 0.008 + heatBias * 0.01 + cell.stormIntensity * 0.016;

		cell.moistureStore = Mth.clamp(Mth.lerp(0.55, cell.moistureStore, moistureInput), 0.0, 1.0);
		double stormTarget = Mth.clamp(cell.stormIntensity + growth - decay, 0.0, 1.0);
		stormTarget = Mth.lerp(0.35, stormTarget, advectedStorm);
		cell.stormIntensity = Mth.lerp(0.45, cell.stormIntensity, stormTarget);

		double calmFloor = 2.2 + coldBias * 0.8 - heatBias * 0.5 + altitudeBias * 1.2;
		double convective = 11.0 * cell.stormIntensity;
		double stormJet = cell.stormIntensity * 6.0;
		double baseMax = 7.0 + convective + stormJet + rainForcing * 1.1 + altitudeBias * 2.2;
		if (thunder) {
			baseMax += 3.5 * cell.stormIntensity;
		}

		double shaping = Mth.clamp(0.55 + 0.3 * synoptic + 0.12 * pulse + altitudeBias * 0.08 + cell.stormIntensity * 0.12, 0.0, 1.0);
		double target = calmFloor + (baseMax - calmFloor) * shaping;
		target = Mth.clamp(target, WIND_MIN, WIND_MAX);

		double advectedTarget = Mth.lerp(0.38, target, advectedWind);
		double blendedTarget = Mth.lerp(0.25, advectedTarget, sampleNeighborWind(cell, target, snapshot));

		cell.targetWindSpeed = Mth.lerp(0.26, cell.targetWindSpeed, blendedTarget);
		cell.windSpeed = Mth.lerp(0.22, cell.windSpeed, cell.targetWindSpeed);

		double windBias = Mth.clamp(cell.windSpeed / WIND_MAX, 0.0, 1.0);
		double turbulenceTarget = Mth.clamp(0.08 + windBias * 0.2 + cell.stormIntensity * 0.35 + rainForcing * 0.08 + altitudeBias * 0.1 + (thunder ? 0.08 * cell.stormIntensity : 0.0), 0.03, 1.0);
		turbulenceTarget = Mth.lerp(0.22, turbulenceTarget, advectedTurb);
		cell.turbulence = Mth.lerp(0.32, cell.turbulence, turbulenceTarget);

		float flowTarget = flowHeading(cell.zoneX, cell.zoneZ, time);
		cell.direction = lerpAngle(cell.direction, flowTarget, 0.18f);
		savedData.update(cell.zoneX, cell.zoneZ, cell.windSpeed, cell.targetWindSpeed, cell.turbulence, cell.direction, cell.stormIntensity, cell.moistureStore, cell.phase);
	}

	private double samplePrecipitation(BlockPos center) {
		int samples = 0;
		int raining = 0;
		int radius = ZONE_SIZE_CHUNKS * 8;
		int[][] offsets = {{-radius, -radius}, {radius, -radius}, {-radius, radius}, {radius, radius}};

		for (int[] offset : offsets) {
			int x = center.getX() + offset[0];
			int z = center.getZ() + offset[1];
			int chunkX = x >> 4;
			int chunkZ = z >> 4;
			if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) continue;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			samples++;
			if (level.isRainingAt(pos)) raining++;
		}

		if (samples == 0) return 0.0;
		return (double) raining / samples;
	}

	private BlockPos getZoneCenter(int zoneX, int zoneZ) {
		int blockX = zoneX * ZONE_SIZE_CHUNKS * 16 + ZONE_SIZE_CHUNKS * 8;
		int blockZ = zoneZ * ZONE_SIZE_CHUNKS * 16 + ZONE_SIZE_CHUNKS * 8;
		return new BlockPos(blockX, 64, blockZ);
	}

	private double sampleNeighborWind(WeatherCell cell, double target, Map<Long, WeatherCell> snapshot) {
		double total = target;
		int count = 1;
		int zoneX = cell.zoneX;
		int zoneZ = cell.zoneZ;
		total += neighborWind(zoneX + 1, zoneZ, snapshot);
		total += neighborWind(zoneX - 1, zoneZ, snapshot);
		total += neighborWind(zoneX, zoneZ + 1, snapshot);
		total += neighborWind(zoneX, zoneZ - 1, snapshot);
		count += 4;
		return total / count;
	}

	private double neighborWind(int zoneX, int zoneZ, Map<Long, WeatherCell> snapshot) {
		long key = (((long) zoneX) << 32) ^ (zoneZ & 0xffffffffL);
		WeatherCell cell = snapshot.get(key);
		if (cell != null) return cell.windSpeed;
		return targetForCoordinates(zoneX, zoneZ);
	}

	private double sampleNeighborStorm(WeatherCell cell, Map<Long, WeatherCell> snapshot) {
		double total = 0.0;
		int count = 0;
		int zoneX = cell.zoneX;
		int zoneZ = cell.zoneZ;
		total += neighborStorm(zoneX + 1, zoneZ, snapshot);
		total += neighborStorm(zoneX - 1, zoneZ, snapshot);
		total += neighborStorm(zoneX, zoneZ + 1, snapshot);
		total += neighborStorm(zoneX, zoneZ - 1, snapshot);
		count += 4;
		return count == 0 ? 0.0 : total / count;
	}

	private double neighborStorm(int zoneX, int zoneZ, Map<Long, WeatherCell> snapshot) {
		long key = (((long) zoneX) << 32) ^ (zoneZ & 0xffffffffL);
		WeatherCell cell = snapshot.get(key);
		if (cell != null) return cell.stormIntensity;
		return 0.0;
	}

	private double sampleUpstreamStorm(WeatherCell cell) {
		double radians = Math.toRadians(flowHeading(cell.zoneX, cell.zoneZ, level.getGameTime() + cell.seed));
		int stepX = (int) Math.signum(Math.cos(radians));
		int stepZ = (int) Math.signum(Math.sin(radians));

		if (stepX == 0 && stepZ == 0) stepX = 1;
		int upstreamX = cell.zoneX - stepX;
		int upstreamZ = cell.zoneZ - stepZ;
		long key = (((long) upstreamX) << 32) ^ (upstreamZ & 0xffffffffL);
		WeatherCell upstream = cells.get(key);

		if (upstream != null) return upstream.stormIntensity;
		return 0.1;
	}

	private double targetForCoordinates(int zoneX, int zoneZ) {
		long seed = mixSeed(zoneX, zoneZ);
		XoroshiroRandomSource random = new XoroshiroRandomSource(seed);
		return 2.5 + random.nextDouble() * 4.0;
	}

	private long mixSeed(int zoneX, int zoneZ) {
		long seed = level.getSeed();
		return seed ^ (((long) zoneX) * 341873128712L) ^ (((long) zoneZ) * 132897987541L);
	}

	private static float wrapDegrees(float degrees) {
		float wrapped = degrees % 360.0f;
		if (wrapped < 0) wrapped += 360.0f;
		return wrapped;
	}

	private double sampleField(double zoneX, double zoneZ, Map<Long, WeatherCell> snapshot, ToDoubleFunction<WeatherCell> extractor) {
		int x0 = (int) Math.floor(zoneX);
		int z0 = (int) Math.floor(zoneZ);
		int x1 = x0 + 1;
		int z1 = z0 + 1;
		double tx = zoneX - x0;
		double tz = zoneZ - z0;

		double c00 = sampleCellValue(x0, z0, snapshot, extractor);
		double c10 = sampleCellValue(x1, z0, snapshot, extractor);
		double c01 = sampleCellValue(x0, z1, snapshot, extractor);
		double c11 = sampleCellValue(x1, z1, snapshot, extractor);

		double a = Mth.lerp(tx, c00, c10);
		double b = Mth.lerp(tx, c01, c11);
		return Mth.lerp(tz, a, b);
	}

	private double sampleCellValue(int zoneX, int zoneZ, Map<Long, WeatherCell> snapshot, ToDoubleFunction<WeatherCell> extractor) {
		WeatherCell cell = snapshot.get((((long) zoneX) << 32) ^ (zoneZ & 0xffffffffL));
		if (cell != null) return extractor.applyAsDouble(cell);
		if (extractor == WIND_EXTRACTOR || extractor == GUST_EXTRACTOR) return targetForCoordinates(zoneX, zoneZ);
		if (extractor == TURB_EXTRACTOR) return 0.08;
		if (extractor == STORM_EXTRACTOR) return 0.02;
		return 0.0;
	}

	private float lerpAngle(float current, float target, float alpha) {
		float delta = wrapDegrees(target - current);
		if (delta > 180.0f) delta -= 360.0f;
		return wrapDegrees(current + delta * alpha);
	}

	private static double angularDelta(double a, double b) {
		double delta = a - b;
		delta = (delta + 540.0) % 360.0 - 180.0;
		return delta;
	}

	private float flowHeading(int zoneX, int zoneZ, double time) {
		double base = flowDirection + Math.sin(zoneX * 0.12 + time / 3200.0) * 18.0 + Math.cos(zoneZ * 0.1 + time / 2800.0) * 14.0;
		double neighborX = flowDirection + Math.sin((zoneX + 1) * 0.12 + time / 3200.0) * 18.0 + Math.cos(zoneZ * 0.1 + time / 2800.0) * 14.0;
		double neighborZ = flowDirection + Math.sin(zoneX * 0.12 + time / 3200.0) * 18.0 + Math.cos((zoneZ + 1) * 0.1 + time / 2800.0) * 14.0;
		double avg = (base + neighborX + neighborZ) / 3.0;
		return wrapDegrees((float) (flowDirectionSeed + avg));
	}

	private boolean zoneHasLoadedChunks(int zoneX, int zoneZ) {
		int startChunkX = zoneX * ZONE_SIZE_CHUNKS;
		int startChunkZ = zoneZ * ZONE_SIZE_CHUNKS;
		int endChunkX = startChunkX + ZONE_SIZE_CHUNKS - 1;
		int endChunkZ = startChunkZ + ZONE_SIZE_CHUNKS - 1;

		for (int chunkX = startChunkX; chunkX <= endChunkX; chunkX++) {
			for (int chunkZ = startChunkZ; chunkZ <= endChunkZ; chunkZ++) {
				if (level.getChunkSource().hasChunk(chunkX, chunkZ)) {
					return true;
				}
			}
		}

		return false;
	}

	private void pruneInactive() {
		long now = level.getGameTime();
		cells.entrySet().removeIf(entry -> {
			WeatherCell cell = entry.getValue();
			if (now - cell.lastTouched < PRUNE_AGE_TICKS) return false;
			int startChunkX = cell.zoneX * ZONE_SIZE_CHUNKS;
			int startChunkZ = cell.zoneZ * ZONE_SIZE_CHUNKS;
			int endChunkX = startChunkX + ZONE_SIZE_CHUNKS - 1;
			int endChunkZ = startChunkZ + ZONE_SIZE_CHUNKS - 1;

			for (int chunkX = startChunkX; chunkX <= endChunkX; chunkX++) {
				for (int chunkZ = startChunkZ; chunkZ <= endChunkZ; chunkZ++) {
					if (level.getChunkSource().hasChunk(chunkX, chunkZ)) {
						return false;
					}
				}
			}

			savedData.remove(cell.zoneX, cell.zoneZ);
			return true;
		});
	}

	private void restoreSavedCells() {
		for (WeatherSavedData.CellData data : savedData.all()) {
			long key = (((long) data.zoneX()) << 32) ^ (data.zoneZ() & 0xffffffffL);
			double windSpeed = data.windSpeed();
			double targetWindSpeed = data.targetWindSpeed();
			double turbulence = data.turbulence();
			float direction = (float) data.direction();
			double stormIntensity = data.stormIntensity();
			double moistureStore = data.moistureStore();
			double phase = data.phase();

			WeatherCell cell = new WeatherCell(mixSeed(data.zoneX(), data.zoneZ()), data.zoneX(), data.zoneZ(), direction, windSpeed, turbulence, phase, stormIntensity, moistureStore);
			cell.targetWindSpeed = targetWindSpeed;
			cell.lastTouched = level.getGameTime();
			cells.put(key, cell);
		}
	}

	private static final class WeatherSavedData extends SavedData {
		private final Map<Long, CellData> cells = new ConcurrentHashMap<>();
		private Double flowDirection;
		private boolean dirtyFlag = false;

		private WeatherSavedData() {
		}

		private static WeatherSavedData load(CompoundTag tag) {
			WeatherSavedData data = new WeatherSavedData();
			data.read(tag);
			return data;
		}

		@Override
		public CompoundTag save(CompoundTag tag) {
			ListTag list = new ListTag();
			for (CellData cell : cells.values()) {
				CompoundTag entry = new CompoundTag();
				entry.putInt("zoneX", cell.zoneX());
				entry.putInt("zoneZ", cell.zoneZ());
				entry.putDouble("windSpeed", cell.windSpeed());
				entry.putDouble("targetWindSpeed", cell.targetWindSpeed());
				entry.putDouble("turbulence", cell.turbulence());
				entry.putDouble("direction", cell.direction());
				entry.putDouble("stormIntensity", cell.stormIntensity());
				entry.putDouble("moistureStore", cell.moistureStore());
				entry.putDouble("phase", cell.phase());
				list.add(entry);
			}

			tag.put("cells", list);
			if (flowDirection != null) {
				tag.putDouble("flowDirection", flowDirection);
			}

			return tag;
		}

		private void read(CompoundTag tag) {
			cells.clear();
			ListTag list = tag.getList("cells", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompound(i);
				int zoneX = entry.getInt("zoneX");
				int zoneZ = entry.getInt("zoneZ");
				double windSpeed = entry.getDouble("windSpeed");
				double targetWindSpeed = entry.getDouble("targetWindSpeed");
				double turbulence = entry.getDouble("turbulence");
				double direction = entry.getDouble("direction");
				double stormIntensity = entry.getDouble("stormIntensity");
				double moistureStore = entry.getDouble("moistureStore");
				double phase = entry.getDouble("phase");
				long key = (((long) zoneX) << 32) ^ (zoneZ & 0xffffffffL);
				cells.put(key, new CellData(zoneX, zoneZ, windSpeed, targetWindSpeed, turbulence, direction, stormIntensity, moistureStore, phase));
			}

			if (tag.contains("flowDirection")) {
				flowDirection = tag.getDouble("flowDirection");
			}
		}

		private void update(int zoneX, int zoneZ, double windSpeed, double targetWindSpeed, double turbulence, double direction, double stormIntensity, double moistureStore, double phase) {
			long key = (((long) zoneX) << 32) ^ (zoneZ & 0xffffffffL);
			CellData existing = cells.get(key);
			if (existing != null) {
				double dirDelta = angularDelta(existing.direction, direction);
				if (Math.abs(existing.windSpeed - windSpeed) < 0.01 && Math.abs(existing.targetWindSpeed - targetWindSpeed) < 0.01 && Math.abs(existing.turbulence - turbulence) < 0.005
						&& Math.abs(dirDelta) < 0.5 && Math.abs(existing.stormIntensity - stormIntensity) < 0.01 && Math.abs(existing.moistureStore - moistureStore) < 0.01) {
					return;
				}
			}

			cells.put(key, new CellData(zoneX, zoneZ, windSpeed, targetWindSpeed, turbulence, direction, stormIntensity, moistureStore, phase));
			dirtyFlag = true;
		}

		private CellData get(int zoneX, int zoneZ) {
			long key = (((long) zoneX) << 32) ^ (zoneZ & 0xffffffffL);
			return cells.get(key);
		}

		private Iterable<CellData> all() {
			return cells.values();
		}

		void setFlowDirection(float flowDirection) {
			if (this.flowDirection != null && Math.abs(angularDelta(this.flowDirection, flowDirection)) < 0.5f) return;
			this.flowDirection = (double) flowDirection;
			dirtyFlag = true;
		}

		void flushIfDirty() {
			if (dirtyFlag) {
				setDirty();
				dirtyFlag = false;
			}
		}

		void remove(int zoneX, int zoneZ) {
			long key = (((long) zoneX) << 32) ^ (zoneZ & 0xffffffffL);
			if (cells.remove(key) != null) {
				dirtyFlag = true;
			}
		}

		private record CellData(int zoneX, int zoneZ, double windSpeed, double targetWindSpeed, double turbulence, double direction, double stormIntensity, double moistureStore, double phase) {
		}
	}
}
