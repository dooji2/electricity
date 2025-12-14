package com.dooji.renderix;

public final class RenderixMaterial {
	private final String name;
	private final String diffuseMap;

	public RenderixMaterial(String name, String diffuseMap) {
		this.name = name;
		this.diffuseMap = diffuseMap;
	}

	public String name() {
		return name;
	}

	public String diffuseMap() {
		return diffuseMap;
	}
}
