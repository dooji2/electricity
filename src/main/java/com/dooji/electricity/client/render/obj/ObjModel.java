package com.dooji.electricity.client.render.obj;

import com.dooji.electricity.main.Electricity;
import de.javagl.obj.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjModel {
	private static final Logger LOGGER = LoggerFactory.getLogger("electricity");
	public final Map<String, ObjGroup> groups = new HashMap<>();
	public final Map<String, ObjMaterial> materials = new HashMap<>();

	public static class ObjGroup {
		public final String name;
		public final List<Vector3f> vertices = new ArrayList<>();
		public final List<Vector3f> normals = new ArrayList<>();
		public final List<Float> texCoords = new ArrayList<>();
		public final String materialName;

		public ObjGroup(String name, String materialName) {
			this.name = name;
			this.materialName = materialName;
		}
	}

	public static class ObjMaterial {
		public final String name;
		public final ResourceLocation texture;

		public ObjMaterial(String name, ResourceLocation texture) {
			this.name = name;
			this.texture = texture;
		}
	}

	public static ObjModel loadFromResource(ResourceLocation resourceLocation) {
		try {
			InputStream stream = Minecraft.getInstance().getResourceManager().getResource(resourceLocation).orElseThrow().open();
			return loadFromStream(stream, resourceLocation);
		} catch (IOException e) {
			LOGGER.error("Failed to load OBJ model: {}", resourceLocation, e);
			return null;
		}
	}

	public static ObjModel loadFromStream(InputStream stream) {
		return loadFromStream(stream, null);
	}

	public static ObjModel loadFromStream(InputStream stream, ResourceLocation resourceLocation) {
		ObjModel model = new ObjModel();
		try {
			Obj obj = ObjReader.read(stream);
			Obj renderableObj = ObjUtils.convertToRenderable(obj);

			model.loadMaterials(obj, resourceLocation);

			Map<String, Obj> objectGroups = ObjSplitting.splitByGroups(renderableObj);

			if (!objectGroups.isEmpty()) {
				for (Map.Entry<String, Obj> entry : objectGroups.entrySet()) {
					String objectName = entry.getKey();
					Obj objectObj = entry.getValue();

					Map<String, Obj> materialGroups = ObjSplitting.splitByMaterialGroups(objectObj);

					for (Map.Entry<String, Obj> materialEntry : materialGroups.entrySet()) {
						String materialName = materialEntry.getKey();
						Obj materialObj = materialEntry.getValue();

						if (materialObj.getNumFaces() == 0) continue;

						ObjGroup group = new ObjGroup(objectName, materialName);
						model.addDeindexedVertices(materialObj, group);
						model.groups.put(objectName + "_" + materialName, group);
					}
				}
			} else {
				Map<String, Obj> materialGroups = ObjSplitting.splitByMaterialGroups(renderableObj);

				for (Map.Entry<String, Obj> entry : materialGroups.entrySet()) {
					String materialName = entry.getKey();
					Obj materialObj = entry.getValue();

					if (materialObj.getNumFaces() == 0) continue;

					ObjGroup group = new ObjGroup("default", materialName);
					model.addDeindexedVertices(materialObj, group);
					model.groups.put(materialName, group);
				}
			}

		} catch (IOException e) {
			LOGGER.error("Error parsing OBJ file", e);
			return null;
		}

		return model;
	}

	private void loadMaterials(Obj obj, ResourceLocation resourceLocation) {
		String namespace = resourceLocation != null ? resourceLocation.getNamespace() : Electricity.MOD_ID;
		String resourcePath = resourceLocation != null ? resourceLocation.getPath() : "";
		String baseDirectory = "";
		int slashIndex = resourcePath.lastIndexOf('/');
		if (slashIndex >= 0) {
			baseDirectory = resourcePath.substring(0, slashIndex + 1);
		}

		for (String mtlFileName : obj.getMtlFileNames()) {
			try {
				String relativePath = baseDirectory.isEmpty() ? mtlFileName : baseDirectory + mtlFileName;
				ResourceLocation mtlLocation = new ResourceLocation(namespace, relativePath);
				InputStream stream = Minecraft.getInstance().getResourceManager().getResource(mtlLocation).orElseThrow().open();

				List<Mtl> mtls = MtlReader.read(stream);
				for (Mtl mtl : mtls) {
					ResourceLocation texture = null;
					if (mtl.getMapKd() != null && !mtl.getMapKd().isEmpty()) {
						texture = new ResourceLocation("electricity", "textures/block/" + mtl.getMapKd());
					}
					materials.put(mtl.getName(), new ObjMaterial(mtl.getName(), texture));
				}

				stream.close();
			} catch (IOException e) {
				LOGGER.error("Failed to load MTL file: {}", mtlFileName, e);
			}
		}
	}

	private void addDeindexedVertices(Obj obj, ObjGroup group) {
		for (int i = 0; i < obj.getNumFaces(); i++) {
			ObjFace face = obj.getFace(i);
			int numVertices = face.getNumVertices();
			if (numVertices < 3) continue;

			Vector3f fallbackNormal = face.containsNormalIndices() ? null : calculateFaceNormal(obj, face);

			for (int j = 0; j < numVertices; j++) {
				addDeindexedVertex(obj, face, j, group, fallbackNormal);
			}

			if (numVertices == 3) {
				addDeindexedVertex(obj, face, 2, group, fallbackNormal);
			}
		}
	}

	private void addDeindexedVertex(Obj obj, ObjFace face, int vertexIndexInFace, ObjGroup group, Vector3f fallbackNormal) {
		int posIndex = face.getVertexIndex(vertexIndexInFace);
		int normalIndex = face.containsNormalIndices() ? face.getNormalIndex(vertexIndexInFace) : -1;
		int texCoordIndex = face.containsTexCoordIndices() ? face.getTexCoordIndex(vertexIndexInFace) : -1;

		FloatTuple vertex = obj.getVertex(posIndex);
		group.vertices.add(new Vector3f(vertex.getX(), vertex.getY(), vertex.getZ()));

		if (normalIndex != -1) {
			FloatTuple normal = obj.getNormal(normalIndex);
			group.normals.add(new Vector3f(normal.getX(), normal.getY(), normal.getZ()));
		} else {
			group.normals.add(fallbackNormal != null ? new Vector3f(fallbackNormal) : new Vector3f(0, 1, 0));
		}

		if (texCoordIndex != -1) {
			FloatTuple texCoord = obj.getTexCoord(texCoordIndex);
			group.texCoords.add(texCoord.getX());
			group.texCoords.add(1.0f - texCoord.getY());
		} else {
			group.texCoords.add(0.0f);
			group.texCoords.add(0.0f);
		}
	}

	private Vector3f calculateFaceNormal(Obj obj, ObjFace face) {
		if (face.getNumVertices() < 3) return new Vector3f(0, 1, 0);

		FloatTuple v0 = obj.getVertex(face.getVertexIndex(0));
		FloatTuple v1 = obj.getVertex(face.getVertexIndex(1));
		FloatTuple v2 = obj.getVertex(face.getVertexIndex(2));

		Vector3f edge1 = new Vector3f(v1.getX() - v0.getX(), v1.getY() - v0.getY(), v1.getZ() - v0.getZ());

		Vector3f edge2 = new Vector3f(v2.getX() - v0.getX(), v2.getY() - v0.getY(), v2.getZ() - v0.getZ());

		Vector3f normal = new Vector3f();
		edge1.cross(edge2, normal);
		normal.normalize();

		return normal;
	}

	public static class BoundingBox {
		public final Vector3f min;
		public final Vector3f max;
		public final Vector3f center;
		public final Vector3f size;

		public BoundingBox(Vector3f min, Vector3f max) {
			this.min = min;
			this.max = max;
			this.center = new Vector3f((min.x + max.x) / 2.0f, (min.y + max.y) / 2.0f, (min.z + max.z) / 2.0f);
			this.size = new Vector3f(max.x - min.x, max.y - min.y, max.z - min.z);
		}
	}

	public static class OrientedBoundingBox {
		public final Vector3f[] corners;
		public final Vector3f center;
		public final Vector3f size;

		public OrientedBoundingBox(Vector3f center, Vector3f size, float yaw, float pitch) {
			this.center = center;
			this.size = size;
			this.corners = calculateOrientedCorners(center, size, yaw, pitch);
		}

		public OrientedBoundingBox(Vector3f center, Vector3f size) {
			this(center, size, 0.0f, 0.0f);
		}

		private static Vector3f[] calculateOrientedCorners(Vector3f center, Vector3f size, float yaw, float pitch) {
			Vector3f[] corners = new Vector3f[8];
			float halfWidth = size.x / 2.0f;
			float halfHeight = size.y / 2.0f;
			float halfDepth = size.z / 2.0f;

			Vector3f[] localCorners = {new Vector3f(-halfWidth, -halfHeight, -halfDepth), new Vector3f(halfWidth, -halfHeight, -halfDepth), new Vector3f(halfWidth, -halfHeight, halfDepth),
					new Vector3f(-halfWidth, -halfHeight, halfDepth), new Vector3f(-halfWidth, halfHeight, -halfDepth), new Vector3f(halfWidth, halfHeight, -halfDepth),
					new Vector3f(halfWidth, halfHeight, halfDepth), new Vector3f(-halfWidth, halfHeight, halfDepth)};

			for (int i = 0; i < 8; i++) {
				Vector3f corner = new Vector3f(localCorners[i]);

				if (yaw != 0) {
					float cosYaw = (float) Math.cos(Math.toRadians(yaw));
					float sinYaw = (float) Math.sin(Math.toRadians(yaw));
					float x = corner.x * cosYaw + corner.z * sinYaw;
					float z = -corner.x * sinYaw + corner.z * cosYaw;
					corner.x = x;
					corner.z = z;
				}

				if (pitch != 0) {
					float cosPitch = (float) Math.cos(Math.toRadians(pitch));
					float sinPitch = (float) Math.sin(Math.toRadians(pitch));
					float y = corner.y * cosPitch - corner.z * sinPitch;
					float z = corner.y * sinPitch + corner.z * cosPitch;
					corner.y = y;
					corner.z = z;
				}

				corners[i] = new Vector3f(corner).add(center);
			}

			return corners;
		}
	}

	public BoundingBox getBoundingBox(String groupName) {
		ObjGroup group = groups.get(groupName);
		if (group == null || group.vertices.isEmpty()) return null;

		Vector3f min = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
		Vector3f max = new Vector3f(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);

		for (Vector3f vertex : group.vertices) {
			min.x = Math.min(min.x, vertex.x);
			min.y = Math.min(min.y, vertex.y);
			min.z = Math.min(min.z, vertex.z);
			max.x = Math.max(max.x, vertex.x);
			max.y = Math.max(max.y, vertex.y);
			max.z = Math.max(max.z, vertex.z);
		}

		return new BoundingBox(min, max);
	}

	public Map<String, BoundingBox> getAllBoundingBoxes() {
		Map<String, BoundingBox> boundingBoxes = new HashMap<>();
		for (String groupName : groups.keySet()) {
			BoundingBox bbox = getBoundingBox(groupName);
			if (bbox != null) {
				boundingBoxes.put(groupName, bbox);
			}
		}
		return boundingBoxes;
	}
}
