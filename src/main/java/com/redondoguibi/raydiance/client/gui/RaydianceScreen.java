package com.redondoguibi.raydiance.client.gui;

import com.redondoguibi.raydiance.Config;
import com.redondoguibi.raydiance.client.profile.*;
import com.redondoguibi.raydiance.client.render.*;
import com.redondoguibi.raydiance.client.shaders.*;
import com.redondoguibi.raydiance.compat.direct.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.neoforged.neoforge.common.ModConfigSpec.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import java.util.*;

/** Paginated controls stay usable at the minimum supported GUI resolution. */
public final class RaydianceScreen extends Screen {
    private final Screen parent;
    private final Map<ConfigValue<?>,Object> initial=new LinkedHashMap<>();
    private final Map<String,PackRepository.Pack> editedPacks=new LinkedHashMap<>();
    private final Map<String,Map<String,Double>> initialPackOptions=new LinkedHashMap<>();
    private int page;
    private String message="";
    private static final String[] PAGES={"Colors","Effects","Environment","Shaderpacks","Integration","Lighting","Materiais DX12"};
    private static final String[][] HELP={
        {"saturation","contrast","brightness","exposure","gamma","temperature","vignette","strength"},
        {"Effects","Quality","bloom","bloomThreshold","atmosphere","contactShadows","water","reflections","wind","FXAA"},
        {"Dynamic profiles","Edit profiles","fogDistance","Custom fog color","Fog red","Fog green","Fog blue","Transition (s)","Reload profiles"},
        {"Pack","Configurar shaderpack...","Use pack defaults","Open shaderpacks folder","Reload shaders","Save","Cancel"},
        {"Direct DX12 integration","HLSL","Detalhe DX12","Reload DX12 pass","Install HLSL example"},
        {"ssao","godRays","sharpen","toneMapping","Quality","Install multi-pass example"},
        {"materialSpecular","materialWetness","materialEmissive","waterAbsorption"}
    };
    public RaydianceScreen(Screen parent){super(Component.literal("Raydiance"));this.parent=parent;}
    private void remember(ConfigValue<?> value){initial.putIfAbsent(value,value.get());}
    @SuppressWarnings({"rawtypes","unchecked"})
    private void revert(){initial.forEach((k,v)->((ConfigValue)k).set(v));initialPackOptions.forEach((id,values)->PackOptionStore.live().restore(editedPacks.get(id),values));VisualPostProcessor.reload();DirectShaderBridge.refresh();}
    private Component text(String label){return SettingsHelp.text(label);}
    private Button button(String label,int x,int y,int w,Runnable action){var control=addRenderableWidget(Button.builder(text(label),b->action.run()).bounds(x,y,w,20).build());SettingsHelp.tip(control,label.split(": ",2)[0]);return control;}
    protected void init(){
        int x=width/2-150,y=48;
        // Snapshot every setting before any preview/edit or nested screen.
        SettingsOptions.numbers().values().forEach(this::remember);
        for(ConfigValue<?> v:List.of(Config.ENABLE_EFFECTS,Config.USE_PACK_DEFAULTS,Config.DYNAMIC_PROFILES,Config.TRANSITION_SECONDS,Config.FXAA,Config.QUALITY,
                Config.FOG_CUSTOM_COLOR,Config.FOG_RED,Config.FOG_GREEN,Config.FOG_BLUE,Config.OPENGL_PACK,Config.DIRECT_SHADER,Config.ENABLE_DIRECT_INTEGRATION,Config.DIRECT_DETAIL))remember(v);
        button("<",x,25,25,()->{page=Math.floorMod(page-1,PAGES.length);rebuildWidgets();});
        button(">",x+275,25,25,()->{page=(page+1)%PAGES.length;rebuildWidgets();});
        var help=button("?",x+243,25,25,()->minecraft.setScreen(new SettingsGuideScreen(this,SettingsHelp.text(PAGES[page]),SettingsHelp.guide(HELP[page]))));
        help.setTooltip(Tooltip.create(Component.translatable("raydiance.ui.guide")));
        if(page==0){String[] names={"saturation","contrast","brightness","exposure","gamma","temperature","vignette","strength"};for(int i=0;i<names.length;i++)number(names[i],x+(i%2)*154,y+(i/2)*25,146);}
        if(page==1){String[] names={"bloom","bloomThreshold","atmosphere","contactShadows","water","reflections","wind"};for(int i=0;i<names.length;i++)number(names[i],x+(i%2)*154,y+(i/2)*25,146);
            toggle("FXAA",Config.FXAA,x+154,y+75,146);
            button("Quality: "+Config.QUALITY.get(),x,y+105,146,()->{Config.QUALITY.set((Config.QUALITY.get()+1)%3);rebuildWidgets();});
            toggle("Effects",Config.ENABLE_EFFECTS,x+154,y+105,146);
        }
        if(page==2){toggle("Dynamic profiles",Config.DYNAMIC_PROFILES,x,y,146);button("Edit profiles",x+154,y,146,()->minecraft.setScreen(new ProfilesScreen(this)));
            number("fogDistance",x,y+25,146);toggle("Custom fog color",Config.FOG_CUSTOM_COLOR,x+154,y+25,146);
            slider("Fog red",Config.FOG_RED,0,1,x,y+50,146);slider("Fog green",Config.FOG_GREEN,0,1,x+154,y+50,146);slider("Fog blue",Config.FOG_BLUE,0,1,x,y+75,146);
            slider("Transition (s)",Config.TRANSITION_SECONDS,0,30,x+154,y+75,146);
            button("Reload profiles",x,y+105,300,()->{DynamicProfiles.reload();message=DynamicProfiles.status();});
        }
        if(page==3){
            button("Pack: "+Config.OPENGL_PACK.get(),x,y,300,()->{try{List<String> all=new ArrayList<>();all.add("none");all.addAll(PackRepository.list());Config.OPENGL_PACK.set(all.get((all.indexOf(Config.OPENGL_PACK.get())+1)%all.size()));VisualPostProcessor.reload();rebuildWidgets();}catch(Exception ex){message=ex.getMessage();}});
            button("Configurar shaderpack...",x,y+25,300,()->{
                var pack=PackRepository.active();
                if(!pack.id().equals(Config.OPENGL_PACK.get())){message="Aguarde o pack carregar antes de configurar.";return;}
                if(pack.shaderOptions().isEmpty()){message="Este pack não declara opções próprias.";return;}
                var store=PackOptionStore.live();store.prepare(pack);editedPacks.put(pack.id(),pack);initialPackOptions.putIfAbsent(pack.id(),store.snapshot(pack));
                minecraft.setScreen(new PackOptionsScreen(this,pack));
            });
            button("Open shaderpacks folder",x,y+50,300,()->{try{java.nio.file.Files.createDirectories(ShaderPackFiles.directory());Util.getPlatform().openFile(ShaderPackFiles.directory().toFile());}catch(Exception ex){message=ex.getMessage();}});
            button("Reload shaders",x,y+75,300,()->{VisualPostProcessor.reload();DirectShaderBridge.refresh();message="Reload requested";});
            button("Use pack defaults: "+(Config.USE_PACK_DEFAULTS.get()?"ON":"OFF"),x,y+100,300,()->{if(Config.USE_PACK_DEFAULTS.get())editable();else Config.USE_PACK_DEFAULTS.set(true);rebuildWidgets();});
        }
        if(page==4){toggle("Direct DX12 integration",Config.ENABLE_DIRECT_INTEGRATION,x,y,300);
            button("HLSL: "+Config.DIRECT_SHADER.get(),x,y+25,300,()->{try{var all=new ArrayList<String>();all.add("none");all.addAll(ShaderPackFiles.list());ShaderPackFiles.select(all.get((all.indexOf(Config.DIRECT_SHADER.get())+1)%all.size()));DirectShaderBridge.refresh();rebuildWidgets();}catch(Exception ex){message=ex.getMessage();}});
            button("Install HLSL example",x,y+50,146,()->{try{ShaderPackFiles.installExample();message="Installed softwarm.hlsl";}catch(Exception ex){message=ex.getMessage();}});
            var direct=button("Direct settings",x+154,y+50,146,()->ModList.get().getModContainerById("direct").ifPresent(mod->mod.getCustomExtension(IConfigScreenFactory.class).ifPresent(factory->minecraft.setScreen(factory.createScreen(mod,this)))));
            direct.active=ModList.get().getModContainerById("direct").flatMap(mod->mod.getCustomExtension(IConfigScreenFactory.class)).isPresent();
            if(parent!=null&&parent.getClass().getName().equals("com.redondoguibi.direct.DirectScreen")){
                direct.active=false;direct.setTooltip(Tooltip.create(Component.translatable("raydiance.ui.finishDirectNavigation")));
            }
            button("Reload DX12 pass",x,y+75,300,()->DirectShaderBridge.refresh());
            slider("Detalhe DX12",Config.DIRECT_DETAIL,0,1,x,y+100,300);
        }
        if(page==5){String[] names={"ssao","godRays","sharpen","toneMapping"};for(int i=0;i<names.length;i++)number(names[i],x+(i%2)*154,y+(i/2)*30,146);
            button("Install multi-pass example",x,y+100,300,()->{try{PackRepository.installPipelineExample();message="Installed multipass_demo";}catch(Exception ex){message=ex.getMessage();}});
            button("Quality: "+Config.QUALITY.get(),x,y+70,300,()->{Config.QUALITY.set((Config.QUALITY.get()+1)%3);rebuildWidgets();});
        }
        if(page==6){String[] names={"materialSpecular","materialWetness","materialEmissive","waterAbsorption"};
            for(int i=0;i<names.length;i++)number(names[i],x,y+i*25,300);
        }
        button("Save",x,height-28,146,()->{try{for(var pack:editedPacks.values())PackOptionStore.live().save(pack);Config.SPEC.save();DirectShaderBridge.refresh();minecraft.setScreen(parent);}catch(Exception ex){message="Falha ao salvar: "+ex.getMessage();}});
        button("Cancel",x+154,height-28,146,this::onClose);
    }
    private void toggle(String label,BooleanValue value,int x,int y,int w){button(label+": "+(value.get()?"ON":"OFF"),x,y,w,()->{value.set(!value.get());rebuildWidgets();});}
    private void number(String key,int x,int y,int w){double[] b=PackRepository.RANGES.get(key);slider(key,SettingsOptions.numbers().get(key),b[0],b[1],x,y,w);}
    private void editable(){if(Config.USE_PACK_DEFAULTS.get()){PackRepository.active().options().forEach((k,v)->SettingsOptions.numbers().get(k).set(v));Config.USE_PACK_DEFAULTS.set(false);}}
    private void slider(String label,DoubleValue setting,double min,double max,int x,int y,int w){
        addRenderableWidget(new AbstractSliderButton(x,y,w,20,Component.empty(),((Config.USE_PACK_DEFAULTS.get()?PackRepository.active().options().getOrDefault(label,setting.get()):setting.get())-min)/(max-min)){
            {updateMessage();SettingsHelp.tip(this,label);}
            protected void updateMessage(){setMessage(text((SettingsOptions.numbers().containsKey(label)?Component.translatable("raydiance.configuration."+label).getString():label)+": "+String.format(Locale.ROOT,"%.2f",min+value*(max-min))));}
            protected void applyValue(){if(setting!=Config.DIRECT_DETAIL)editable();setting.set(min+value*(max-min));}
        });
    }
    public void onClose(){revert();minecraft.setScreen(parent);}
    public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);g.drawCenteredString(font,title,width/2,8,0xffffff);g.drawCenteredString(font,SettingsHelp.text(PAGES[page]).getString()+"  "+(page+1)+"/"+PAGES.length,width/2,30,0xd5e6ff);
        g.drawCenteredString(font,Component.translatable("raydiance.ui.hint"),width/2,height-56,0x8fa4b8);
        String info=message.isEmpty()?((page==4||page==6)?(DirectShaderBridge.nativeGeometryActive()?TerrainEffects.status():DirectShaderBridge.status()):(page==3?VisualPostProcessor.diagnostic():SettingsHelp.text("Profile").getString()+": "+DynamicProfiles.status())):message;
        if(font.width(info)>width-16)info=font.plainSubstrByWidth(info,width-16);
        g.drawCenteredString(font,info,width/2,height-42,0xaaaaaa);
    }
}
