package com.dooji.electricity.client.wire;

import net.minecraft.world.phys.Vec3;

public class WirePhysics {
	public static final float DEFLECTION_COEFFICIENT = 0.005f;
	public static final float LENGTH_COEFFICIENT = 0.02f;
	public static final float WIRE_SEGMENT_LENGTH = 1.0f;
	public static final float WIRE_RADIUS = 0.025f;

	public static Vec3 calculateDeflectionPoint(Vec3 start, Vec3 end, double progress) {
		Vec3 direction = end.subtract(start);
		double lx = Math.sqrt(direction.x * direction.x + direction.z * direction.z);

		if (lx == 0.0) return start.add(direction.scale(progress));

		double ly = direction.y;
		double alpha = DEFLECTION_COEFFICIENT * (1.0 + LENGTH_COEFFICIENT * lx);
		double a = lx > 0.0 ? (lx - ly / (alpha * lx)) / 2.0 : 0.0;

		double x = lx * progress;
		double y = alpha * (x * x - 2.0 * a * x);

		Vec3 result = start.add(new Vec3((direction.x / lx) * x, y, (direction.z / lx) * x));

		return result;
	}

	public static Vec3 calculateTangent(Vec3 start, Vec3 end, double progress) {
		Vec3 direction = end.subtract(start);
		double lx = Math.sqrt(direction.x * direction.x + direction.z * direction.z);

		if (lx == 0.0) return direction.normalize();

		double ly = direction.y;
		double alpha = DEFLECTION_COEFFICIENT * (1.0 + LENGTH_COEFFICIENT * lx);
		double a = lx > 0.0 ? (lx - ly / (alpha * lx)) / 2.0 : 0.0;

		double x = lx * progress;
		double slope = 2.0 * alpha * (x - a);

		return new Vec3(direction.x / lx, slope, direction.z / lx).normalize();
	}

	public static double calculateWireLength(Vec3 start, Vec3 end) {
		Vec3 direction = end.subtract(start);
		double lx = Math.sqrt(direction.x * direction.x + direction.z * direction.z);

		if (lx == 0.0) return Math.abs(direction.y);

		double ly = direction.y;
		double alpha = DEFLECTION_COEFFICIENT * (1.0 + LENGTH_COEFFICIENT * lx);
		double a = lx > 0.0 ? (lx - ly / (alpha * lx)) / 2.0 : 0.0;

		double length = 0;
		double x = 0;

		while (x < lx) {
			double slope = 2.0 * alpha * (x - a);
			double segmentLength = Math.sqrt(1 + slope * slope) * WIRE_SEGMENT_LENGTH;

			length += segmentLength;
			x += WIRE_SEGMENT_LENGTH;
		}

		return length;
	}

	public static boolean shouldUseDeflection(Vec3 start, Vec3 end) {
		Vec3 direction = end.subtract(start);
		double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
		return horizontalDistance > 0.1;
	}

	public static float getYaw(Vec3 vec) {
		return (float) Math.toDegrees(Math.atan2(vec.x, vec.z));
	}

	public static float getPitch(Vec3 vec) {
		double xz = Math.sqrt(vec.x * vec.x + vec.z * vec.z);
		return (float) Math.toDegrees(Math.atan2(vec.y, xz));
	}
}
