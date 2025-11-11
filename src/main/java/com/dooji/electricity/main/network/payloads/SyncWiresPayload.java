package com.dooji.electricity.main.network.payloads;

import com.dooji.electricity.main.wire.WireConnection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

public record SyncWiresPayload(List<WireConnection> connections) {
	public static void write(SyncWiresPayload message, FriendlyByteBuf buf) {
		buf.writeVarInt(message.connections.size());
		for (WireConnection connection : message.connections) {
			WireConnectionPayload.writeConnection(buf, connection);
		}
	}

	public static SyncWiresPayload read(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<WireConnection> connections = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			connections.add(WireConnectionPayload.readConnection(buf));
		}
		return new SyncWiresPayload(connections);
	}
}
