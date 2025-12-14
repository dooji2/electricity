package com.dooji.renderix;

import java.util.ArrayList;
import java.util.List;

public final class RenderixTessellator {
	private RenderixTessellator() {
	}

	public static RenderixMesh toRenderable(RenderixMesh mesh) {
		List<RenderixFace> triangulated = new ArrayList<>();

		for (RenderixFace face : mesh.facesView()) {
			int count = face.vertexCount();
			if (count < 3) continue;
			if (count == 3) {
				triangulated.add(face);
				continue;
			}

			for (int i = 1; i < count - 1; i++) {
				triangulated.add(face.slice(0, i, i + 1));
			}
		}

		return mesh.withFaces(triangulated);
	}
}
