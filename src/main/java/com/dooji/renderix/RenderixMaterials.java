package com.dooji.renderix;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class RenderixMaterials {
	private RenderixMaterials() {
	}

	public static List<RenderixMaterial> read(InputStream stream) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			List<RenderixMaterial> materials = new ArrayList<>();
			String currentName = null;
			String diffuseMap = null;

			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;

				if (line.startsWith("newmtl")) {
					if (currentName != null) {
						materials.add(new RenderixMaterial(currentName, diffuseMap));
					}

					currentName = parseName(line);
					diffuseMap = null;
				} else if (line.startsWith("map_Kd")) {
					diffuseMap = parseName(line);
				}
			}

			if (currentName != null) {
				materials.add(new RenderixMaterial(currentName, diffuseMap));
			}

			return materials;
		}
	}

	private static String parseName(String line) {
		int firstSpace = line.indexOf(' ');
		if (firstSpace < 0 || firstSpace + 1 >= line.length()) return "";
		return line.substring(firstSpace + 1).trim();
	}
}
