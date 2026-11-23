package id.heartlifesteal.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Utilitas teks: kode warna &, hex &#rrggbb, dan penggantian placeholder {kunci}. */
public final class Text {
    private static final LegacyComponentSerializer AMP =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private Text() {}

    public static Component color(String s) {
        return AMP.deserialize(s == null ? "" : s);
    }

    /** Seperti color(), tetapi tanpa miring (default nama/lore item di Minecraft adalah miring). */
    public static Component noItalic(String s) {
        return color(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Teks berkode § (untuk entri scoreboard). */
    public static String legacy(String s) {
        return SECTION.serialize(color(s));
    }

    /** Ganti {kunci} dengan nilai; argumen berpasangan: kunci1, nilai1, kunci2, nilai2, ... */
    public static String apply(String template, Object... kv) {
        if (template == null) return "";
        String out = template;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out = out.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return out;
    }
}
