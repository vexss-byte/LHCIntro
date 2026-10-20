package com.laper.lhc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.lang.reflect.Method;

/**
 * Menggambar teks lewat refleksi, karena nama method GUI (drawString / text) berubah antar versi.
 * Kalau tidak ketemu, teks tidak digambar (tidak crash).
 */
final class Txt {
    private static Method m;
    private static boolean withShadow, tried, broken;

    static void draw(Object g, String s, int x, int y, int color) {
        if (broken) return;
        try {
            if (!tried) {
                tried = true;
                for (Method c : g.getClass().getMethods()) {
                    String n = c.getName();
                    if (!n.equals("drawString") && !n.equals("text")) continue;
                    Class<?>[] p = c.getParameterTypes();
                    if (p.length >= 5 && p[0] == Font.class && p[1] == String.class
                            && p[2] == int.class && p[3] == int.class && p[4] == int.class) {
                        if (p.length == 6 && p[5] == boolean.class) { m = c; withShadow = true; break; }
                        if (p.length == 5 && m == null) m = c;
                    }
                }
                if (m == null) { broken = true; return; }
            }
            Font font = Minecraft.getInstance().font;
            if (withShadow) m.invoke(g, font, s, x, y, color, true);
            else m.invoke(g, font, s, x, y, color);
        } catch (Throwable t) {
            broken = true;
        }
    }
}
