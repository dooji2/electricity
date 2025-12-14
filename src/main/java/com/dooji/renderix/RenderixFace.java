package com.dooji.renderix;

import java.util.Arrays;

public final class RenderixFace {
	private final int[] positionIndices;
	private final int[] texCoordIndices;
	private final int[] normalIndices;

	private final boolean hasTexCoords;
	private final boolean hasNormals;

	private final String groupName;
	private final String materialName;

	public RenderixFace(int[] positionIndices, int[] texCoordIndices, int[] normalIndices, String groupName, String materialName) {
		this.positionIndices = Arrays.copyOf(positionIndices, positionIndices.length);
		this.texCoordIndices = texCoordIndices != null ? Arrays.copyOf(texCoordIndices, texCoordIndices.length) : createFilled(positionIndices.length, -1);
		this.normalIndices = normalIndices != null ? Arrays.copyOf(normalIndices, normalIndices.length) : createFilled(positionIndices.length, -1);
		this.hasTexCoords = allNonNegative(this.texCoordIndices);
		this.hasNormals = allNonNegative(this.normalIndices);
		this.groupName = groupName != null ? groupName : "default";
		this.materialName = materialName;
	}

	public int vertexCount() {
		return positionIndices.length;
	}

	public boolean hasTexCoords() {
		return hasTexCoords;
	}

	public boolean hasNormals() {
		return hasNormals;
	}

	public int positionIndex(int vertexInFace) {
		return positionIndices[vertexInFace];
	}

	public int texCoordIndex(int vertexInFace) {
		return texCoordIndices[vertexInFace];
	}

	public int normalIndex(int vertexInFace) {
		return normalIndices[vertexInFace];
	}

	public String groupName() {
		return groupName;
	}

	public String materialName() {
		return materialName;
	}

	public RenderixFace slice(int... picks) {
		int[] positions = new int[picks.length];
		int[] texCoords = new int[picks.length];
		int[] normals = new int[picks.length];

		for (int i = 0; i < picks.length; i++) {
			int idx = picks[i];
			positions[i] = positionIndices[idx];
			texCoords[i] = texCoordIndices[idx];
			normals[i] = normalIndices[idx];
		}

		return new RenderixFace(positions, texCoords, normals, groupName, materialName);
	}

	private static int[] createFilled(int count, int value) {
		int[] data = new int[count];
		Arrays.fill(data, value);
		return data;
	}

	private static boolean allNonNegative(int[] values) {
		for (int value : values) {
			if (value < 0) return false;
		}

		return values.length > 0;
	}
}
