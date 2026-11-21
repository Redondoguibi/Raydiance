package com.redondoguibi.raydiance.client.shaders;

import com.google.gson.*;
import java.util.*;

public record ShaderPreset(String name,Map<String,Double> options,Map<String,Double> shaderValues,int quality,boolean fxaa) {
    public ShaderPreset { options=Map.copyOf(options);shaderValues=Map.copyOf(shaderValues); }
    public static List<ShaderPreset> parse(JsonObject manifest,List<ShaderOption> definitions) {
        if(!manifest.has("presets"))return List.of();
        JsonArray array=manifest.getAsJsonArray("presets");if(array.size()>12)throw new IllegalArgumentException("Too many presets");
        List<ShaderPreset> result=new ArrayList<>();Set<String> names=new HashSet<>();
        for(JsonElement element:array) {
            JsonObject o=element.getAsJsonObject();String name=o.get("name").getAsString();
            if(name.isBlank()||name.length()>40||!names.add(name))throw new IllegalArgumentException("Invalid preset name");
            Map<String,Double> options=new HashMap<>(),values=new HashMap<>();
            if(o.has("options"))for(var e:o.getAsJsonObject("options").entrySet()) {
                double v=e.getValue().getAsDouble();PackRepository.validateOption(e.getKey(),v);options.put(e.getKey(),v);
            }
            if(o.has("shaderValues"))for(var e:o.getAsJsonObject("shaderValues").entrySet()) {
                // Define options may be named bare (BLOOM) or with their store key (define:BLOOM).
                var match=definitions.stream().filter(d->d.key().equals(e.getKey())||d.define()&&d.defineName().equals(e.getKey())).findFirst();
                // Discovered defines can differ per dimension folder; only manifest (uniform) keys must exist.
                if(match.isEmpty()&&!e.getKey().matches("[a-z][a-zA-Z0-9]{0,47}"))continue;
                var definition=match.orElseThrow(()->new IllegalArgumentException("Unknown preset shader option"));
                // Define options accept their source value ("2", "HIGH", true); uniforms stay numeric.
                double v=definition.parseStored(e.getValue());
                if(v<definition.min()||v>definition.max()||Math.abs(v-definition.normalize(v))>1e-7)throw new IllegalArgumentException("Invalid preset value");
                values.put(definition.key(),v);
            }
            double quality=o.has("quality")?o.get("quality").getAsDouble():1;
            if(quality<0||quality>2||quality!=Math.rint(quality))throw new IllegalArgumentException("Invalid preset quality");
            if(o.has("fxaa")&&(!o.get("fxaa").isJsonPrimitive()||!o.getAsJsonPrimitive("fxaa").isBoolean()))throw new IllegalArgumentException("Invalid FXAA switch");
            result.add(new ShaderPreset(name,options,values,(int)quality,o.has("fxaa")&&o.get("fxaa").getAsBoolean()));
        }
        return List.copyOf(result);
    }
}
