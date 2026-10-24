package moodygod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** text: страницы разделены \f, строки внутри страницы — \n. mood: 0..4 */
public record GodPayload(String text, int mood) implements CustomPacketPayload {
    public static final Type<GodPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("moody_god", "speak"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GodPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16384), GodPayload::text,
            ByteBufCodecs.VAR_INT, GodPayload::mood,
            GodPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // тайминги анимации (общие для клиента и сервера)
    public static final int MS_PER_CHAR = 45, HOLD_MS = 900, FALL_STAGGER_MS = 22, FALL_MS = 1100;

    public static int pageMs(String page) {
        int n = page.length();
        return n * MS_PER_CHAR + HOLD_MS + n * FALL_STAGGER_MS + FALL_MS + 350;
    }

    // префиксы страниц: \u0001 = Сатана, \u0002+цифра = перебивка настроения
    public static boolean isSatan(String pg) { return pg.startsWith("\u0001"); }
    public static int moodOf(String pg, int def) {
        return (pg.startsWith("\u0002") && pg.length() > 2) ? (pg.charAt(1) - 'A') : def;
    }
    public static String strip(String pg) {
        if (pg.startsWith("\u0001")) return pg.substring(1);
        if (pg.startsWith("\u0002") && pg.length() > 2) return pg.substring(2);
        return pg;
    }
    public static String tag(int mood) { return "\u0002" + (char) ('A' + mood); }
}
