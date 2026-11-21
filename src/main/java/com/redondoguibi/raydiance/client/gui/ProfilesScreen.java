package com.redondoguibi.raydiance.client.gui;
import com.google.gson.*;
import com.redondoguibi.raydiance.client.profile.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;

public final class ProfilesScreen extends Screen {
    private final Screen parent;
    private List<ProfileRules.Rule> rules=new ArrayList<>();
    private int page;
    private String error="";
    public ProfilesScreen(Screen parent){super(SettingsHelp.text("Dynamic profiles"));this.parent=parent;try{DynamicProfiles.reload();rules=new ArrayList<>(DynamicProfiles.parse(Files.readString(DynamicProfiles.file())));}catch(Exception ex){error=ex.getMessage();}}
    private void button(String name,int x,int y,int width,Runnable action){addRenderableWidget(Button.builder(Component.literal(name),b->action.run()).bounds(x,y,width,20).build());}
    protected void init(){
        int x=width/2-150;
        addRenderableWidget(Button.builder(Component.literal("?"),b->minecraft.setScreen(new SettingsGuideScreen(this,SettingsHelp.text("Dynamic profiles"),SettingsHelp.guide(new String[]{"Dynamic profiles","Edit profiles","Transition (s)","Reload profiles"})))).bounds(x+275,8,25,20).build());
        int count=Math.max(1,(height-105)/25);
        page=Math.min(page,Math.max(0,(rules.size()-1)/count));
        for(int i=0;i<count;i++){int index=page*count+i;if(index>=rules.size())break;var rule=rules.get(index);
            button(rule.name()+" ("+rule.priority()+")",x,38+i*25,245,()->minecraft.setScreen(new Editor(index)));
            button("X",x+250,38+i*25,50,()->{rules.remove(index);rebuildWidgets();});}
        button("<",x,height-57,40,()->{page=Math.max(0,page-1);rebuildWidgets();});
        button(SettingsHelp.text("addProfile").getString(),x+45,height-57,210,()->minecraft.setScreen(new Editor(-1)));
        button(">",x+260,height-57,40,()->{if((page+1)*count<rules.size())page++;rebuildWidgets();});
        button(SettingsHelp.text("saveProfiles").getString(),x,height-30,145,()->{try{save();DynamicProfiles.reload();minecraft.setScreen(parent);}catch(Exception ex){error=ex.getMessage();}});
        button(SettingsHelp.text("Cancel").getString(),x+155,height-30,145,this::onClose);
    }
    private void save() throws Exception{
        JsonArray a=new JsonArray();for(var r:rules){JsonObject o=new JsonObject();o.addProperty("name",r.name());o.addProperty("dimension",r.dimension());o.addProperty("biome",r.biome());o.addProperty("weather",r.weather());o.addProperty("start",r.start());o.addProperty("end",r.end());o.addProperty("priority",r.priority());o.add("values",new Gson().toJsonTree(r.values()));a.add(o);}
        JsonObject root=new JsonObject();root.add("profiles",a);String json=new GsonBuilder().setPrettyPrinting().create().toJson(root);DynamicProfiles.parse(json);
        Path path=DynamicProfiles.file(),temp=Files.createTempFile(path.getParent(),"profiles-",".tmp");
        try{Files.writeString(temp,json);try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
    }
    public void onClose(){minecraft.setScreen(parent);}
    public void render(GuiGraphics g,int x,int y,float f){super.render(g,x,y,f);g.drawCenteredString(font,title,width/2,12,0xffffff);if(rules.isEmpty())g.drawCenteredString(font,SettingsHelp.text("emptyProfiles"),width/2,48,0xbacbd2);if(!error.isEmpty())g.drawCenteredString(font,font.plainSubstrByWidth(error,width-16),width/2,height-72,0xff7777);}
    private final class Editor extends Screen {
        private final int index;
        private final List<EditBox> fields=new ArrayList<>();
        private String error="";
        private final String[] labels={"ruleName","dimension","biome","weather","startTime","endTime","priority","optionValues"};
        Editor(int index){super(SettingsHelp.text("editRule"));this.index=index;}
        protected void init(){
            var r=index<0?new ProfileRules.Rule(SettingsHelp.text("newProfile").getString(),"*","*","*",0,0,0,Map.of("saturation",1.0)):rules.get(index);
            String values=String.join(",",r.values().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+"="+e.getValue()).toList());
            String[] initial={r.name(),r.dimension(),r.biome(),r.weather(),""+r.start(),""+r.end(),""+r.priority(),values};
            if(fields.size()==8)initial=fields.stream().map(EditBox::getValue).toArray(String[]::new);
            fields.clear();
            for(int i=0;i<8;i++){EditBox box=new EditBox(font,width/2-150+(i%2)*155,45+(i/2)*36,145,20,SettingsHelp.text(labels[i]));box.setTooltip(Tooltip.create(SettingsHelp.text(labels[i]+"Tip")));box.setMaxLength(i==7?2048:80);box.setValue(initial[i]);fields.add(addRenderableWidget(box));}
            addRenderableWidget(Button.builder(SettingsHelp.text("applyRule"),b->{try{
                Map<String,Double> map=new HashMap<>();for(String item:fields.get(7).getValue().split(",")){String[] pair=item.trim().split("=",2);if(pair.length!=2)throw new IllegalArgumentException("Use key=value, e.g. bloom=0.2");double v=Double.parseDouble(pair[1]);com.redondoguibi.raydiance.client.shaders.PackRepository.validateOption(pair[0],v);map.put(pair[0],v);}
                var next=new ProfileRules.Rule(fields.get(0).getValue(),fields.get(1).getValue(),fields.get(2).getValue(),fields.get(3).getValue(),Integer.parseInt(fields.get(4).getValue()),Integer.parseInt(fields.get(5).getValue()),Integer.parseInt(fields.get(6).getValue()),Map.copyOf(map));
                if(next.name().isBlank()||next.start()<0||next.start()>23999||next.end()<0||next.end()>23999)throw new IllegalArgumentException("Invalid name/time");
                if(index<0)rules.add(next);else rules.set(index,next);minecraft.setScreen(ProfilesScreen.this);
            }catch(Exception ex){error=ex.getMessage();}}).bounds(width/2-150,height-30,145,20).build());
            addRenderableWidget(Button.builder(SettingsHelp.text("Cancel"),b->onClose()).bounds(width/2+5,height-30,145,20).build());
        }
        public void onClose(){minecraft.setScreen(ProfilesScreen.this);}
        public void render(GuiGraphics g,int x,int y,float f){super.render(g,x,y,f);g.drawCenteredString(font,title,width/2,10,0xffffff);for(int i=0;i<8;i++)g.drawString(font,SettingsHelp.text(labels[i]),width/2-150+(i%2)*155,34+(i/2)*36,0xcccccc);if(!error.isEmpty())g.drawCenteredString(font,font.plainSubstrByWidth(error,width-16),width/2,height-44,0xff7777);}
    }
}
