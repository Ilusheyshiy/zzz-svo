package moodygod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Универсальные клиентские эффекты: disco, eye, deaf, interrupt, freeze, scene_on/off, show, say... */
public record FxPayload(String op, String a, int n) implements CustomPacketPayload {
    public static final Type<FxPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("moody_god", "fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), FxPayload::op,
            ByteBufCodecs.stringUtf8(2048), FxPayload::a,
            ByteBufCodecs.VAR_INT, FxPayload::n,
            FxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
