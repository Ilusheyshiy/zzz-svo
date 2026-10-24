package moodygod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** ticks до следующего визита; -1 = Бог прямо сейчас здесь */
public record TimerPayload(int ticks, int faith) implements CustomPacketPayload {
    public static final Type<TimerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("moody_god", "timer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TimerPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, TimerPayload::ticks, ByteBufCodecs.VAR_INT, TimerPayload::faith, TimerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
