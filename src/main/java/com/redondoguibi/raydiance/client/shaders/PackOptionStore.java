package com.redondoguibi.raydiance.client.shaders;

import com.google.gson.*;
import com.redondoguibi.raydiance.Raydiance;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** User overrides live outside shaderpack folders so upgrades preserve them. */
public final class PackOptionStore {
    private final Path directory;
    private long revision;
    public long revision() { return revision; }

    /** Conditions refer to option values, including the actual numeric spelling of a define choice. */
    public boolean enabled(PackRepository.Pack pack, List<String> keys) {
        if (keys.isEmpty()) return true;
        for (var option : pack.shaderOptions()) {
            if (!keys.contains(option.key())) continue;
            double value = value(pack, option);
            if (option.kind() == ShaderOption.Kind.CHOICE) value = Double.parseDouble(option.sourceValue(value));
            if (value != 0) return true;
        }
        return false;
    }
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
        Map<String, Double> result = new LinkedHashMap<>();
        for (var option : pack.shaderOptions()) result.put(option.key(), option.defaultValue());
        return result;
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
            result=candidate;errors.remove(pack.id());
        }catch(Exception ex){errors.put(pack.id(),"Ajustes inválidos; usando padrões: "+ex.getMessage());Raydiance.LOGGER.warn("Cannot load shaderpack settings for {}",pack.id(),ex);}
        values.put(pack.id(),result);
        revision++;
    }
    public String error(PackRepository.Pack pack){return errors.getOrDefault(pack.id(),"");}
    public double value(PackRepository.Pack pack,ShaderOption option){return values.getOrDefault(pack.id(),Map.of()).getOrDefault(option.key(),option.defaultValue());}
    public Map<String,Double> snapshot(PackRepository.Pack pack) {var result=defaults(pack);result.putAll(values.getOrDefault(pack.id(),Map.of()));return Map.copyOf(result);}
    /** Current define overrides in a stable order; part of the pipeline key, so changing one recompiles. */
    public Map<String,String> defineValues(PackRepository.Pack pack) {
        Map<String,String> result=new TreeMap<>();
        for(var option:pack.shaderOptions())if(option.define())result.put(option.defineName(),option.sourceValue(value(pack,option)));
        return result;
    }
    public void set(PackRepository.Pack pack,ShaderOption option,double value) {values.computeIfAbsent(pack.id(),k->defaults(pack)).put(option.key(),option.normalize(value));revision++;}
    public void restore(PackRepository.Pack pack,Map<String,Double> snapshot) {
        var result=defaults(pack);for(var option:pack.shaderOptions())if(snapshot.containsKey(option.key()))result.put(option.key(),option.normalize(snapshot.get(option.key())));
        values.put(pack.id(),result);
        revision++;
    }
    public void reset(PackRepository.Pack pack){values.put(pack.id(),defaults(pack));revision++;}
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
