package com.redondoguibi.raydiance.client.gui;

import com.redondoguibi.raydiance.Config;
import com.redondoguibi.raydiance.client.profile.SettingsOptions;
import com.redondoguibi.raydiance.client.shaders.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import java.util.*;

/** Live pack-specific sliders; the parent owns final Save/Cancel and disk persistence. */
public final class PackOptionsScreen extends Screen {
    private final RaydianceScreen parent;
    private final PackRepository.Pack pack;
    private final PackOptionStore store=PackOptionStore.live();
    private final Map<String,Double> initial;
    private final Map<ConfigValue<?>,Object> initialCommon=new LinkedHashMap<>();
    private final List<String> categories;
    private int category,page,preset=-1;
    private Button presetButton;
    public PackOptionsScreen(RaydianceScreen parent,PackRepository.Pack pack){
        super(Component.literal("Configurar "+pack.title()));this.parent=parent;this.pack=pack;initial=store.snapshot(pack);
        SettingsOptions.numbers().values().forEach(v->initialCommon.put(v,v.get()));
        for(ConfigValue<?> v:List.of(Config.USE_PACK_DEFAULTS,Config.QUALITY,Config.FXAA))initialCommon.put(v,v.get());
        categories=pack.shaderOptions().stream().map(ShaderOption::category).distinct().toList();
    }
    private Component text(String value){return Component.literal(value);}
    private Button button(String label,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(text(label),b->action.run()).bounds(x,y,w,20).build());}
    private String presetLabel(){return "Perfil: "+(preset<0?"Personalizado":pack.presets().get(preset).name());}
    private void custom(){preset=-1;if(presetButton!=null)presetButton.setMessage(text(presetLabel()));}
    private void reset(){
        store.reset(pack);pack.options().forEach((k,v)->SettingsOptions.numbers().get(k).set(v));Config.USE_PACK_DEFAULTS.set(true);Config.QUALITY.set(1);Config.FXAA.set(false);
    }
    private void applyPreset(ShaderPreset selected){
        reset();selected.shaderValues().forEach((key,value)->pack.shaderOptions().stream().filter(o->o.key().equals(key)).findFirst().ifPresent(o->store.set(pack,o,value)));
        selected.options().forEach((k,v)->SettingsOptions.numbers().get(k).set(v));Config.USE_PACK_DEFAULTS.set(false);Config.QUALITY.set(selected.quality());Config.FXAA.set(selected.fxaa());
    }
    private void guide(){
        var paragraphs=new ArrayList<Component>();
        paragraphs.add(Component.translatable("raydiance.ui.packGuide"));
        paragraphs.add(Component.translatable("raydiance.ui.presetTip"));
        for(var option:pack.shaderOptions())if(option.category().equals(categories.get(category))){
            paragraphs.add(text(option.label()).copy().withStyle(net.minecraft.ChatFormatting.AQUA));
            paragraphs.add(text(option.description()));
            paragraphs.add(Component.translatable("raydiance.ui.default",option.defaultValue(),option.min(),option.max()));
        }
        minecraft.setScreen(new SettingsGuideScreen(this,text(pack.title()+" • "+categories.get(category)),paragraphs));
    }
    private Component details(ShaderOption option){
        return text(option.description()).copy().append("\n\n").append(Component.translatable("raydiance.ui.default",option.defaultValue(),option.min(),option.max()));
    }
    protected void init(){
        int x=width/2-150;int rows=Math.max(1,Math.min(4,(height-168)/25));int count=rows*2;
        var options=pack.shaderOptions().stream().filter(o->o.category().equals(categories.get(category))).toList();
        int pages=Math.max(1,(options.size()+count-1)/count);page=Math.min(page,pages-1);
        button("<",x,30,25,()->{category=Math.floorMod(category-1,categories.size());page=0;rebuildWidgets();});
        button(">",x+275,30,25,()->{category=(category+1)%categories.size();page=0;rebuildWidgets();});
        button("?",x+243,30,25,this::guide).setTooltip(Tooltip.create(Component.translatable("raydiance.ui.guide")));
        presetButton=button(presetLabel(),x,55,300,()->{if(!pack.presets().isEmpty()){preset=(preset+1)%pack.presets().size();applyPreset(pack.presets().get(preset));rebuildWidgets();}});
        presetButton.active=!pack.presets().isEmpty();
        presetButton.setTooltip(Tooltip.create(Component.translatable("raydiance.ui.presetTip")));
        for(int i=0;i<count;i++){
            int index=page*count+i;if(index>=options.size())break;var option=options.get(index);int bx=x+(i%2)*154,by=82+(i/2)*25;
            if(option.kind()==ShaderOption.Kind.TOGGLE){
                var control=button(option.label()+": "+(store.value(pack,option)>.5?"ON":"OFF"),bx,by,146,()->{store.set(pack,option,store.value(pack,option)>.5?0:1);custom();rebuildWidgets();});
                if(!option.description().isBlank())control.setTooltip(Tooltip.create(details(option)));
            }else{
                var slider=new AbstractSliderButton(bx,by,146,20,Component.empty(),(store.value(pack,option)-option.min())/(option.max()-option.min())){
                    {updateMessage();}
                    protected void updateMessage(){double actual=option.normalize(option.min()+value*(option.max()-option.min()));String shown=option.kind()==ShaderOption.Kind.INTEGER?String.format(Locale.ROOT,"%.0f",actual):String.format(Locale.ROOT,"%.2f",actual);setMessage(text(option.label()+": "+shown));}
                    protected void applyValue(){store.set(pack,option,option.min()+value*(option.max()-option.min()));custom();}
                };
                if(!option.description().isBlank())slider.setTooltip(Tooltip.create(details(option)));addRenderableWidget(slider);
            }
        }
        var previous=button("<",x,height-83,35,()->{page--;rebuildWidgets();});previous.active=page>0;
        var next=button(">",x+265,height-83,35,()->{page++;rebuildWidgets();});next.active=page+1<pages;
        button("Restaurar padrões",x,height-58,300,()->{reset();custom();rebuildWidgets();}).setTooltip(Tooltip.create(Component.translatable("raydiance.ui.resetTip")));
        button("Concluir",x,height-28,146,()->minecraft.setScreen(parent)).setTooltip(Tooltip.create(Component.translatable("raydiance.ui.preview")));button("Cancelar",x+154,height-28,146,this::onClose).setTooltip(Tooltip.create(SettingsHelp.description("Cancel")));
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public void onClose(){store.restore(pack,initial);initialCommon.forEach((key,value)->((ConfigValue)key).set(value));minecraft.setScreen(parent);}
    public void render(GuiGraphics g,int x,int y,float partial){
        super.render(g,x,y,partial);g.drawCenteredString(font,title,width/2,10,0xffffff);
        g.drawCenteredString(font,categories.get(category),width/2,36,0xd5e6ff);
        int count=Math.max(1,Math.min(4,(height-168)/25))*2;long total=pack.shaderOptions().stream().filter(o->o.category().equals(categories.get(category))).count();
        g.drawCenteredString(font,"Página "+(page+1)+"/"+Math.max(1,(total+count-1)/count),width/2,height-77,0xcccccc);
        String error=store.error(pack);if(!error.isEmpty())g.drawCenteredString(font,font.plainSubstrByWidth(error,width-16),width/2,height-97,0xff9999);
    }
}
