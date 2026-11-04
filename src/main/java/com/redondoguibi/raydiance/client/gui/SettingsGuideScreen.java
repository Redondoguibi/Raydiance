package com.redondoguibi.raydiance.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;

/** Readable help independent of hover duration; keyboard and mouse scrolling. */
final class SettingsGuideScreen extends Screen {
    private final Screen parent;
    private final List<Component> paragraphs;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private int offset;

    SettingsGuideScreen(Screen parent, Component title, List<Component> paragraphs) {
        super(title); this.parent=parent; this.paragraphs=List.copyOf(paragraphs);
    }
    protected void init() {
        lines.clear();
        for (var paragraph:paragraphs) {
            lines.addAll(font.split(paragraph, Math.min(480,width-40)));
            lines.add(FormattedCharSequence.EMPTY);
        }
        clamp();
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose())
                .bounds(width/2-75,height-28,150,20).build());
    }
    private int visible(){return Math.max(1,(height-76)/12);}
    private void clamp(){offset=Math.clamp(offset,0,Math.max(0,lines.size()-visible()));}
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        offset-=(int)Math.signum(vertical)*3;clamp();return true;
    }
    public boolean keyPressed(int key,int scan,int modifiers){
        switch(key){
            case 264->offset++; case 265->offset--;
            case 267->offset+=visible(); case 266->offset-=visible();
            case 268->offset=0; case 269->offset=lines.size();
            default->{return super.keyPressed(key,scan,modifiers);}
        }
        clamp();return true;
    }
    public void onClose(){minecraft.setScreen(parent);}
    public void render(GuiGraphics g,int x,int y,float partial){
        super.render(g,x,y,partial);
        g.drawCenteredString(font,title,width/2,10,0xffffff);
        int left=(width-Math.min(480,width-40))/2;
        for(int i=0;i<visible()&&offset+i<lines.size();i++)
            g.drawString(font,lines.get(offset+i),left,34+i*12,0xd5e0ed);
        if(lines.size()>visible())g.drawCenteredString(font,Component.translatable("raydiance.ui.scroll",offset+1,lines.size()),width/2,height-42,0xaaaaaa);
    }
}
