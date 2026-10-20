package com.laper.lhc;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class IntroShow {
    enum Phase { LOAD_IN, APPROACH, MEMBER, LOAD_OUT }

    record Saved(ServerLevel level, double x, double y, double z, float yaw, float pitch, GameType mode) {}

    public static MinecraftServer server;

    static boolean active, full, moved, restored;
    static Phase phase;
    static int tick, idx, seg, walkT, poseT, poseId, walkId;
    static final Map<UUID, Saved> saved = new LinkedHashMap<>();
    static List<String> members = new ArrayList<>();
    static UUID actor;
    static ServerLevel level;
    static Vec3 center, entrance, fwd, anchor;
    static float fy;
    static ServerBossEvent bar;

    static final int LOAD = 80, APPROACH = 200, POSES = 8, WALKS = 10;

    // ------------------------------------------------------------ util
    static ServerLevel findLevel(String dim) {
        for (ServerLevel l : server.getAllLevels()) if (l.dimension().toString().equals(dim)) return l;
        return server.overworld();
    }

    static void send(ServerPlayer p, LhcPayload pl) {
        if (ServerPlayNetworking.canSend(p, LhcPayload.TYPE)) ServerPlayNetworking.send(p, pl);
    }

    static void sendAll(LhcPayload pl) {
        for (UUID u : saved.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) send(p, pl);
        }
    }

    static String two(int n) { return String.format("%02d", n); }

    // ------------------------------------------------------------ mulai / stop
    public static String play(boolean fullIntro, List<String> mem, Collection<ServerPlayer> viewers) {
        if (active) return "Intro sedang berjalan. Pakai /lhc stop dulu.";
        LhcConfig c = LhcConfig.I;
        if (!c.cSet) return "Belum set tengah panggung. Berdiri di tengah lalu /lhc setcenter";
        if (!c.eSet) return "Belum set pintu masuk. Berdiri di pintu lalu /lhc setentrance";
        if (mem.isEmpty()) return "Daftar member kosong. Pakai /lhc members add <nama> atau /lhc members online";
        if (viewers.isEmpty()) return "Tidak ada pemain online.";

        level = findLevel(c.dim);
        center = new Vec3(c.cx, c.cy, c.cz);
        entrance = new Vec3(c.ex, c.ey, c.ez);
        double dx = center.x - entrance.x, dz = center.z - entrance.z, len = Math.hypot(dx, dz);
        if (len < 0.5) return "Pintu masuk terlalu dekat dengan tengah panggung.";
        fwd = new Vec3(dx / len, 0, dz / len);
        fy = (float) Math.toDegrees(Math.atan2(-fwd.x, fwd.z));
        anchor = center.add(fwd.scale(18)).add(0, 10, 0);

        saved.clear();
        for (ServerPlayer p : viewers) {
            saved.put(p.getUUID(), new Saved((ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(),
                    p.getYRot(), p.getXRot(), p.gameMode.getGameModeForPlayer()));
        }
        bar = new ServerBossEvent(UUID.randomUUID(), Fx.gold("LAPER HARDCORE"),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        bar.setProgress(0f);

        full = fullIntro;
        members = new ArrayList<>(mem);
        walkT = c.walkTicks;
        poseT = c.poseTicks;
        idx = 0; seg = 0; tick = 0;
        moved = false; restored = false; active = true;
        Fireworks.clear();

        if (full) {
            phase = Phase.LOAD_IN;
            sendAll(LhcPayload.simple(1, LOAD));
        } else {
            moveViewers();
            phase = Phase.MEMBER;
            startMember();
        }
        return null;
    }

    public static boolean stop() {
        if (!active) return false;
        killActor();
        if (!restored) restoreViewers();
        endAll();
        return true;
    }

    static void killActor() {
        if (actor != null && level != null) {
            Entity e = level.getEntity(actor);
            if (e != null) e.discard();
        }
        actor = null;
    }

    static void moveViewers() {
        for (UUID u : saved.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) continue;
            p.teleportTo(level, anchor.x, anchor.y, anchor.z, Set.<Relative>of(), 0f, 0f, true);
            p.setGameMode(GameType.SPECTATOR);
            bar.addPlayer(p);
        }
        moved = true;
    }

    static void restore(ServerPlayer p, Saved s) {
        p.teleportTo(s.level(), s.x(), s.y(), s.z(), Set.<Relative>of(), s.yaw(), s.pitch(), true);
        p.setGameMode(s.mode());
        if (bar != null) bar.removePlayer(p);
    }

    static void restoreViewers() {
        for (Map.Entry<UUID, Saved> e : saved.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) restore(p, e.getValue());
        }
        moved = false;
        restored = true;
    }

    static void endAll() {
        sendAll(LhcPayload.simple(0, 0));
        if (bar != null) bar.removeAllPlayers();
        saved.clear();
        Fireworks.clear();
        active = false;
    }

    public static void onLeave(ServerPlayer p) {
        Saved s = saved.remove(p.getUUID());
        if (s != null && moved) restore(p, s);
        else if (bar != null) bar.removePlayer(p);
    }

    // ------------------------------------------------------------ tick
    public static void tick() {
        if (!active) return;
        tick++;
        if (moved) hold();
        switch (phase) {
            case LOAD_IN -> {
                if (tick == 20) moveViewers();
                if (tick >= LOAD) {
                    phase = Phase.APPROACH;
                    tick = 0;
                    sendAll(LhcPayload.of(2, APPROACH, 0, 0, 0, 0, center.x, center.y, center.z, fy, ""));
                }
            }
            case APPROACH -> {
                if (tick % 10 == 0) ambientRocket();
                if (tick == 20) titles();
                if (tick >= APPROACH) {
                    phase = Phase.MEMBER;
                    tick = 0;
                    startMember();
                }
            }
            case MEMBER -> memberStep();
            case LOAD_OUT -> {
                if (tick == 20) restoreViewers();
                if (tick >= LOAD) endAll();
            }
        }
    }

    static void hold() {
        for (UUID u : saved.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) continue;
            if (p.distanceToSqr(anchor) > 4) {
                p.teleportTo(level, anchor.x, anchor.y, anchor.z, Set.<Relative>of(), 0f, 0f, false);
            }
        }
    }

    static void ambientRocket() {
        var r = ThreadLocalRandom.current();
        double a = r.nextDouble() * Math.PI * 2, d = 6 + r.nextDouble() * 8;
        Fireworks.launch(level, center.x + Math.cos(a) * d, center.y, center.z + Math.sin(a) * d,
                14 + r.nextDouble() * 8, r.nextBoolean() ? Fireworks.GOLD : Fireworks.MIX);
    }

    static void titles() {
        for (UUID u : saved.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) continue;
            p.connection.send(new ClientboundSetTitlesAnimationPacket(15, 110, 25));
            p.connection.send(new ClientboundSetTitleTextPacket(Fx.gold("LAPER HARDCORE")));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Fx.white("PERKENALAN MEMBER")));
        }
    }

    // ------------------------------------------------------------ member
    static boolean spawnActor(String name) {
        String safe = name.replaceAll("[^A-Za-z0-9_.\\-]", "");
        UUID id = UUID.randomUUID();
        long m = id.getMostSignificantBits(), l = id.getLeastSignificantBits();
        String cmd = String.format(Locale.ROOT,
                "summon minecraft:mannequin %.3f %.3f %.3f {UUID:[I;%d,%d,%d,%d],profile:\"%s\",CustomName:\"%s\","
                        + "CustomNameVisible:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,hide_description:1b,Tags:[\"lhc_actor\"]}",
                entrance.x, entrance.y, entrance.z, (int) (m >> 32), (int) m, (int) (l >> 32), (int) l, safe, safe);
        CommandSourceStack src = server.createCommandSourceStack().withLevel(level).withPosition(entrance).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(src, cmd);
        actor = id;
        return level.getEntity(id) != null;
    }

    static void startMember() {
        seg = 0;
        String name = members.get(idx);
        poseId = idx % POSES;
        walkId = (idx * 3 + 1) % WALKS;
        if (!spawnActor(name)) {
            for (UUID u : saved.keySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                if (p != null) p.sendSystemMessage(Fx.white("[LHC] Gagal memunculkan actor (entity mannequin tidak ditemukan)."));
            }
            stop();
            return;
        }
        Entity e = level.getEntity(actor);
        bar.setName(Fx.gold("[" + two(idx + 1) + "] " + name));
        bar.setProgress((idx + 1f) / members.size());
        sendAll(LhcPayload.of(3, walkT, poseT, e.getId(), idx + 1, members.size(),
                center.x, center.y, center.z, fy, name));
        Fx.poofRing(level, entrance.x, entrance.y, entrance.z, 1.0);
    }

    static void memberStep() {
        seg++;
        Entity e = actor == null ? null : level.getEntity(actor);
        if (e == null) { stop(); return; }

        if (seg <= walkT) {
            walk(e, seg / (double) walkT);
        } else if (seg <= walkT + poseT) {
            pose(e, seg - walkT);
        } else {
            Fx.poofRing(level, center.x, center.y, center.z, 1.3);
            Fx.sound(level, center.x, center.y, center.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, 3f, 1.4f);
            e.discard();
            actor = null;
            idx++;
            if (idx >= members.size()) finishMembers();
            else startMember();
        }
    }

    static void finishMembers() {
        if (full) {
            phase = Phase.LOAD_OUT;
            tick = 0;
            sendAll(LhcPayload.simple(1, LOAD));
        } else {
            stop();
        }
    }

    static void apply(Entity e, Vec3 pos, float yaw, Pose pose, boolean sprint) {
        e.setPos(pos.x, pos.y, pos.z);
        e.setYRot(yaw);
        e.setXRot(0f);
        if (e instanceof LivingEntity le) {
            le.setYHeadRot(yaw);
            le.setYBodyRot(yaw);
        }
        e.setPose(pose);
        e.setSprinting(sprint);
    }

    /** 10 gaya jalan berbeda. */
    static void walk(Entity e, double p) {
        double rx = fwd.z, rz = -fwd.x; // vektor kanan
        double q = p, lateral = 0, up = 0;
        float yaw = fy;
        Pose pose = Pose.STANDING;
        boolean sprint = false;
        switch (walkId) {
            case 1 -> sprint = true;
            case 2 -> pose = Pose.CROUCHING;
            case 3 -> pose = Pose.SWIMMING;
            case 4 -> up = Math.abs(Math.sin(p * Math.PI * 9)) * 0.7;
            case 5 -> { lateral = Math.sin(p * Math.PI * 6) * 1.3; yaw = fy + (float) (Math.cos(p * Math.PI * 6) * 35); }
            case 6 -> yaw = fy + 180f;
            case 7 -> yaw = fy + 90f;
            case 8 -> yaw = fy + (float) (p * 720);
            case 9 -> q = p * p * (3 - 2 * p);
            default -> {}
        }
        Vec3 base = entrance.lerp(center, q);
        Vec3 pos = new Vec3(base.x + rx * lateral, base.y + up, base.z + rz * lateral);
        apply(e, pos, yaw, pose, sprint);
        if (seg % 2 == 0) Fx.feetDust(level, pos.x, base.y, pos.z);
    }

    /** Ayun tangan: dipicu lewat paket kustom, client yang memutar animasinya (paket animate vanilla crash di mannequin). */
    static void swing(Entity e) {
        sendAll(LhcPayload.simple(4, e.getId()));
    }

    static void equip(LivingEntity le, Item item) {
        le.stopUsingItem();
        le.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
    }

    static void use(LivingEntity le) {
        if (!le.isUsingItem()) le.startUsingItem(InteractionHand.MAIN_HAND);
    }

    /** 8 pose berbeda (memakai animasi bawaan Minecraft: item dipakai, ayun tangan, jongkok, dll). */
    static void pose(Entity e, int k) {
        LivingEntity le = e instanceof LivingEntity l ? l : null;
        float yaw = fy;
        Pose pose = Pose.STANDING;
        double prog = k / (double) poseT;

        if (k == 1) {
            Fx.poofRing(level, center.x, center.y, center.z, 1.6);
            var r = ThreadLocalRandom.current();
            for (int i = 0; i < 3; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = 4 + r.nextDouble() * 5;
                Fireworks.launch(level, center.x + Math.cos(a) * d, center.y, center.z + Math.sin(a) * d,
                        12 + r.nextDouble() * 8, i % 2 == 0 ? Fireworks.GOLD : Fireworks.MIX);
            }
            Fx.sound(level, center.x, center.y, center.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 3f, 1f);
            if (le != null) {
                switch (poseId) {
                    case 0, 4 -> equip(le, Items.TRIDENT);
                    case 1 -> equip(le, Items.SHIELD);
                    case 2 -> equip(le, Items.SPYGLASS);
                    case 3 -> equip(le, Items.BOW);
                    case 5, 7 -> equip(le, Items.DIAMOND_SWORD);
                    case 6 -> equip(le, Items.DIAMOND_PICKAXE);
                    default -> {}
                }
            }
        }
        if (k % 28 == 0) Fireworks.launch(level, center.x, center.y, center.z, 14, Fireworks.GOLD);

        switch (poseId) {
            case 0 -> { // tombak: dipegang, lalu diangkat, lalu ditusukkan, berulang
                if (le != null) {
                    boolean raise = (k % 80) >= 35;
                    if (raise) use(le);
                    else if (le.isUsingItem()) le.stopUsingItem();
                    if (k % 80 == 0) swing(e);
                }
            }
            case 1, 2, 3 -> { if (le != null) use(le); }
            case 4 -> yaw = fy + (float) (540 * (prog * prog * (3 - 2 * prog)));
            case 5 -> pose = Pose.CROUCHING;
            case 6 -> { if (k % 8 == 0) swing(e); }
            case 7 -> {
                yaw = fy + (float) (Math.sin(k * 0.12) * 40);
                if (k % 12 == 0) swing(e);
            }
            default -> {}
        }
        apply(e, center, yaw, pose, false);
    }
}
