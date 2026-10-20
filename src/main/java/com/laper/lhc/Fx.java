package com.laper.lhc;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

public final class Fx {
    private static int lerp(int a, int b, double f) {
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }

    /** Teks emas tebal bergradient (kuning -> oranye). */
    public static MutableComponent gold(String s) {
        MutableComponent out = Component.empty();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            double t = n <= 1 ? 0 : (double) i / (n - 1);
            out.append(Component.literal(String.valueOf(s.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(lerp(0xFFE27A, 0xFF9F1C, t))).withBold(true)));
        }
        return out;
    }

    public static MutableComponent white(String s) {
        return Component.literal(s).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xF2F2F2)));
    }

    static void dust(ServerLevel l, double x, double y, double z, int color, float size) {
        l.sendParticles(new DustParticleOptions(color, size), x, y, z, 1, 0, 0, 0, 0);
    }

    static void sound(ServerLevel l, double x, double y, double z, SoundEvent s, float vol, float pitch) {
        l.playSound((Player) null, x, y, z, s, SoundSource.AMBIENT, vol, pitch);
    }

    /** Awan debu di kaki saat berjalan. */
    static void feetDust(ServerLevel l, double x, double y, double z) {
        l.sendParticles(ParticleTypes.POOF, x, y + 0.1, z, 3, 0.25, 0.05, 0.25, 0.02);
        l.sendParticles(ParticleTypes.CLOUD, x, y + 0.1, z, 1, 0.3, 0.02, 0.3, 0.01);
    }

    static void poofRing(ServerLevel l, double x, double y, double z, double r) {
        for (int i = 0; i < 28; i++) {
            double a = i * Math.PI * 2 / 28;
            l.sendParticles(ParticleTypes.POOF, x + Math.cos(a) * r, y + 0.1, z + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.05);
        }
    }
}
