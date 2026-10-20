package com.laper.lhc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LhcConfig {
    public String dim = "";
    public boolean cSet = false, eSet = false;
    public double cx, cy, cz;   // tengah panggung
    public double ex, ey, ez;   // pintu masuk (tempat member muncul)
    public int walkTicks = 100; // 5 detik jalan
    public int poseTicks = 110; // 5.5 detik pose
    public List<String> members = new ArrayList<>();

    public static LhcConfig I = new LhcConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("laperhardcore.json"); }

    public static void load() {
        try {
            if (Files.exists(path())) {
                LhcConfig c = GSON.fromJson(Files.readString(path()), LhcConfig.class);
                if (c != null) I = c;
                if (I.members == null) I.members = new ArrayList<>();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void save() {
        try { Files.writeString(path(), GSON.toJson(I)); } catch (Exception e) { e.printStackTrace(); }
    }
}
