package com.redondoguibi.raydiance.client.profile;

import com.google.gson.*;
import com.redondoguibi.raydiance.Config;
import com.redondoguibi.raydiance.client.shaders.PackRepository;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.*;

public final class DynamicProfiles {
    private static List<ProfileRules.Rule> rules = List.of();
    private static final Map<String, Double> blended = new HashMap<>();
    private static String status = "Base";
    private static Object world;
    private static long last;
    private static boolean loaded;
    public static Path file() { return FMLPaths.CONFIGDIR.get().resolve("raydiance/profiles.json"); }
    public static String status() { return status; }
    public static void reload() {
        try {
            Path path = file(); Files.createDirectories(path.getParent());
            if (!Files.exists(path)) Files.writeString(path, """
                {"profiles":[
                  {"name":"Rain","dimension":"minecraft:overworld","weather":"rain","priority":20,"values":{"saturation":0.9,"fogDistance":0.8}},
                  {"name":"Night","dimension":"minecraft:overworld","start":13000,"end":23000,"priority":10,"values":{"temperature":-0.15,"exposure":-0.1}},
                  {"name":"Desert","biome":"minecraft:desert","priority":5,"values":{"temperature":0.2}}
                ]}
                """);
            rules = parse(PackRepository.readText(path, 128 * 1024)); loaded = true;
            status = "Profiles loaded";
        } catch (Exception ex) { status = "Profile error: " + ex.getMessage(); loaded = true; }
    }
    public static List<ProfileRules.Rule> parse(String json) {
        List<ProfileRules.Rule> parsed = new ArrayList<>();
        JsonArray array = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("profiles");
        if (array == null || array.size() > 128) throw new IllegalArgumentException("Expected up to 128 profiles");
        Set<String> names = new HashSet<>();
        for (JsonElement e : array) {
            JsonObject o = e.getAsJsonObject(); String name = o.get("name").getAsString();
            if (name.isBlank() || name.length() > 80 || !names.add(name)) throw new IllegalArgumentException("Invalid/duplicate profile name");
            int start = integer(o,"start",0), end = integer(o,"end",0);
            if (start < 0 || start >= 24000 || end < 0 || end >= 24000) throw new IllegalArgumentException("Time must be 0..23999");
            Map<String,Double> values = new HashMap<>();
            for (var entry : o.getAsJsonObject("values").entrySet()) {
                double v = entry.getValue().getAsDouble();
                PackRepository.validateOption(entry.getKey(), v); values.put(entry.getKey(),v);
            }
            String weather=text(o,"weather","*");
            if(!List.of("*","clear","rain","thunder").contains(weather)) throw new IllegalArgumentException("Invalid weather selector");
            parsed.add(new ProfileRules.Rule(name, text(o,"dimension","*"),text(o,"biome","*"),
                    weather,start,end,integer(o,"priority",0),Map.copyOf(values)));
        }
        return List.copyOf(parsed);
    }
    private static String text(JsonObject o,String k,String fallback) { return o.has(k)?o.get(k).getAsString():fallback; }
    private static int integer(JsonObject o,String k,int fallback) { return o.has(k)?o.get(k).getAsInt():fallback; }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { world=null; blended.clear(); last=0; return; }
        if (!loaded) reload();
        if (world != mc.level) { world=mc.level; blended.clear(); last=0; }
        String biome = mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(k->k.location().toString()).orElse("");
        var context = new ProfileRules.Context(mc.level.dimension().location().toString(),biome,
                mc.level.isThundering()?"thunder":mc.level.isRaining()?"rain":"clear", (int)(mc.level.getDayTime()%24000));
        ProfileRules.Rule rule = Config.DYNAMIC_PROFILES.get()?ProfileRules.select(rules,context):null;
        status = rule == null?"Base":rule.name();
        long now=System.nanoTime(); double dt=last==0?0.05:Math.min(0.25,(now-last)/1e9); last=now;
        for (String key : PackRepository.RANGES.keySet()) {
            double base = base(key), target=rule==null?base:rule.values().getOrDefault(key,base);
            blended.put(key,ProfileRules.blend(blended.getOrDefault(key,base),target,Config.TRANSITION_SECONDS.get(),dt));
        }
    }
    public static double value(String key) { if(com.redondoguibi.raydiance.client.pipeline.PipelineManager.packSelected())return com.redondoguibi.raydiance.client.pipeline.PipelineManager.value(key); return Config.DYNAMIC_PROFILES.get()?blended.getOrDefault(key,base(key)):base(key); }
    private static double base(String key) {
        Double override=Config.USE_PACK_DEFAULTS.get()&&PackRepository.active().id().equals(Config.OPENGL_PACK.get())?PackRepository.active().options().get(key):null;
        return override==null?SettingsOptions.number(key):override;
    }
    private DynamicProfiles() {}
}
