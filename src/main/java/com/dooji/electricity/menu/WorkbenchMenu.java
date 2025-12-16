package com.dooji.electricity.menu;

import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.recipe.WorkbenchRecipe;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;

public class WorkbenchMenu extends AbstractContainerMenu {
	private final TransientCraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
	private final ResultContainer resultSlots = new ResultContainer();
	private final ContainerLevelAccess access;

	public WorkbenchMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
		this(id, inventory, ContainerLevelAccess.create(inventory.player.level(), buf.readBlockPos()));
	}

	public WorkbenchMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		super(Electricity.WORKBENCH_MENU.get(), id);
		this.access = access;
		this.addSlot(new Slot(resultSlots, 0, 124, 33) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack stack) {
				craftResult(player, stack);
			}
		});

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				int x = 30 + col * 18;
				int y = 15 + row * 18;
				this.addSlot(new Slot(craftSlots, col + row * 3, x, y));
			}
		}

		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 9; ++col) {
				int x = 13 + col * 17;
				int y = 93 + row * 17;
				this.addSlot(new Slot(inventory, col + row * 9 + 9, x, y));
			}
		}

		int[] hotbarX = {30, 13, 47, 64, 81, 98, 115, 132, 149};
		for (int hotbar = 0; hotbar < 9; ++hotbar) {
			this.addSlot(new Slot(inventory, hotbar, hotbarX[hotbar], 144));
		}

		this.slotChangedCraftingGrid();
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		this.slotChangedCraftingGrid();
	}

	private void slotChangedCraftingGrid() {
		access.execute((level, pos) -> {
			var recipe = level.getRecipeManager().getRecipeFor(WorkbenchRecipe.TYPE, craftSlots, level);
			if (recipe.isPresent()) {
				resultSlots.setItem(0, recipe.get().assemble(craftSlots, level.registryAccess()));
			} else {
				resultSlots.setItem(0, ItemStack.EMPTY);
			}
		});
	}

	private void craftResult(Player player, ItemStack stack) {
		access.execute((level, pos) -> {
			stack.onCraftedBy(level, player, stack.getCount());
			Optional<WorkbenchRecipe> recipe = level.getRecipeManager().getRecipeFor(WorkbenchRecipe.TYPE, craftSlots, level);
			if (recipe.isEmpty()) return;

			NonNullList<ItemStack> remaining = recipe.get().getRemainingItems(craftSlots);
			for (int i = 0; i < remaining.size(); i++) {
				ItemStack ingredient = craftSlots.getItem(i);
				ItemStack leftover = remaining.get(i);

				if (!ingredient.isEmpty()) {
					craftSlots.removeItem(i, 1);
					ingredient = craftSlots.getItem(i);
				}

				if (!leftover.isEmpty()) {
					if (ingredient.isEmpty()) {
						craftSlots.setItem(i, leftover);
					} else if (!player.getInventory().add(leftover)) {
						player.drop(leftover, false);
					}
				}
			}
			slotChangedCraftingGrid();
		});
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, Electricity.WORKBENCH_BLOCK.get());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack itemstack = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack slotStack = slot.getItem();
			itemstack = slotStack.copy();
			if (index == 0) {
				if (!this.moveItemStackTo(slotStack, 10, 46, true)) {
					return ItemStack.EMPTY;
				}

				slot.onQuickCraft(slotStack, itemstack);
			} else if (index >= 10 && index < 46) {
				if (!this.moveItemStackTo(slotStack, 1, 10, false)) {
					if (index < 37) {
						if (!this.moveItemStackTo(slotStack, 37, 46, false)) {
							return ItemStack.EMPTY;
						}
					} else if (!this.moveItemStackTo(slotStack, 10, 37, false)) {
						return ItemStack.EMPTY;
					}
				}
			} else if (!this.moveItemStackTo(slotStack, 10, 46, false)) {
				return ItemStack.EMPTY;
			}

			if (slotStack.isEmpty()) {
				slot.set(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}

			if (slotStack.getCount() == itemstack.getCount()) {
				return ItemStack.EMPTY;
			}

			slot.onTake(player, slotStack);
		}

		return itemstack;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.access.execute((level, pos) -> this.clearContainer(player, this.craftSlots));
	}
}
