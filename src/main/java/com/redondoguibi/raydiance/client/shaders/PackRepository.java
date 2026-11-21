package com.redondoguibi.raydiance.client.shaders;

import com.google.gson.*;
import com.redondoguibi.raydiance.Config;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Folder or zip packs: a manifest, optional GLSL programs and an optional {@code direct.hlsl}.
 * No native code, network access or paths outside the pack.
 *
 * <p>Formats: 1 = one optional custom composite; 2 = built-in effects + ordered post passes;
 * 3 = Iris-style {@code composite*.fsh}/{@code final.fsh} programs over {@code colortex0-7}.</p>
 */
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
    public static final int MAX_PROGRAMS = 33;
    public static final String MANIFEST = "raydiance.json", HLSL = "direct.hlsl";
    private static final Pattern FOLDER = Pattern.compile("[A-Za-z0-9_-]{1,48}");
    private static final Map<String,String> WORLD_FOLDERS = Map.of("minecraft:overworld","world0","minecraft:the_nether","world-1","minecraft:the_end","world1");

    public record Pass(String file, String source, double scale, boolean floatingPoint, String when) {}
    public record BufferSpec(int index, ProgramDirectives.BufferFormat format, double scale, boolean clear, float[] clearColor, boolean mipmap) {
        public long bytes(int width,int height){
            long w=Math.max(1,(long)Math.ceil(width*scale)),h=Math.max(1,(long)Math.ceil(height*scale));
            return Math.multiplyExact(Math.multiplyExact(w,h),format.bytes);
        }
    }
    /** A format-3 stage. {@code when} gates on an engine option, {@code enabledBy} on a define toggle. */
    public record Program(String name, String file, String source, ProgramDirectives directives, String when, String enabledBy) {}
    public record LoadContext(String dimension, String language) {
        public static final LoadContext DEFAULT = new LoadContext("minecraft:overworld","en_us");
    }
    public record Pack(String id,String title,int format,Map<String,Double> options,String composite,List<Pass> passes,
                       List<Program> programs,Map<Integer,BufferSpec> buffers,boolean builtinEffects,String hlsl,
                       Map<Integer,String> sourceNames,List<ShaderOption> shaderOptions,List<ShaderPreset> presets) {
        public Pack(String id,String title,Map<String,Double> options,String composite) { this(id,title,options,composite,List.of()); }
        public Pack(String id,String title,Map<String,Double> options,String composite,List<Pass> passes) {this(id,title,options,composite,passes,List.of(),List.of());}
        public Pack(String id,String title,Map<String,Double> options,String composite,List<Pass> passes,List<ShaderOption> shaderOptions,List<ShaderPreset> presets) {
            this(id,title,passes.isEmpty()?1:2,options,composite,passes,List.of(),Map.of(),true,null,Map.of(),shaderOptions,presets);
        }
        public Pack {
            options=Map.copyOf(options);passes=List.copyOf(passes);programs=List.copyOf(programs);buffers=Map.copyOf(buffers);
            sourceNames=Map.copyOf(sourceNames);shaderOptions=List.copyOf(shaderOptions);presets=List.copyOf(presets);
        }
        public boolean none(){return "none".equals(id);}
        public boolean hasGlsl(){return composite!=null||!passes.isEmpty()||!programs.isEmpty();}
        public List<ShaderOption> uniformOptions(){return shaderOptions.stream().filter(o->!o.define()).toList();}
        /** Parameter-only packs use the built-in composite on either backend. */
        public boolean parameterOnly(){return !hasGlsl()&&hlsl==null&&uniformOptions().isEmpty();}
        public boolean canRunOpenGl(){return hasGlsl()||uniformOptions().isEmpty();}
        public boolean canRunDirect(){return hlsl!=null||parameterOnly();}
    }
    public static final Pack NONE = new Pack("none","Custom settings",Map.of(),null);
    private static Pack active = NONE;
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

    /** A mounted pack root; closing it releases a zip file system. */
    public static final class PackRoot implements Closeable {
        public final Path path; private final FileSystem zip;
        PackRoot(Path path,FileSystem zip){this.path=path;this.zip=zip;}
        public boolean zipped(){return zip!=null;}
        public void close() throws IOException {if(zip!=null)zip.close();}
    }
    public static PackRoot open(Path root,String id) throws IOException {
        if(!ShaderPackFiles.validName(id)) throw new IOException("Invalid pack name");
        if(Files.isSymbolicLink(root)) throw new IOException("Invalid pack directory");
        Path dir=root.resolve(id);
        if(Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS)){
            if(Files.isSymbolicLink(dir)) throw new IOException("Invalid pack directory");
            return new PackRoot(dir,null);
        }
        Path zip=root.resolve(id+".zip");
        if(!Files.isRegularFile(zip,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(zip)) throw new IOException("Invalid pack directory");
        FileSystem fs=FileSystems.newFileSystem(zip);
        try{
            Path base=fs.getPath("/");
            if(Files.isRegularFile(base.resolve(MANIFEST)))return new PackRoot(base,fs);
            List<Path> candidates;
            try(var entries=Files.list(base)){candidates=entries.filter(p->Files.isDirectory(p)&&Files.isRegularFile(p.resolve(MANIFEST))).toList();}
            if(candidates.size()!=1) throw new IOException("Zip must contain "+MANIFEST+" at its root or in one top-level folder");
            return new PackRoot(candidates.getFirst(),fs);
        }catch(IOException|RuntimeException ex){fs.close();throw ex;}
    }
    public static Pack read(Path root,String id) throws IOException { return read(root,id,LoadContext.DEFAULT); }
    public static Pack read(Path root,String id,LoadContext context) throws IOException {
        try(PackRoot mounted=open(root,id)){return parse(mounted.path,id,context);}
    }
    private static Pack parse(Path dir,String id,LoadContext context) throws IOException {
        try {
            JsonObject o=JsonParser.parseString(readText(dir.resolve(MANIFEST),65536)).getAsJsonObject();
            int format=(int)o.get("format").getAsDouble();
            if(o.get("format").getAsDouble()!=format||format<1||format>3) throw new IOException("Unsupported pack format");
            String title=o.has("name")?o.get("name").getAsString():id;
            if(title.length()>80) throw new IOException("Pack title too long");
            Map<String,Double> options=new HashMap<>();
            if(o.has("options")) for(var e:o.getAsJsonObject("options").entrySet()) { double v=e.getValue().getAsDouble();validateOption(e.getKey(),v);options.put(e.getKey(),v); }
            ShaderSourceLoader loader=new ShaderSourceLoader(dir);
            String overlay=overlay(o,dir,context.dimension());
            List<String> rawSources=new ArrayList<>();
            String composite=null;
            if(format==1&&loader.exists(pick(loader,overlay,"composite.fsh")))rawSources.add(composite=loader.load(pick(loader,overlay,"composite.fsh")));
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
                    String when=when(pass);
                    String source=loader.load(pick(loader,overlay,file));rawSources.add(source);
                    passes.add(new Pass(file,source,scale,target.equals("rgba16f"),when));
                }
            }
            List<Program> programs=new ArrayList<>();
            Map<Integer,BufferSpec> buffers=Map.of();
            if(format==3) {
                JsonObject settings=o.has("programs")?o.getAsJsonObject("programs"):new JsonObject();
                for(String name:settings.keySet())if(!programName(name))throw new IOException("Unknown program: "+name);
                for(String name:programNames()){
                    String file=pick(loader,overlay,name+".fsh");
                    if(!loader.exists(file))continue;
                    JsonObject s=settings.has(name)?settings.getAsJsonObject(name):new JsonObject();
                    if(s.has("enabled")&&s.get("enabled").isJsonPrimitive()&&s.get("enabled").getAsJsonPrimitive().isBoolean()&&!s.get("enabled").getAsBoolean())continue;
                    String source=loader.load(file);rawSources.add(source);
                    String enabledBy=s.has("enabled")&&s.get("enabled").getAsJsonPrimitive().isString()?s.get("enabled").getAsString():"";
                    try{programs.add(new Program(name,file,source,ProgramDirectives.parse(source),when(s),enabledBy));}
                    catch(IllegalArgumentException ex){throw new IOException(file+": "+ex.getMessage(),ex);}
                }
                if(programs.isEmpty()) throw new IOException("Format 3 requires composite*.fsh or final.fsh");
                buffers=buffers(o,programs);
            }
            String hlsl=null;
            ShaderSourceLoader hlslLoader=new ShaderSourceLoader(dir,ShaderSourceLoader.Dialect.HLSL);
            if(hlslLoader.exists(pick(hlslLoader,overlay,HLSL)))rawSources.add(hlsl=hlslLoader.load(pick(hlslLoader,overlay,HLSL)));
            var definitions=new ArrayList<>(ShaderOption.parse(o));
            Map<String,String> categories=new HashMap<>();
            if(o.has("defineCategories"))for(var e:o.getAsJsonObject("defineCategories").entrySet())categories.put(e.getKey(),e.getValue().getAsString());
            definitions.addAll(DefineOptions.options(DefineOptions.discover(rawSources),PackLang.load(dir,context.language()),categories));
            for(var program:programs)if(!program.enabledBy().isEmpty()&&definitions.stream().noneMatch(d->d.define()&&d.kind()==ShaderOption.Kind.TOGGLE&&d.defineName().equals(program.enabledBy())))
                throw new IOException(program.file()+": 'enabled' must name a toggle #define");
            // Formats 1/2 always run the built-in effects first; format 3 owns the image unless it opts in.
            boolean builtin=o.has("builtinEffects")?o.get("builtinEffects").getAsBoolean():format!=3;
            return new Pack(id,title,format,options,composite,passes,programs,buffers,builtin,
                    hlsl,loader.sourceNames(),definitions,ShaderPreset.parse(o,definitions));
        } catch(RuntimeException e) { throw new IOException("Invalid manifest: "+e.getMessage(),e); }
    }
    private static String when(JsonObject o) throws IOException {
        String when=o.has("when")?o.get("when").getAsString():"";
        if(!when.isEmpty()&&!RANGES.containsKey(when)) throw new IOException("Unknown conditional option: "+when);
        return when;
    }
    public static List<String> programNames(){
        List<String> names=new ArrayList<>();names.add("composite");
        for(int i=1;i<MAX_PROGRAMS-1;i++)names.add("composite"+i);
        names.add("final");return names;
    }
    private static boolean programName(String name){return programNames().contains(name);}
    /** Iris world folders (world0, world-1, world1) or a manifest {@code dimensions} mapping; files fall back to the root. */
    static String overlay(JsonObject manifest,Path dir,String dimension) throws IOException {
        String folder=WORLD_FOLDERS.get(dimension);
        if(manifest.has("dimensions")){
            for(var e:manifest.getAsJsonObject("dimensions").entrySet()){
                String value=e.getValue().getAsString();
                if(!FOLDER.matcher(value).matches()) throw new IOException("Invalid dimension folder: "+value);
                if(e.getKey().equals(dimension))folder=value;
            }
        }
        return folder!=null&&Files.isDirectory(dir.resolve(folder))&&!Files.isSymbolicLink(dir.resolve(folder))?folder:null;
    }
    private static String pick(ShaderSourceLoader loader,String overlay,String file){
        return overlay!=null&&loader.exists(overlay+"/"+file)?overlay+"/"+file:file;
    }
    private static final Pattern SAMPLED=Pattern.compile("\\b(colortex[0-7]|gcolor|gdepth|gnormal|gaux[1-4])\\b");
    static Map<Integer,BufferSpec> buffers(JsonObject manifest,List<Program> programs) throws IOException {
        Set<Integer> used=new TreeSet<>(List.of(0));
        Map<Integer,String> formats=new HashMap<>();Map<Integer,Boolean> clear=new HashMap<>();Map<Integer,float[]> colors=new HashMap<>();Set<Integer> mipmaps=new HashSet<>();
        for(var p:programs){
            used.addAll(p.directives().renderTargets());
            for(var m=SAMPLED.matcher(p.source());m.find();)used.add(ProgramDirectives.buffer(m.group(1)));
            formats.putAll(p.directives().formats());clear.putAll(p.directives().clear());colors.putAll(p.directives().clearColors());mipmaps.addAll(p.directives().mipmaps());
        }
        Map<Integer,Double> scales=new HashMap<>();
        if(manifest.has("buffers"))for(var e:manifest.getAsJsonObject("buffers").entrySet()){
            int index;
            try{index=ProgramDirectives.buffer(e.getKey());}catch(RuntimeException ex){throw new IOException("Unknown buffer: "+e.getKey());}
            used.add(index);JsonObject b=e.getValue().getAsJsonObject();
            if(b.has("format"))formats.put(index,ProgramDirectives.BufferFormat.of(b.get("format").getAsString()).name());
            if(b.has("scale"))scales.put(index,b.get("scale").getAsDouble());
            if(b.has("clear"))clear.put(index,b.get("clear").getAsBoolean());
            if(b.has("mipmap")&&b.get("mipmap").getAsBoolean())mipmaps.add(index);
            if(b.has("clearColor")){var a=b.getAsJsonArray("clearColor");StringBuilder s=new StringBuilder();for(var v:a)s.append(s.isEmpty()?"":",").append(v.getAsFloat());colors.put(index,ProgramDirectives.color(s.toString()));}
        }
        Map<Integer,BufferSpec> result=new TreeMap<>();
        for(int index:used){
            double scale=scales.getOrDefault(index,1.0);
            if(!Double.isFinite(scale)||scale<0.25||scale>1||index==0&&scale!=1) throw new IOException("Buffer scale must be 0.25..1 (colortex0: 1)");
            var format=ProgramDirectives.BufferFormat.of(formats.getOrDefault(index,"RGBA8"));
            result.put(index,new BufferSpec(index,format,scale,index!=0&&clear.getOrDefault(index,true),colors.getOrDefault(index,new float[4]),mipmaps.contains(index)));
        }
        for(var p:programs){
            double first=result.get(p.directives().renderTargets().getFirst()).scale();
            for(int t:p.directives().renderTargets())if(result.get(t).scale()!=first) throw new IOException(p.file()+": all render targets of a program need the same scale");
        }
        return result;
    }
    public static List<String> list() throws IOException {
        Path root=ShaderPackFiles.directory();Files.createDirectories(root);
        Set<String> result=new TreeSet<>();
        try(var entries=Files.list(root)) {for(Path p:entries.toList()){
            if(Files.isSymbolicLink(p))continue;
            String name=p.getFileName().toString();
            if(Files.isDirectory(p)&&ShaderPackFiles.validName(name)&&Files.isRegularFile(p.resolve(MANIFEST)))result.add(name);
            else if(Files.isRegularFile(p)&&name.endsWith(".zip")&&ShaderPackFiles.validName(name.substring(0,name.length()-4)))result.add(name.substring(0,name.length()-4));
        }}
        return List.copyOf(result);
    }
    public static Pack load(String id,LoadContext context) throws IOException {
        return id.equals("none")?NONE:read(ShaderPackFiles.directory(),id,context);
    }
    public static Pack requested() throws IOException { return load(Config.OPENGL_PACK.get(),LoadContext.DEFAULT); }
    public static void accept(Pack pack) { active=pack;error=""; }
    public static void reject(Exception ex) { error=ex.getMessage()==null?ex.toString():ex.getMessage(); }
    public static void installExample() throws IOException {
        Path root=ShaderPackFiles.directory();Files.createDirectories(root);
        if(Files.isSymbolicLink(root))throw new IOException("Symbolic shaderpack directory is not allowed");
        Path dir=root.resolve("cinematic");Files.createDirectory(dir);
        Files.writeString(dir.resolve(MANIFEST),"{\"format\":1,\"name\":\"Cinematic Light\",\"options\":{\"bloom\":0.25,\"atmosphere\":0.2,\"saturation\":1.08}}",StandardOpenOption.CREATE_NEW);
        Files.writeString(dir.resolve("composite.fsh"),com.redondoguibi.raydiance.client.render.GlProgram.resource("composite.fsh"),StandardOpenOption.CREATE_NEW);
        Files.writeString(dir.resolve(HLSL),com.redondoguibi.raydiance.client.render.GlProgram.resource("native-composite.hlsl"),StandardOpenOption.CREATE_NEW);
    }
    public static void installPipelineExample() throws IOException {
        Path root=ShaderPackFiles.directory();Files.createDirectories(root);
        if(Files.isSymbolicLink(root))throw new IOException("Symlink shaderpack directory is not allowed");
        Path dir=root.resolve("multipass_demo");Files.createDirectory(dir);Files.createDirectory(dir.resolve("lib"));
        Files.writeString(dir.resolve(HLSL),com.redondoguibi.raydiance.client.render.GlProgram.resource("native-composite.hlsl").replace("shade();return fragColor;","shade();fragColor.rgb*=1.12;fragColor.rgb=fragColor.rgb/(1+0.1*fragColor.rgb);return fragColor;"),StandardOpenOption.CREATE_NEW);
        Files.writeString(dir.resolve(MANIFEST),"""
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
    /** Installs {@code iris_style_demo}: a format-3 pack bundled in the mod resources. */
    public static void installProgramExample() throws IOException {
        Path root=ShaderPackFiles.directory();Files.createDirectories(root);
        if(Files.isSymbolicLink(root))throw new IOException("Symlink shaderpack directory is not allowed");
        Path dir=root.resolve("iris_style_demo");Files.createDirectory(dir);
        for(String file:List.of(MANIFEST,"composite.fsh","composite1.fsh","composite2.fsh","final.fsh","lib/common.glsl","world-1/final.fsh","lang/en_us.lang","lang/pt_br.lang")){
            Path target=dir.resolve(file);Files.createDirectories(target.getParent());
            Files.writeString(target,com.redondoguibi.raydiance.client.render.GlProgram.resource("examples/iris_style_demo/"+file),StandardOpenOption.CREATE_NEW);
        }
    }
    public static long targetBytes(List<Pass> passes,int width,int height) {
        long bytes=0;
        for(Pass pass:passes) {
            long w=Math.max(1,(int)Math.ceil(width*pass.scale())),h=Math.max(1,(int)Math.ceil(height*pass.scale()));
            bytes=Math.addExact(bytes,Math.multiplyExact(Math.multiplyExact(w,h),pass.floatingPoint()?8:4));
        }
        return bytes;
    }
    /** Format-3 buffers are double-buffered (ping-pong), so each allocates two textures. */
    public static long bufferBytes(Map<Integer,BufferSpec> buffers,int width,int height) {
        long bytes=0;for(var b:buffers.values())bytes=Math.addExact(bytes,Math.multiplyExact(2,b.bytes(width,height)));return bytes;
    }
    private PackRepository() {}
}
