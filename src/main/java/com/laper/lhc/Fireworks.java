package com.laper.lhc;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Firework buatan sendiri (partikel + suara): roket naik lalu meledak. */
public final class Fireworks {
    public static final int[] GOLD = {0xFFD54A, 0xFFA726, 0xFF7043, 0xFFFFFF};
    public static final int[] MIX = {0xFF4D6D, 0x4DD2FF, 0xB05CFF, 0xFFE066, 0x7CFF8A};

    static final class Rocket {
        ServerLevel l; double x, y0, z, top; int age; int[] pal;
    }

    static final List<Rocket> R = new ArrayList<>();
    static final int FLIGHT = 14;

    public static void launch(ServerLevel l, double x, double y, double z, double height, int[] pal) {
        Rocket r = new Rocket();
        r.l = l; r.x = x; r.y0 = y; r.z = z; r.top = y + height; r.pal = pal;
        R.add(r);
    }

    public static void clear() { R.clear(); }

    public static void tick() {
        for (Iterator<Rocket> it = R.iterator(); it.hasNext(); ) {
            Rocket r = it.next();
            r.age++;
            double y = r.y0 + (r.top - r.y0) * Math.min(1.0, r.age / (double) FLIGHT);
            r.l.sendParticles(ParticleTypes.FIREWORK, r.x, y, r.z, 2, 0.04, 0.04, 0.04, 0.01);
            if (r.age == 1) Fx.sound(r.l, r.x, y, r.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, 3f, 1f);
            if (r.age >= FLIGHT) {
                burst(r.l, r.x, y, r.z, r.pal);
                it.remove();
            }
        }
    }

    public static void burst(ServerLevel l, double x, double y, double z, int[] pal) {
        var rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 110; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, c = rnd.nextDouble() * 2 - 1, s = Math.sqrt(1 - c * c);
            double dx = s * Math.cos(a), dy = c, dz = s * Math.sin(a);
            double rad = 1.5 + rnd.nextDouble() * 1.8;
            Fx.dust(l, x + dx * rad, y + dy * rad, z + dz * rad, pal[rnd.nextInt(pal.length)], 1.6f);
            if (i % 2 == 0) l.sendParticles(ParticleTypes.FIREWORK, x, y, z, 0, dx, dy, dz, 0.25);
            if (i % 5 == 0) l.sendParticles(ParticleTypes.END_ROD, x, y, z, 0, dx, dy, dz, 0.18);
        }
        Fx.sound(l, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, 4f, 0.9f + rnd.nextFloat() * 0.3f);
        Fx.sound(l, x, y, z, SoundEvents.FIREWORK_ROCKET_TWINKLE, 4f, 1f);
    }
}
