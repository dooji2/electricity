package com.dooji.electricity.client;

import com.dooji.electricity.block.UtilityPoleBlockEntity;
import com.dooji.electricity.main.network.ElectricityNetworking;
import com.dooji.electricity.main.network.payloads.UpdateUtilityPoleConfigPayload;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class UtilityPoleConfigScreen extends Screen {
	private Button doneButton;
	private Button cancelButton;
	private final BlockPos blockPos;
	private float offsetX = 0.0f;
	private float offsetY = 0.0f;
	private float offsetZ = 0.0f;
	private float yaw = 0.0f;
	private float pitch = 0.0f;

	public UtilityPoleConfigScreen(BlockPos blockPos) {
		super(Component.literal("Utility Pole Configuration"));
		this.blockPos = blockPos;

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null && minecraft.level.getBlockEntity(blockPos) instanceof UtilityPoleBlockEntity entity) {
			this.offsetX = entity.getOffsetX();
			this.offsetY = entity.getOffsetY();
			this.offsetZ = entity.getOffsetZ();
			this.yaw = entity.getYaw();
			this.pitch = entity.getPitch();
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

		this.addRenderableWidget(createFloatField(centerX - 100, centerY - 60, fieldWidth, fieldHeight, Component.literal("Offset X"), offsetX, value -> this.offsetX = value));
		this.addRenderableWidget(createFloatField(centerX - 100, centerY - 60 + spacing, fieldWidth, fieldHeight, Component.literal("Offset Y"), offsetY, value -> this.offsetY = value));
		this.addRenderableWidget(createFloatField(centerX - 100, centerY - 60 + spacing * 2, fieldWidth, fieldHeight, Component.literal("Offset Z"), offsetZ, value -> this.offsetZ = value));
		this.addRenderableWidget(createDegreeField(centerX + 20, centerY - 60, fieldWidth, fieldHeight, Component.literal("Yaw (deg)"), yaw, value -> this.yaw = value));
		this.addRenderableWidget(createDegreeField(centerX + 20, centerY - 60 + spacing, fieldWidth, fieldHeight, Component.literal("Pitch (deg)"), pitch, value -> this.pitch = value));

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
		UpdateUtilityPoleConfigPayload payload = new UpdateUtilityPoleConfigPayload(this.blockPos, offsetX, offsetY, offsetZ, yaw, pitch);
		ElectricityNetworking.INSTANCE.sendToServer(payload);
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

	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static String formatDegrees(float radians) {
		return String.format("%.2f", Math.toDegrees(radians));
	}

	private EditBox createFloatField(int x, int y, int width, int height, Component label, float initialValue, Consumer<Float> setter) {
		EditBox field = new EditBox(this.font, x, y, width, height, label);
		field.setValue(Float.toString(initialValue));
		field.setResponder(value -> {
			Float parsed = tryParseFloat(value);
			if (parsed != null) {
				setter.accept(parsed);
			}
		});
		return field;
	}

	private EditBox createDegreeField(int x, int y, int width, int height, Component label, float initialRadians, Consumer<Float> setter) {
		EditBox field = new EditBox(this.font, x, y, width, height, label);
		field.setValue(formatDegrees(initialRadians));
		field.setResponder(value -> {
			Float parsed = tryParseFloat(value);
			if (parsed != null) {
				setter.accept((float) Math.toRadians(parsed));
			}
		});
		return field;
	}

	private static Float tryParseFloat(String value) {
		try {
			return Float.parseFloat(value);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}
}
