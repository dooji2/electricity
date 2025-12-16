package com.dooji.electricity.recipe;

import com.dooji.electricity.main.Electricity;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

public class WorkbenchRecipe implements Recipe<CraftingContainer> {
	public static final ResourceLocation ID = new ResourceLocation(Electricity.MOD_ID, "workbench");
	public static final RecipeType<WorkbenchRecipe> TYPE = new RecipeType<>() {
		public String toString() {
			return ID.toString();
		}
	};

	private final ShapedRecipe inner;

	public WorkbenchRecipe(ShapedRecipe inner) {
		this.inner = inner;
	}

	@Override
	public boolean matches(CraftingContainer container, Level level) {
		return inner.matches(container, level);
	}

	@Override
	public ItemStack assemble(CraftingContainer container, net.minecraft.core.RegistryAccess registryAccess) {
		return inner.assemble(container, registryAccess);
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return inner.canCraftInDimensions(width, height);
	}

	@Override
	public ItemStack getResultItem(net.minecraft.core.RegistryAccess registryAccess) {
		return inner.getResultItem(registryAccess);
	}

	@Override
	public ResourceLocation getId() {
		return inner.getId();
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return Electricity.WORKBENCH_RECIPE_SERIALIZER.get();
	}

	@Override
	public RecipeType<?> getType() {
		return TYPE;
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	public static class Serializer implements RecipeSerializer<WorkbenchRecipe> {
		private final ShapedRecipe.Serializer delegate = new ShapedRecipe.Serializer();

		@Override
		public WorkbenchRecipe fromJson(ResourceLocation id, JsonObject json) {
			return new WorkbenchRecipe(delegate.fromJson(id, json));
		}

		@Override
		public WorkbenchRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
			return new WorkbenchRecipe(delegate.fromNetwork(id, buf));
		}

		@Override
		public void toNetwork(FriendlyByteBuf buf, WorkbenchRecipe recipe) {
			delegate.toNetwork(buf, recipe.inner);
		}
	}
}
