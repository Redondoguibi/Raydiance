package com.redondoguibi.raydiance.client.shaders;

import com.google.gson.*;
import com.redondoguibi.raydiance.Config;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Folder packs are data + an optional GLSL composite. No native code, includes or external paths. */
public final class PackRepository {
    public static final Map<String,double[]> RANGES = Map.ofEntries(
        Map.entry("materialSpecular",new double[]{0,1}),
        Map.entry("materialWetness",new double[]{0,1}),
        Map.entry("materialEmissive",new double[]{0,1}),
        Map.entry("waterAbsorption",new double[]{0,1}),
        Map.entry("saturation",new double[]{0,2}), Map.entry("contrast",new double[]{.5,2}),Map.entry("brightness",new double[]{.5,2}),
        Map.entry("exposure",new double[]{-2,2}),Map.entry("gamma",new double[]{.5,2}),Map.entry("temperature",new double[]{-1,1}),
        Map.entry("vignette",new double[]{0,1}),Map.entry("strength",new double[]{0,1}),Map.entry("fogDistance",new double[]{.25,3}),
        Map.entry("bloom",new double[]{0,1}),Map.entry("bloomThreshold",new double[]{.1,1}),Map.entry("atmosphere",new double[]{0,1}),
        Map.entry("contactShadows",new double[]{0,1}),Map.entry("water",new double[]{0,1}),Map.entry("reflections",new double[]{0,1}),Map.entry("wind",new double[]{0,1}),Map.entry("ssao",new double[]{0,1}),Map.entry("godRays",new double[]{0,1}),Map.entry("sharpen",new double[]{0,1}),Map.entry("toneMapping",new double[]{0,1}));
    public record Pass(String file, String source, double scale, boolean floatingPoint, String when) {}
    public record Pack(String id,String title,Map<String,Double> options,String composite,List<Pass> passes,List<ShaderOption> shaderOptions,List<ShaderPreset> presets) {
        public Pack(String id,String title,Map<String,Double> options,String composite) { this(id,title,options,composite,List.of()); }
        public Pack(String id,String title,Map<String,Double> options,String composite,List<Pass> passes) {this(id,title,options,composite,passes,List.of(),List.of());}
        public Pack { options=Map.copyOf(options); passes=List.copyOf(passes);shaderOptions=List.copyOf(shaderOptions);presets=List.copyOf(presets); }
    }
    private static Pack active = new Pack("none","Custom settings",Map.of(),null);
    private static String error="";
    public static Pack active() { return active; }
    public static String error() { return error; }
    public static void validateOption(String key,double value) {
        double[] bounds=RANGES.get(key);
        if(bounds==null || !Double.isFinite(value) || value<bounds[0] || value>bounds[1]) throw new IllegalArgumentException("Invalid option: "+key);
    }
    public static String readText(Path path,int limit) throws IOException {
        if(Files.isSymbolicLink(path)) throw new IOException("Symbolic links are not allowed: "+path.getFileName());
        try(InputStream in=Files.newInputStream(path)) {
            byte[] bytes=in.readNBytes(limit+1);
            if(bytes.length==0 || bytes.length>limit) throw new IOException("Invalid file size: "+path.getFileName());
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        }
    }
    public static Pack read(Path root,String id) throws IOException {
        if(!ShaderPackFiles.validName(id)) throw new IOException("Invalid pack name");
        Path dir=root.resolve(id);
        if(Files.isSymbolicLink(root) || Files.isSymbolicLink(dir) || !Files.isDirectory(dir)) throw new IOException("Invalid pack directory");
        try {
            JsonObject o=JsonParser.parseString(readText(dir.resolve("raydiance.json"),65536)).getAsJsonObject();
            double format=o.get("format").getAsDouble();
            if(format!=1 && format!=2) throw new IOException("Unsupported pack format");
            String title=o.has("name")?o.get("name").getAsString():id;
            if(title.length()>80) throw new IOException("Pack title too long");
            Map<String,Double> options=new HashMap<>();
            if(o.has("options")) for(var e:o.getAsJsonObject("options").entrySet()) { double v=e.getValue().getAsDouble();validateOption(e.getKey(),v);options.put(e.getKey(),v); }
            ShaderSourceLoader loader=new ShaderSourceLoader(dir);
            String source=format==1 && Files.exists(dir.resolve("composite.fsh"))?loader.load("composite.fsh"):null;
            List<Pass> passes=new ArrayList<>();
            if(format==2) {
                JsonArray array=o.getAsJsonArray("passes");
                if(array==null || array.isEmpty() || array.size()>8) throw new IOException("Format 2 requires 1..8 passes");
                for(JsonElement element:array) {
                    JsonObject pass=element.getAsJsonObject();
                    String file=pass.get("file").getAsString();
                    if(!file.endsWith(".fsh")) throw new IOException("Pass must be a .fsh file");
                    double scale=pass.has("scale")?pass.get("scale").getAsDouble():1;
                    if(!Double.isFinite(scale) || scale<0.25 || scale>1) throw new IOException("Pass scale must be 0.25..1");
                    String target=pass.has("target")?pass.get("target").getAsString():"rgba8";
                    if(!target.equals("rgba8")&&!target.equals("rgba16f")) throw new IOException("Unsupported pass target");
                    String when=pass.has("when")?pass.get("when").getAsString():"";
                    if(!when.isEmpty()&&!RANGES.containsKey(when)) throw new IOException("Unknown conditional option: "+when);
                    passes.add(new Pass(file,loader.load(file),scale,target.equals("rgba16f"),when));
                }
            }
            var definitions=ShaderOption.parse(o);
            return new Pack(id,title,options,source,passes,definitions,ShaderPreset.parse(o,definitions));
        } catch(RuntimeException e) { throw new IOException("Invalid manifest: "+e.getMessage(),e); }
    }
    public static List<String> list() throws IOException {
        Path root=ShaderPackFiles.directory();Files.createDirectories(root);
        try(var entries=Files.list(root)) {return entries.filter(p->!Files.isSymbolicLink(p)&&Files.isDirectory(p))
            .filter(p->ShaderPackFiles.validName(p.getFileName().toString())&&Files.isRegularFile(p.resolve("raydiance.json")))
            .map(p->p.getFileName().toString()).sorted().toList();}
    }
    public static Pack requested() throws IOException {
        String id=Config.OPENGL_PACK.get();return id.equals("none")?new Pack("none","Custom settings",Map.of(),null):read(ShaderPackFiles.directory(),id);
    }
    public static void accept(Pack pack) { active=pack;error=""; }
    public static void reject(Exception ex) { error=ex.getMessage(); }
    public static void installExample() throws IOException {
        Path root=ShaderPackFiles.directory();Files.createDirectories(root);
        if(Files.isSymbolicLink(root))throw new IOException("Symbolic shaderpack directory is not allowed");
        Path dir=root.resolve("cinematic");Files.createDirectory(dir);
        Files.writeString(dir.resolve("raydiance.json"),"{\"format\":1,\"name\":\"Cinematic Light\",\"options\":{\"bloom\":0.25,\"atmosphere\":0.2,\"saturation\":1.08}}",StandardOpenOption.CREATE_NEW);
        Files.writeString(dir.resolve("composite.fsh"),com.redondoguibi.raydiance.client.render.GlProgram.resource("composite.fsh"),StandardOpenOption.CREATE_NEW);
    }
    public static void installPipelineExample() throws IOException {
        Path root=ShaderPackFiles.directory();Files.createDirectories(root);
        if(Files.isSymbolicLink(root))throw new IOException("Symlink shaderpack directory is not allowed");
        Path dir=root.resolve("multipass_demo");Files.createDirectory(dir);Files.createDirectory(dir.resolve("lib"));
        Files.writeString(dir.resolve("raydiance.json"),"""
            {"format":2,"name":"Multi-pass light","options":{"ssao":0.2,"godRays":0.15},
             "passes":[{"file":"composite.fsh","target":"rgba16f"},{"file":"final.fsh"}]}
            """,StandardOpenOption.CREATE_NEW);
        Files.writeString(dir.resolve("composite.fsh"),"""
            #version 150
            uniform sampler2D Scene;
            in vec2 texCoord;
            out vec4 fragColor;
            void main(){vec4 c=texture(Scene,texCoord);fragColor=vec4(c.rgb*1.12,c.a);}
            """,StandardOpenOption.CREATE_NEW);
        Files.writeString(dir.resolve("final.fsh"),"""
            #version 150
            #include "lib/finish.glsl"
            uniform sampler2D Scene;
            in vec2 texCoord;
            out vec4 fragColor;
            void main(){vec4 c=texture(Scene,texCoord);fragColor=vec4(finishLight(c.rgb),c.a);}
            """,StandardOpenOption.CREATE_NEW);
        Files.writeString(dir.resolve("lib/finish.glsl"),"vec3 finishLight(vec3 c){return c/(1.0+0.1*c);}",StandardOpenOption.CREATE_NEW);
    }
    public static long targetBytes(List<Pass> passes,int width,int height) {
        long bytes=0;
        for(Pass pass:passes) {
            long w=Math.max(1,(int)Math.ceil(width*pass.scale())),h=Math.max(1,(int)Math.ceil(height*pass.scale()));
            bytes=Math.addExact(bytes,Math.multiplyExact(Math.multiplyExact(w,h),pass.floatingPoint()?8:4));
        }
        return bytes;
    }
    private PackRepository() {}
}
