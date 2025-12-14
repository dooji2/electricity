package com.dooji.renderix;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RenderixSplitter {
	private static final String DEFAULT_GROUP = "default";

	private RenderixSplitter() {
	}

	public static Map<String, RenderixMesh> splitByObject(RenderixMesh mesh) {
		boolean hasNamedGroup = false;
		for (RenderixFace face : mesh.facesView()) {
			if (face.groupName() != null && !DEFAULT_GROUP.equals(face.groupName())) {
				hasNamedGroup = true;
				break;
			}
		}

		if (!hasNamedGroup) {
			return Map.of();
		}

		Map<String, List<RenderixFace>> grouped = new LinkedHashMap<>();
		for (RenderixFace face : mesh.facesView()) {
			String key = normalize(face.groupName());
			grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(face);
		}

		return buildMeshes(mesh, grouped);
	}

	public static Map<String, RenderixMesh> splitByMaterial(RenderixMesh mesh) {
		Map<String, List<RenderixFace>> grouped = new LinkedHashMap<>();
		for (RenderixFace face : mesh.facesView()) {
			String key = normalize(face.materialName());
			grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(face);
		}

		return buildMeshes(mesh, grouped);
	}

	private static Map<String, RenderixMesh> buildMeshes(RenderixMesh mesh, Map<String, List<RenderixFace>> groupedFaces) {
		Map<String, RenderixMesh> result = new LinkedHashMap<>();
		for (Map.Entry<String, List<RenderixFace>> entry : groupedFaces.entrySet()) {
			result.put(entry.getKey(), mesh.withFaces(entry.getValue()));
		}

		return result;
	}

	private static String normalize(String value) {
		if (value == null || value.isEmpty()) return DEFAULT_GROUP;
		return value;
	}
}
