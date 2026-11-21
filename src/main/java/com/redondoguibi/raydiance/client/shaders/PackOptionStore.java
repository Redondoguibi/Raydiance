package com.redondoguibi.raydiance.client.shaders;

import com.google.gson.*;
import com.redondoguibi.raydiance.Raydiance;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** User overrides live outside shaderpack folders so upgrades preserve them. */
public final class PackOptionStore {
    private final Path directory;
    private final Map<String,Map<String,Double>> values=new HashMap<>();
    private final Map<String,String> errors=new HashMap<>();
    public PackOptionStore(Path directory){this.directory=directory;}
    private static final class Live { static final PackOptionStore STORE=new PackOptionStore(ShaderPackFiles.directory().getParent().resolve("pack-options")); }
    public static PackOptionStore live(){return Live.STORE;}
    private Path file(PackRepository.Pack pack) {
        if(!ShaderPackFiles.validName(pack.id()))throw new IllegalArgumentException("Invalid settings pack");
        return directory.resolve(pack.id()+".json");
    }
    private Map<String,Double> defaults(PackRepository.Pack pack) {
        Map<String,Double> result=new LinkedHashMap<>();pack.options().forEach((k,v)->result.put("engine."+k,v));result.put("engine.quality",1d);result.put("engine.fxaa",0d);for(var option:pack.shaderOptions())result.put(option.key(),option.defaultValue());return result;
    }
    public void prepare(PackRepository.Pack pack) {
        if(values.containsKey(pack.id())){restore(pack,snapshot(pack));return;}
        Map<String,Double> result=defaults(pack);Path file=file(pack);
        if(Files.exists(file))try {
            if(Files.isSymbolicLink(directory))throw new IOException("Symbolic settings directory");
            JsonObject data=JsonParser.parseString(PackRepository.readText(file,65536)).getAsJsonObject();
            if(!data.has("format")||data.get("format").getAsDouble()!=1)throw new IOException("Unknown settings format");
            JsonObject saved=data.getAsJsonObject("values");
            if(saved==null)throw new IOException("Missing settings values");
            Map<String,Double> candidate=new LinkedHashMap<>(result);
            for(var option:pack.shaderOptions())if(saved.has(option.key())) {
                double v=saved.get(option.key()).getAsDouble();
                if(!Double.isFinite(v))throw new IOException("Non-finite shader option");
                candidate.put(option.key(),option.normalize(v));
            }
            for(String key:PackRepository.RANGES.keySet())if(saved.has("engine."+key)) {
                double v=saved.get("engine."+key).getAsDouble();PackRepository.validateOption(key,v);candidate.put("engine."+key,v);
            }
            for(String key:List.of("quality","fxaa"))if(saved.has("engine."+key)) {
                double v=saved.get("engine."+key).getAsDouble();validateMode(key,v);candidate.put("engine."+key,v);
            }
            result=candidate;errors.remove(pack.id());
        }catch(Exception ex){errors.put(pack.id(),"Ajustes inválidos; usando padrões: "+ex.getMessage());Raydiance.LOGGER.warn("Cannot load shaderpack settings for {}",pack.id(),ex);}
        values.put(pack.id(),result);
    }
    private static void validateMode(String key,double value){
        if(!Double.isFinite(value)||value!=Math.rint(value)||value<0||value>(key.equals("quality")?2:1))throw new IllegalArgumentException("Invalid native "+key);
    }
    private static void validateEngine(String key,double value){
        if(key.equals("quality")||key.equals("fxaa"))validateMode(key,value);else PackRepository.validateOption(key,value);
    }
    public double engine(PackRepository.Pack pack,String key){return values.getOrDefault(pack.id(),Map.of()).getOrDefault("engine."+key,pack.options().getOrDefault(key,com.redondoguibi.raydiance.client.profile.SettingsOptions.numbers().get(key).getDefault()));}
    public int quality(PackRepository.Pack pack){return values.getOrDefault(pack.id(),Map.of()).getOrDefault("engine.quality",1d).intValue();}
    public boolean fxaa(PackRepository.Pack pack){return values.getOrDefault(pack.id(),Map.of()).getOrDefault("engine.fxaa",0d)>.5;}
    public void engine(PackRepository.Pack pack,String key,double value){PackRepository.validateOption(key,value);values.computeIfAbsent(pack.id(),k->defaults(pack)).put("engine."+key,value);}
    public void modes(PackRepository.Pack pack,int quality,boolean fxaa){validateMode("quality",quality);var data=values.computeIfAbsent(pack.id(),k->defaults(pack));data.put("engine.quality",(double)quality);data.put("engine.fxaa",fxaa?1d:0d);}
    public String error(PackRepository.Pack pack){return errors.getOrDefault(pack.id(),"");}
    public double value(PackRepository.Pack pack,ShaderOption option){return values.getOrDefault(pack.id(),Map.of()).getOrDefault(option.key(),option.defaultValue());}
    public Map<String,Double> snapshot(PackRepository.Pack pack) {var result=defaults(pack);result.putAll(values.getOrDefault(pack.id(),Map.of()));return Map.copyOf(result);}
    /** Current define overrides in a stable order; part of the pipeline key, so changing one recompiles. */
    public Map<String,String> defineValues(PackRepository.Pack pack) {
        Map<String,String> result=new TreeMap<>();
        for(var option:pack.shaderOptions())if(option.define())result.put(option.defineName(),option.sourceValue(value(pack,option)));
        return result;
    }
    public void set(PackRepository.Pack pack,ShaderOption option,double value) {values.computeIfAbsent(pack.id(),k->defaults(pack)).put(option.key(),option.normalize(value));}
    public void restore(PackRepository.Pack pack,Map<String,Double> snapshot) {
        var result=defaults(pack);for(var option:pack.shaderOptions())if(snapshot.containsKey(option.key()))result.put(option.key(),option.normalize(snapshot.get(option.key())));
        for(var entry:snapshot.entrySet())if(entry.getKey().startsWith("engine.")){validateEngine(entry.getKey().substring(7),entry.getValue());result.put(entry.getKey(),entry.getValue());}
        values.put(pack.id(),result);
    }
    public void reset(PackRepository.Pack pack){values.put(pack.id(),defaults(pack));}
    public void save(PackRepository.Pack pack) throws IOException {
        Path file=file(pack);Files.createDirectories(directory);
        if(Files.isSymbolicLink(directory)||Files.isSymbolicLink(file))throw new IOException("Symbolic settings paths are not allowed");
        JsonObject data=new JsonObject();data.addProperty("format",1);data.add("values",new Gson().toJsonTree(snapshot(pack)));
        Path temp=Files.createTempFile(directory,"pack-options-",".tmp");
        try {
            Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(data));
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
            errors.remove(pack.id());
        }finally{Files.deleteIfExists(temp);}
    }
}
