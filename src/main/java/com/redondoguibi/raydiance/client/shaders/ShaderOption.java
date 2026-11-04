package com.redondoguibi.raydiance.client.shaders;

import com.google.gson.*;
import java.util.*;

/** Declarative pack controls. All GPU values are scalar floats, including switches. */
public record ShaderOption(String key,String label,String category,String description,String uniform,
                           Kind kind,double min,double max,double step,double defaultValue) {
    public enum Kind { NUMBER, INTEGER, TOGGLE }
    public ShaderOption {
        if(!key.matches("[a-z][a-zA-Z0-9]{0,47}")||!uniform.matches("Pack[A-Za-z0-9]{1,60}"))
            throw new IllegalArgumentException("Invalid shader option identifier");
        if(label.isBlank()||label.length()>64||category.isBlank()||category.length()>40||description.length()>400)
            throw new IllegalArgumentException("Invalid shader option text");
        if(!Double.isFinite(min)||!Double.isFinite(max)||!Double.isFinite(step)||!Double.isFinite(defaultValue)
                ||min>=max||step<=0||step>max-min||(max-min)/step>10000||defaultValue<min||defaultValue>max)
            throw new IllegalArgumentException("Invalid shader option bounds: "+key);
        if(kind==Kind.TOGGLE&&(min!=0||max!=1||step!=1)||kind==Kind.INTEGER&&(min!=Math.rint(min)||max!=Math.rint(max)||step!=Math.rint(step)))
            throw new IllegalArgumentException("Invalid discrete option: "+key);
        double snapped=min+Math.rint((defaultValue-min)/step)*step;
        if(Math.abs(snapped-defaultValue)>1e-7)throw new IllegalArgumentException("Default does not match step: "+key);
    }
    public double normalize(double value) {
        if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite shader setting");
        return Math.clamp(min+Math.round((Math.clamp(value,min,max)-min)/step)*step,min,max);
    }
    public static List<ShaderOption> parse(JsonObject manifest) {
        if(!manifest.has("shaderOptions"))return List.of();
        JsonArray array=manifest.getAsJsonArray("shaderOptions");
        if(array.size()>64)throw new IllegalArgumentException("At most 64 shader options");
        List<ShaderOption> result=new ArrayList<>();Set<String> keys=new HashSet<>(),uniforms=new HashSet<>();
        for(JsonElement element:array) {
            JsonObject o=element.getAsJsonObject();
            Kind kind=switch(o.has("type")?o.get("type").getAsString():"number") {
                case "number"->Kind.NUMBER;case "integer"->Kind.INTEGER;case "toggle"->Kind.TOGGLE;
                default->throw new IllegalArgumentException("Unknown shader option type");
            };
            var option=new ShaderOption(o.get("key").getAsString(),o.get("label").getAsString(),
                    o.has("category")?o.get("category").getAsString():"Shaderpack",
                    o.has("description")?o.get("description").getAsString():"",o.get("uniform").getAsString(),kind,
                    kind==Kind.TOGGLE?0:o.get("min").getAsDouble(),kind==Kind.TOGGLE?1:o.get("max").getAsDouble(),
                    kind==Kind.TOGGLE?1:o.get("step").getAsDouble(),o.get("default").getAsDouble());
            if(!keys.add(option.key)||!uniforms.add(option.uniform))throw new IllegalArgumentException("Duplicate shader option");
            result.add(option);
        }
        return List.copyOf(result);
    }
}
