package com.dooji.electricity.main.registry;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public record ObjBlockDefinition(Block block, ResourceLocation model, List<String> insulators) {
}
