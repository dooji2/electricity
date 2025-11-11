package com.dooji.electricity.client.screen;

import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.block.WindTurbineBlockEntity;
import com.dooji.electricity.client.render.obj.ObjRaycaster;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class PowerInfoScreen extends Screen {
	private final BlockPos targetPos;

	public PowerInfoScreen(BlockPos targetPos) {
		super(Component.literal("Power Diagnostics"));
		this.targetPos = targetPos.immutable();
	}

	@Override
	public void tick() {
		super.tick();
		if (minecraft == null || minecraft.level == null || minecraft.level.getBlockEntity(targetPos) == null) {
			onClose();
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics);
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		Font font = this.font;
		int centerX = this.width / 2;
		int y = this.height / 4;

		guiGraphics.drawCenteredString(font, title, centerX, y, 0xFFFFFF);
		y += font.lineHeight + 8;

		for (Component line : collectInfoLines()) {
			guiGraphics.drawCenteredString(font, line, centerX, y, 0xC6C6C6);
			y += font.lineHeight + 2;
		}
	}

	private List<Component> collectInfoLines() {
		List<Component> lines = new ArrayList<>();
		if (minecraft == null || minecraft.level == null) {
			lines.add(Component.literal("World unavailable"));
			return lines;
		}

		var blockEntity = minecraft.level.getBlockEntity(targetPos);
		if (blockEntity == null) {
			lines.add(Component.literal("Block removed"));
			return lines;
		}

		String formatted = ObjRaycaster.getPowerDisplayText(blockEntity);
		if (formatted != null) {
			for (String line : formatted.split("\n")) {
				lines.add(Component.literal(line));
			}
		} else {
			lines.add(Component.literal(blockEntity.getBlockState().getBlock().getName().getString()));
			lines.add(Component.literal("No power data available"));
		}

		if (blockEntity instanceof WindTurbineBlockEntity turbine) {
			lines.add(formatValue("Wind Speed", "%.1f m/s", turbine.getWindSpeed()));
			lines.add(formatValue("Rotation", "%.1f° / %.1f°", turbine.getRotation1(), turbine.getRotation2()));
		} else if (blockEntity instanceof UtilityPoleBlockEntity pole) {
			lines.add(formatValue("Offsets", "(%.2f, %.2f, %.2f)", pole.getOffsetX(), pole.getOffsetY(), pole.getOffsetZ()));
			lines.add(formatValue("Yaw / Pitch (deg)", "(%.1f, %.1f)", Math.toDegrees(pole.getYaw()), Math.toDegrees(pole.getPitch())));
		}

		return lines;
	}

	private MutableComponent formatValue(String label, String pattern, Object... values) {
		return Component.literal(label + ": " + String.format(pattern, values));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
