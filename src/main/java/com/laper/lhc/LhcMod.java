package com.laper.lhc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class LhcMod implements ModInitializer {
    static final Predicate<CommandSourceStack> OP = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    static void ok(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal("§6[LHC] §f" + msg), false);
    }

    static int fail(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }

    @Override
    public void onInitialize() {
        LhcConfig.load();
        PayloadTypeRegistry.clientboundPlay().register(LhcPayload.TYPE, LhcPayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTING.register(s -> IntroShow.server = s);
        ServerTickEvents.END_SERVER_TICK.register(s -> { IntroShow.tick(); Fireworks.tick(); });
        ServerPlayConnectionEvents.DISCONNECT.register((h, srv) -> IntroShow.onLeave(h.getPlayer()));

        CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(
            literal("lhc").requires(OP)
                .then(literal("play")
                    .then(literal("intro").executes(c -> {
                        var players = c.getSource().getServer().getPlayerList().getPlayers();
                        String err = IntroShow.play(true, new ArrayList<>(LhcConfig.I.members), players);
                        if (err != null) return fail(c, err);
                        ok(c, "Intro dimulai untuk " + players.size() + " pemain.");
                        return 1;
                    }))
                    .then(literal("member").then(argument("nama", StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        String err = IntroShow.play(false, List.of(StringArgumentType.getString(c, "nama")), List.of(p));
                        if (err != null) return fail(c, err);
                        ok(c, "Preview member dimulai.");
                        return 1;
                    }))))
                .then(literal("stop").executes(c -> {
                    if (!IntroShow.stop()) return fail(c, "Tidak ada intro yang berjalan.");
                    ok(c, "Intro dihentikan.");
                    return 1;
                }))
                .then(literal("setcenter").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    LhcConfig cf = LhcConfig.I;
                    cf.dim = p.level().dimension().toString();
                    cf.cx = p.getX(); cf.cy = p.getY(); cf.cz = p.getZ();
                    cf.cSet = true;
                    LhcConfig.save();
                    ok(c, "Tengah panggung diset.");
                    return 1;
                }))
                .then(literal("setentrance").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    LhcConfig cf = LhcConfig.I;
                    if (cf.cSet && !cf.dim.equals(p.level().dimension().toString()))
                        return fail(c, "Pintu masuk harus di dimensi yang sama dengan tengah panggung.");
                    cf.dim = p.level().dimension().toString();
                    cf.ex = p.getX(); cf.ey = p.getY(); cf.ez = p.getZ();
                    cf.eSet = true;
                    LhcConfig.save();
                    ok(c, "Pintu masuk diset. Member akan berjalan dari sini ke tengah.");
                    return 1;
                }))
                .then(literal("members")
                    .then(literal("add").then(argument("nama", StringArgumentType.word()).executes(c -> {
                        String n = StringArgumentType.getString(c, "nama");
                        if (LhcConfig.I.members.stream().anyMatch(x -> x.equalsIgnoreCase(n))) return fail(c, "Sudah ada.");
                        LhcConfig.I.members.add(n);
                        LhcConfig.save();
                        ok(c, n + " ditambahkan (total " + LhcConfig.I.members.size() + ").");
                        return 1;
                    })))
                    .then(literal("remove").then(argument("nama", StringArgumentType.word()).executes(c -> {
                        String n = StringArgumentType.getString(c, "nama");
                        boolean r = LhcConfig.I.members.removeIf(x -> x.equalsIgnoreCase(n));
                        if (!r) return fail(c, "Tidak ditemukan.");
                        LhcConfig.save();
                        ok(c, n + " dihapus.");
                        return 1;
                    })))
                    .then(literal("list").executes(c -> {
                        ok(c, LhcConfig.I.members.size() + " member: " + String.join(", ", LhcConfig.I.members));
                        return 1;
                    }))
                    .then(literal("clear").executes(c -> {
                        LhcConfig.I.members.clear();
                        LhcConfig.save();
                        ok(c, "Daftar member dikosongkan.");
                        return 1;
                    }))
                    .then(literal("online").executes(c -> {
                        LhcConfig.I.members.clear();
                        for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers())
                            LhcConfig.I.members.add(p.getGameProfile().name());
                        LhcConfig.save();
                        ok(c, "Daftar member diisi dari pemain online (" + LhcConfig.I.members.size() + ").");
                        return 1;
                    })))
                .then(literal("time")
                    .then(literal("walk").then(argument("detik", IntegerArgumentType.integer(2, 60)).executes(c -> {
                        LhcConfig.I.walkTicks = IntegerArgumentType.getInteger(c, "detik") * 20;
                        LhcConfig.save();
                        ok(c, "Durasi jalan: " + IntegerArgumentType.getInteger(c, "detik") + " detik.");
                        return 1;
                    })))
                    .then(literal("pose").then(argument("detik", IntegerArgumentType.integer(2, 60)).executes(c -> {
                        LhcConfig.I.poseTicks = IntegerArgumentType.getInteger(c, "detik") * 20;
                        LhcConfig.save();
                        ok(c, "Durasi pose: " + IntegerArgumentType.getInteger(c, "detik") + " detik.");
                        return 1;
                    }))))
                .then(literal("test")
                    .then(literal("loading").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, LhcPayload.TYPE))
                            return fail(c, "Client kamu tidak punya mod ini.");
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, LhcPayload.simple(1, 80));
                        ok(c, "Tes layar loading dikirim.");
                        return 1;
                    }))
                    .then(literal("firework").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    for (int i = 0; i < 4; i++) {
                        double a = i * Math.PI / 2;
                        Fireworks.launch((net.minecraft.server.level.ServerLevel) p.level(),
                                p.getX() + Math.cos(a) * 5, p.getY(), p.getZ() + Math.sin(a) * 5, 14,
                                i % 2 == 0 ? Fireworks.GOLD : Fireworks.MIX);
                    }
                    return 1;
                })))
        ));
    }
}
