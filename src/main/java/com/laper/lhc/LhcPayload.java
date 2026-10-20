package com.laper.lhc;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * kind 0 = clear
 * kind 1 = loading terrain      (i1 = durasi tick)
 * kind 2 = kamera approach      (i1 = durasi tick, x/y/z = tengah panggung, yaw = arah depan)
 * kind 4 = ayun tangan actor  (i1 = entityId)
 * kind 3 = shot member          (i1 = tick jalan, i2 = tick pose, i3 = entityId actor, i4 = nomor, i5 = total, s = nama)
 *
 * Dikirim sebagai satu string UTF-8 (codec bawaan Minecraft) supaya tidak ada risiko salah baca/tulis byte.
 */
public record LhcPayload(int kind, int i1, int i2, int i3, int i4, int i5,
                         double x, double y, double z, float yaw, String s) implements CustomPacketPayload {

    public static final Type<LhcPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("laperhardcore", "fx"));
    public static final StreamCodec<ByteBuf, LhcPayload> CODEC =
            ByteBufCodecs.STRING_UTF8.map(LhcPayload::decode, LhcPayload::encode);

    static String encode(LhcPayload p) {
        String name = p.s == null ? "" : p.s.replace("|", "");
        return p.kind + "|" + p.i1 + "|" + p.i2 + "|" + p.i3 + "|" + p.i4 + "|" + p.i5 + "|"
                + p.x + "|" + p.y + "|" + p.z + "|" + p.yaw + "|" + name;
    }

    static LhcPayload decode(String str) {
        String[] a = str.split("\\|", 11);
        return new LhcPayload(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]),
                Integer.parseInt(a[3]), Integer.parseInt(a[4]), Integer.parseInt(a[5]),
                Double.parseDouble(a[6]), Double.parseDouble(a[7]), Double.parseDouble(a[8]),
                Float.parseFloat(a[9]), a.length > 10 ? a[10] : "");
    }

    public static LhcPayload of(int kind, int i1, int i2, int i3, int i4, int i5,
                                double x, double y, double z, float yaw, String s) {
        return new LhcPayload(kind, i1, i2, i3, i4, i5, x, y, z, yaw, s);
    }

    public static LhcPayload simple(int kind, int i1) { return of(kind, i1, 0, 0, 0, 0, 0, 0, 0, 0f, ""); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
