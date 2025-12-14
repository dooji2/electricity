package com.dooji.renderix;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RenderixLoader {
	private RenderixLoader() {
	}

	public static RenderixMesh readObj(InputStream stream) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			List<float[]> positions = new ArrayList<>();
			List<float[]> normals = new ArrayList<>();
			List<float[]> texCoords = new ArrayList<>();

			List<RenderixFace> faces = new ArrayList<>();
			List<String> materialLibraries = new ArrayList<>();

			String currentGroup = "default";
			String currentMaterial = null;

			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;

				if (line.startsWith("v ")) {
					positions.add(parseFloats(line.substring(1), 3));
				} else if (line.startsWith("vn ")) {
					normals.add(parseFloats(line.substring(2), 3));
				} else if (line.startsWith("vt ")) {
					texCoords.add(parseFloats(line.substring(2), 2));
				} else {
					String[] tokens = line.split("\\s+");
					String keyword = tokens[0];

					switch (keyword) {
						case "f" -> {
							RenderixFace face = parseFace(tokens, positions.size(), texCoords.size(), normals.size(), currentGroup, currentMaterial);
							if (face != null) faces.add(face);
						}
						case "g", "o" -> currentGroup = tokens.length > 1 ? tokens[1] : "default";
						case "usemtl" -> currentMaterial = tokens.length > 1 ? tokens[1] : null;
						case "mtllib" -> {
							if (tokens.length > 1) {
								materialLibraries.addAll(Arrays.asList(tokens).subList(1, tokens.length));
							}
						}
						default -> {
						}
					}
				}
			}

			return new RenderixMesh(positions, normals, texCoords, faces, materialLibraries);
		}
	}

	private static RenderixFace parseFace(String[] tokens, int positionCount, int texCoordCount, int normalCount, String groupName, String materialName) {
		int vertexCount = tokens.length - 1;
		if (vertexCount < 3) return null;

		int[] positions = new int[vertexCount];
		int[] texCoords = new int[vertexCount];
		int[] normals = new int[vertexCount];
		Arrays.fill(texCoords, -1);
		Arrays.fill(normals, -1);

		for (int i = 0; i < vertexCount; i++) {
			String[] parts = tokens[i + 1].split("/");
			try {
				int positionIndex = resolveIndex(parts[0], positionCount);
				if (positionIndex < 0 || positionIndex >= positionCount) return null;
				positions[i] = positionIndex;
				if (parts.length > 1 && !parts[1].isEmpty()) {
					int texIndex = resolveIndex(parts[1], texCoordCount);
					if (texIndex >= 0 && texIndex < texCoordCount) {
						texCoords[i] = texIndex;
					}
				}

				if (parts.length > 2 && !parts[2].isEmpty()) {
					int normalIndex = resolveIndex(parts[2], normalCount);
					if (normalIndex >= 0 && normalIndex < normalCount) {
						normals[i] = normalIndex;
					}
				}
			} catch (NumberFormatException | IndexOutOfBoundsException e) {
				return null;
			}
		}

		return new RenderixFace(positions, texCoords, normals, groupName, materialName);
	}

	private static float[] parseFloats(String body, int minimumSize) {
		String[] raw = body.trim().split("\\s+");
		int length = Math.max(raw.length, minimumSize);
		float[] values = new float[length];

		for (int i = 0; i < raw.length && i < length; i++) {
			values[i] = parseFloatSafe(raw[i]);
		}

		return values;
	}

	private static float parseFloatSafe(String text) {
		try {
			return Float.parseFloat(text);
		} catch (NumberFormatException e) {
			return 0.0f;
		}
	}

	private static int resolveIndex(String token, int size) {
		int raw = Integer.parseInt(token);
		return raw > 0 ? raw - 1 : size + raw;
	}
}
