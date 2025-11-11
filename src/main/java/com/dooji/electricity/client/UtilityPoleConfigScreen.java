package com.dooji.electricity.client;

import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.network.payloads.UpdateUtilityPoleConfigPayload;
import javax.annotation.Nonnull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class UtilityPoleConfigScreen extends Screen {
	private EditBox offsetXField;
	private EditBox offsetYField;
	private EditBox offsetZField;
	private EditBox yawField;
	private EditBox pitchField;
	private Button doneButton;
	private Button cancelButton;
	private final BlockPos blockPos;
	private final UtilityPoleBlockEntity blockEntity;

	public UtilityPoleConfigScreen(BlockPos blockPos) {
		super(Component.literal("Utility Pole Configuration"));
		this.blockPos = blockPos;

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			if (minecraft.level.getBlockEntity(blockPos) instanceof UtilityPoleBlockEntity entity) {
				this.blockEntity = entity;
			} else {
				this.blockEntity = null;
			}
		} else {
			this.blockEntity = null;
		}
	}

	@Override
	protected void init() {
		super.init();

		int centerX = this.width / 2;
		int centerY = this.height / 2;
		int fieldWidth = 60;
		int fieldHeight = 20;
		int spacing = 24;

		this.offsetXField = new EditBox(this.font, centerX - 100, centerY - 60, fieldWidth, fieldHeight, Component.literal("Offset X"));
		this.offsetXField.setValue(blockEntity != null ? String.valueOf(blockEntity.getOffsetX()) : "0.0");
		this.addWidget(this.offsetXField);

		this.offsetYField = new EditBox(this.font, centerX - 100, centerY - 60 + spacing, fieldWidth, fieldHeight, Component.literal("Offset Y"));
		this.offsetYField.setValue(blockEntity != null ? String.valueOf(blockEntity.getOffsetY()) : "0.0");
		this.addWidget(this.offsetYField);

		this.offsetZField = new EditBox(this.font, centerX - 100, centerY - 60 + spacing * 2, fieldWidth, fieldHeight, Component.literal("Offset Z"));
		this.offsetZField.setValue(blockEntity != null ? String.valueOf(blockEntity.getOffsetZ()) : "0.0");
		this.addWidget(this.offsetZField);

		this.yawField = new EditBox(this.font, centerX + 20, centerY - 60, fieldWidth, fieldHeight, Component.literal("Yaw (deg)"));
		this.yawField.setValue(blockEntity != null ? formatDegrees(blockEntity.getYaw()) : "0.0");
		this.addWidget(this.yawField);

		this.pitchField = new EditBox(this.font, centerX + 20, centerY - 60 + spacing, fieldWidth, fieldHeight, Component.literal("Pitch (deg)"));
		this.pitchField.setValue(blockEntity != null ? formatDegrees(blockEntity.getPitch()) : "0.0");
		this.addWidget(this.pitchField);

		this.doneButton = Button.builder(Component.literal("Done"), button -> {
			this.saveAndClose();
		}).bounds(centerX - 100, centerY + 40, 80, 20).build();

		this.cancelButton = Button.builder(Component.literal("Cancel"), button -> {
			this.onClose();
		}).bounds(centerX + 20, centerY + 40, 80, 20).build();

		this.addRenderableWidget(this.doneButton);
		this.addRenderableWidget(this.cancelButton);
	}

	private void saveAndClose() {
		try {
			float offsetX = Float.parseFloat(this.offsetXField.getValue());
			float offsetY = Float.parseFloat(this.offsetYField.getValue());
			float offsetZ = Float.parseFloat(this.offsetZField.getValue());
			float yaw = parseDegrees(this.yawField);
			float pitch = parseDegrees(this.pitchField);

			UpdateUtilityPoleConfigPayload payload = new UpdateUtilityPoleConfigPayload(this.blockPos, offsetX, offsetY, offsetZ, yaw, pitch);

			ElectricityNetworking.INSTANCE.sendToServer(payload);
		} catch (NumberFormatException e) {
		}

		this.onClose();
	}

	@Override
	public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(guiGraphics);
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		int centerX = this.width / 2;
		int centerY = this.height / 2;

		guiGraphics.drawString(this.font, "Offset X:", centerX - 160, centerY - 55, 0xFFFFFF);
		guiGraphics.drawString(this.font, "Offset Y:", centerX - 160, centerY - 55 + 24, 0xFFFFFF);
		guiGraphics.drawString(this.font, "Offset Z:", centerX - 160, centerY - 55 + 48, 0xFFFFFF);
		guiGraphics.drawString(this.font, "Yaw (deg):", centerX - 40, centerY - 55, 0xFFFFFF);
		guiGraphics.drawString(this.font, "Pitch (deg):", centerX - 40, centerY - 55 + 24, 0xFFFFFF);

		if (this.offsetXField != null) this.offsetXField.render(guiGraphics, mouseX, mouseY, partialTick);
		if (this.offsetYField != null) this.offsetYField.render(guiGraphics, mouseX, mouseY, partialTick);
		if (this.offsetZField != null) this.offsetZField.render(guiGraphics, mouseX, mouseY, partialTick);
		if (this.yawField != null) this.yawField.render(guiGraphics, mouseX, mouseY, partialTick);
		if (this.pitchField != null) this.pitchField.render(guiGraphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.offsetXField != null && this.offsetXField.keyPressed(keyCode, scanCode, modifiers)) return true;
		if (this.offsetYField != null && this.offsetYField.keyPressed(keyCode, scanCode, modifiers)) return true;
		if (this.offsetZField != null && this.offsetZField.keyPressed(keyCode, scanCode, modifiers)) return true;
		if (this.yawField != null && this.yawField.keyPressed(keyCode, scanCode, modifiers)) return true;
		if (this.pitchField != null && this.pitchField.keyPressed(keyCode, scanCode, modifiers)) return true;

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (this.offsetXField != null && this.offsetXField.charTyped(codePoint, modifiers)) return true;
		if (this.offsetYField != null && this.offsetYField.charTyped(codePoint, modifiers)) return true;
		if (this.offsetZField != null && this.offsetZField.charTyped(codePoint, modifiers)) return true;
		if (this.yawField != null && this.yawField.charTyped(codePoint, modifiers)) return true;
		if (this.pitchField != null && this.pitchField.charTyped(codePoint, modifiers)) return true;

		return super.charTyped(codePoint, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static String formatDegrees(float radians) {
		return String.format("%.2f", Math.toDegrees(radians));
	}

	private static float parseDegrees(EditBox field) {
		return (float) Math.toRadians(Float.parseFloat(field.getValue()));
	}
}
