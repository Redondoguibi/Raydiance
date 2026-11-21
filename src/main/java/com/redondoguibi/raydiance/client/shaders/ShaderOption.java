package com.redondoguibi.raydiance.client.shaders;

import com.google.gson.*;
import java.util.*;

/**
 * Declarative pack controls.
 *
 * <p>{@link Binding#UNIFORM} options are live float uniforms declared in the manifest. {@link Binding#DEFINE}
 * options are discovered in the pack sources ({@code #define NAME value // [a b c]} or a referenced boolean
 * {@code #define NAME}), Iris-style, and are applied by rewriting the source before compilation. Define values
 * are stored as an index into {@link #choices()} (or 0/1 for toggles), so they share the numeric store.</p>
 */
public record ShaderOption(String key,String label,String category,String description,String uniform,
                           Kind kind,double min,double max,double step,double defaultValue,
                           Binding binding,List<String> choices) {
    public enum Kind { NUMBER, INTEGER, TOGGLE, CHOICE }
    public enum Binding { UNIFORM, DEFINE }
    public static final String DEFINE_PREFIX="define:";
    /** GUI sentinel: define options without a declared category are listed under a localized heading. */
    public static final String DEFINE_CATEGORY="#defines";

    public ShaderOption(String key,String label,String category,String description,String uniform,
                        Kind kind,double min,double max,double step,double defaultValue) {
        this(key,label,category,description,uniform,kind,min,max,step,defaultValue,Binding.UNIFORM,List.of());
    }
    public ShaderOption {
        choices=List.copyOf(choices);
        if(binding==Binding.UNIFORM){
            if(!key.matches("[a-z][a-zA-Z0-9]{0,47}")||!uniform.matches("Pack[A-Za-z0-9]{1,60}")||kind==Kind.CHOICE)
                throw new IllegalArgumentException("Invalid shader option identifier");
        }else{
            if(!key.matches("define:[A-Za-z_][A-Za-z0-9_]{0,63}")||!uniform.isEmpty()||(kind!=Kind.TOGGLE&&kind!=Kind.CHOICE))
                throw new IllegalArgumentException("Invalid define option identifier");
            if(kind==Kind.CHOICE&&(choices.size()<2||choices.size()>64||max!=choices.size()-1))
                throw new IllegalArgumentException("Invalid define choices: "+key);
        }
        if(label.isBlank()||label.length()>64||category.isBlank()||category.length()>40||description.length()>400)
            throw new IllegalArgumentException("Invalid shader option text");
        if(!Double.isFinite(min)||!Double.isFinite(max)||!Double.isFinite(step)||!Double.isFinite(defaultValue)
                ||min>=max||step<=0||step>max-min||(max-min)/step>10000||defaultValue<min||defaultValue>max)
            throw new IllegalArgumentException("Invalid shader option bounds: "+key);
        boolean discrete=kind==Kind.INTEGER||kind==Kind.CHOICE;
        if(kind==Kind.TOGGLE&&(min!=0||max!=1||step!=1)||discrete&&(min!=Math.rint(min)||max!=Math.rint(max)||step!=Math.rint(step)))
            throw new IllegalArgumentException("Invalid discrete option: "+key);
        double snapped=min+Math.rint((defaultValue-min)/step)*step;
        if(Math.abs(snapped-defaultValue)>1e-7)throw new IllegalArgumentException("Default does not match step: "+key);
    }
    public boolean define(){return binding==Binding.DEFINE;}
    /** The bare preprocessor name of a define option. */
    public String defineName(){return define()?key.substring(DEFINE_PREFIX.length()):"";}
    public double normalize(double value) {
        if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite shader setting");
        return Math.clamp(min+Math.round((Math.clamp(value,min,max)-min)/step)*step,min,max);
    }
    /** Source text for a stored value: the selected choice, or "true"/"false" for toggles. */
    public String sourceValue(double stored) {
        if(kind==Kind.TOGGLE)return normalize(stored)>.5?"true":"false";
        if(kind==Kind.CHOICE)return choices.get((int)normalize(stored));
        return Double.toString(normalize(stored));
    }
    /** Resolves a preset/JSON value to the stored representation. */
    public double parseStored(JsonElement value) {
        if(kind==Kind.CHOICE){
            String text=value.getAsJsonPrimitive().getAsString();
            for(int i=0;i<choices.size();i++)if(sameValue(choices.get(i),text))return i;
            throw new IllegalArgumentException("Unknown choice for "+key+": "+text);
        }
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isBoolean())return value.getAsBoolean()?1:0;
        return value.getAsDouble();
    }
    static boolean sameValue(String a,String b) {
        if(a.equals(b))return true;
        try{return Double.parseDouble(a)==Double.parseDouble(b);}catch(NumberFormatException ex){return false;}
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
