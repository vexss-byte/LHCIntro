package com.laper.lhc.client;

import com.laper.lhc.LhcPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Kamera sinematik + overlay (loading terrain, kartu member). */
public final class ClientFx {
    static int mode = 0; // 1 loading, 2 approach, 3 member
    static long start;
    static int i1, i2, i3, i4, i5;
    static double px, py, pz;
    static float fy;
    static String name = "";

    public static final List<int[]> RECTS = new ArrayList<>();
    public static final List<Object[]> TEXTS = new ArrayList<>();

    /** Ayun tangan lokal pada entity (mannequin) lewat field LivingEntity; diam-diam diabaikan kalau field berubah. */
    static void swingLocal(int entityId) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
            if (!(e instanceof net.minecraft.world.entity.LivingEntity)) return;
            Class<?> c = net.minecraft.world.entity.LivingEntity.class;
            c.getField("swingTime").setInt(e, -1);
            c.getField("swinging").setBoolean(e, true);
            c.getField("swingingArm").set(e, net.minecraft.world.InteractionHand.MAIN_HAND);
        } catch (Throwable ignored) { }
    }

    public static void receive(LhcPayload p) {
        if (p.kind() == 4) { swingLocal(p.i1()); return; }
        if (p.kind() == 0) { mode = 0; return; }
        mode = p.kind();
        start = System.nanoTime();
        i1 = p.i1(); i2 = p.i2(); i3 = p.i3(); i4 = p.i4(); i5 = p.i5();
        px = p.x(); py = p.y(); pz = p.z();
        fy = p.yaw();
        name = p.s();
    }

    static double t() { return (System.nanoTime() - start) / 50_000_000.0; }
    static double clamp(double x) { return x < 0 ? 0 : Math.min(1, x); }
    static double smooth(double x) { x = clamp(x); return x * x * (3 - 2 * x); }
    static double lerp(double a, double b, double f) { return a + (b - a) * f; }
    static int argb(double a, int rgb) { return ((int) (clamp(a) * 255) << 24) | rgb; }

    static void update() {
        if (mode == 0) return;
        double t = t();
        boolean end = switch (mode) {
            case 1 -> t > i1 + 40;
            case 2 -> t > i1 + 80;   // tahan di posisi akhir sampai shot berikutnya datang
            case 3 -> t > i1 + i2 + 200;
            default -> true;
        };
        if (end) mode = 0;
    }

    /** Blokir putaran mouse selama sinematik berjalan. */
    public static boolean locked() {
        update();
        return mode != 0;
    }

    /** @return {x,y,z,yaw,pitch,roll,detached} atau null untuk kamera normal. */
    public static double[] camera(float pt) {
        update();
        if (mode != 2 && mode != 3) return null;
        double t = t();
        double fx = -Math.sin(Math.toRadians(fy)), fz = Math.cos(Math.toRadians(fy)); // arah masuk -> tengah
        double rx = fz, rz = -fx;                                                      // kanan penonton
        double cx, cy, cz, tx, ty, tz;

        if (mode == 2) { // datang dari kanan-atas jauh, mendekat perlahan
            double x = clamp(t / i1);
            double e = 1 - Math.pow(1 - x, 2.2);
            double sx = px + rx * 70 + fx * 55, sy = py + 48, sz = pz + rz * 70 + fz * 55;
            double ex = px + fx * 10.5, ey = py + 3.0, ez = pz + fz * 10.5;
            cx = lerp(sx, ex, e); cy = lerp(sy, ey, e); cz = lerp(sz, ez, e);
            tx = px; ty = py + lerp(2.0, 1.2, e); tz = pz;
        } else {
            Minecraft mc = Minecraft.getInstance();
            Entity a = mc.level == null ? null : mc.level.getEntity(i3);
            Vec3 ap = a != null ? a.getPosition(pt) : new Vec3(px, py, pz);
            double W = i1, P = i2;
            double w = smooth(t / W);
            // shot lebar: nunggu member berjalan dari pintu
            double d = 10.5 - 2.0 * w;
            double wx = px + fx * d + rx * Math.sin(t * 0.03), wy = py + 3.0 - 0.6 * w, wz = pz + fz * d + rz * Math.sin(t * 0.03);
            // shot dekat: mengitari member yang berpose
            double pp = clamp((t - W) / P);
            double ang = Math.toRadians(-25 + 50 * pp), rad = 3.8 - 0.8 * pp;
            double ox = fx * Math.cos(ang) - fz * Math.sin(ang), oz = fx * Math.sin(ang) + fz * Math.cos(ang);
            double kx = ap.x + ox * rad, ky = ap.y + 1.5, kz = ap.z + oz * rad;
            double s = smooth((t - (W - 14)) / 34);
            cx = lerp(wx, kx, s); cy = lerp(wy, ky, s); cz = lerp(wz, kz, s);
            tx = ap.x; ty = ap.y + 1.0 + 0.25 * s; tz = ap.z;
        }
        double dx = tx - cx, dy = ty - cy, dz = tz - cz;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz)));
        return new double[]{cx, cy, cz, yaw, pitch, 0, 1};
    }

    static String two(int n) { return String.format("%02d", n); }

    /** Isi daftar RECTS/TEXTS untuk frame ini. */
    public static void build(int W, int H) {
        RECTS.clear();
        TEXTS.clear();
        update();
        if (mode == 0) return;
        double t = t();

        if (mode == 1) { // layar "Loading terrain..."
            double a = Math.min(smooth(t / 15.0), 1 - smooth((t - (i1 - 20)) / 20.0));
            if (a <= 0.001) return;
            RECTS.add(new int[]{0, 0, W, H, argb(a, 0x0E0E12)});
            if (a > 0.5) {
                String s = "Loading terrain...";
                int tw = Minecraft.getInstance().font.width(s);
                TEXTS.add(new Object[]{s, W / 2 - tw / 2, H / 2 - 14, argb(1, 0xFFFFFF)});
                int bw = 182, bx = W / 2 - bw / 2, by = H / 2 + 4;
                double prog = clamp(t / (i1 - 20));
                RECTS.add(new int[]{bx - 1, by - 1, bx + bw + 1, by + 7, argb(1, 0x2A2A2A)});
                RECTS.add(new int[]{bx, by, bx + bw, by + 6, argb(1, 0x555555)});
                RECTS.add(new int[]{bx, by, bx + (int) (bw * prog), by + 6, argb(1, 0xFFFFFF)});
            }
        } else if (mode == 3) {
            double W0 = i1;
            // fade hitam singkat saat pergantian member
            double fade = 1 - smooth(t / 10.0);
            if (fade > 0.001) RECTS.add(new int[]{0, 0, W, H, argb(fade, 0x000000)});
            // kartu member kiri-bawah (meluncur dari kiri)
            double slide = smooth((t - (W0 - 10)) / 14.0);
            if (slide > 0.001) {
                int cw = 210, ch = 58;
                int x0 = (int) (22 - (1 - slide) * (cw + 40)), y0 = H - 22 - ch;
                RECTS.add(new int[]{x0 - 2, y0 - 2, x0 + cw + 2, y0 + ch + 2, argb(1, 0xFFA726)});
                RECTS.add(new int[]{x0, y0, x0 + cw, y0 + ch, argb(0.94, 0x141414)});
                TEXTS.add(new Object[]{"\u00A7f\u00A7l#" + two(i4) + " " + name, x0 + 10, y0 + 9, argb(1, 0xFFFFFF)});
                TEXTS.add(new Object[]{"\u00A76\u00A7lLAPER HARDCORE", x0 + 10, y0 + 24, argb(1, 0xFFA726)});
                TEXTS.add(new Object[]{"\u00A77MEMBER " + two(i4) + " OF " + two(i5), x0 + 10, y0 + 38, argb(1, 0xB0B0B0)});
            }
        }
    }
}
