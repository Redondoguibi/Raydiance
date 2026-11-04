package com.redondoguibi.raydiance.client.gui;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import java.util.*;
final class SettingsHelp {
    static Component text(String label){
        int separator=label.indexOf(": ");
        if(separator>0){
            String prefix=label.substring(0,separator),value=label.substring(separator+2);
            if(prefix.equals("Quality"))value=switch(value){case "0"->"low";case "1"->"medium";case "2"->"high";default->value;};
            return text(prefix).copy().append(": ").append(text(value));
        }
        String key="raydiance.ui."+label;
        return I18n.exists(key)?Component.translatable(key):Component.literal(label);
    }
    static Component description(String key){
        String id="raydiance.help."+key;
        if(I18n.exists(id))return Component.translatable(id);
        return Component.translatable("raydiance.configuration."+key+".tooltip");
    }
    static void tip(AbstractWidget control,String key){
        String id="raydiance.help."+key;
        if(I18n.exists(id)||I18n.exists("raydiance.configuration."+key+".tooltip"))
            control.setTooltip(Tooltip.create(description(key)));
    }
    static List<Component> guide(String[] keys){
        var result=new ArrayList<Component>();result.add(Component.translatable("raydiance.ui.general"));
        result.add(Component.translatable("raydiance.ui.preview"));
        for(String key:keys){
            String label="raydiance.configuration."+key;
            result.add((I18n.exists(label)?Component.translatable(label):text(key)).copy().withStyle(net.minecraft.ChatFormatting.AQUA));
            result.add(description(key));
        }
        return result;
    }
}
