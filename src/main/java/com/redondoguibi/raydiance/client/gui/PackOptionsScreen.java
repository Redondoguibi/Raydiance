package com.redondoguibi.raydiance.client.gui;

import com.redondoguibi.raydiance.client.shaders.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Live pack-specific sliders; the parent owns final Save/Cancel and disk persistence. */
public final class PackOptionsScreen extends Screen {
    private final RaydianceScreen parent;
    private final PackRepository.Pack pack;
    private final PackOptionStore store=PackOptionStore.live();
    private final Map<String,Double> initial;
    private final List<String> categories;
    private List<String> folder = List.of();
    private int page, pages, preset = -1;
    private Button presetButton;
    public PackOptionsScreen(RaydianceScreen parent,PackRepository.Pack pack){
        super(Component.literal(pack.title()));this.parent=parent;this.pack=pack;initial=store.snapshot(pack);
        categories = pack.shaderOptions().stream().map(ShaderOption::category).distinct().toList();
    }
    private Component text(String value){return Component.literal(value);}
    private Button button(String label,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(text(label),b->action.run()).bounds(x,y,w,20).build());}
    private String presetLabel(){return SettingsHelp.text("presets").getString()+" · "+(preset<0?SettingsHelp.text("custom").getString():pack.presets().get(preset).name());}
    private void custom(){preset=-1;if(presetButton!=null)presetButton.setMessage(text(presetLabel()));}
    private void reset(){
        store.reset(pack);
    }
    private void applyPreset(ShaderPreset selected){
        reset();selected.shaderValues().forEach((key,value)->pack.shaderOptions().stream().filter(o->o.key().equals(key)).findFirst().ifPresent(o->store.set(pack,o,value)));
    }
    private void guide(){
        var paragraphs=new ArrayList<Component>();
        paragraphs.add(Component.translatable("raydiance.ui.packGuide"));
        paragraphs.add(Component.translatable("raydiance.ui.presetTip"));
        for (var option : folderOptions()) {
            paragraphs.add(text(option.label()).copy().withStyle(net.minecraft.ChatFormatting.AQUA));
            paragraphs.add(text(option.description()));
            paragraphs.add(range(option));
        }
        minecraft.setScreen(new SettingsGuideScreen(this,text(breadcrumb()),paragraphs));
    }
    /** Define options without a declared category share a localized heading. */
    private String categoryLabel(String category){return category.equals(ShaderOption.DEFINE_CATEGORY)?Component.translatable("raydiance.ui.defineCategory").getString():category;}
    private Component range(ShaderOption option){
        if(option.kind()==ShaderOption.Kind.CHOICE)return Component.translatable("raydiance.ui.defaultChoice",option.sourceValue(option.defaultValue()),String.join(" ",option.choices()));
        return Component.translatable("raydiance.ui.default",option.defaultValue(),option.min(),option.max());
    }
    private Component details(ShaderOption option){
        var result=text(option.description()).copy().append("\n\n").append(range(option));
        if(option.define())result.append("\n").append(Component.translatable("raydiance.ui.defineRecompile"));
        return result;
    }
    private static List<String> path(String category) {
        return Arrays.stream(category.split("/")).map(String::strip).filter(part -> !part.isEmpty()).toList();
    }

    private List<ShaderOption> folderOptions() {
        return pack.shaderOptions().stream().filter(option -> path(option.category()).equals(folder)).toList();
    }

    private List<String> childFolders() {
        var names = new LinkedHashSet<String>();
        for (String category : categories) {
            var path = path(category);
            if (path.size() > folder.size() && path.subList(0, folder.size()).equals(folder)) {
                names.add(path.get(folder.size()));
            }
        }
        return List.copyOf(names);
    }

    private void open(List<String> path) {
        folder = List.copyOf(path);
        page = 0;
        rebuildWidgets();
    }

    private String breadcrumb() {
        return folder.isEmpty() ? pack.title()
                : pack.title() + " / " + String.join(" / ", folder.stream().map(this::categoryLabel).toList());
    }

    @Override
    protected void init() {
        int panel = Math.min(360, width - 20);
        int x = (width - panel) / 2;
        int column = (panel - 6) / 2;
        int count = Math.max(1, (height - 159) / 24) * 2;
        var children = childFolders();
        var options = folderOptions();
        int entries = children.size() + options.size();
        pages = Math.max(1, (entries + count - 1) / count);
        page = Math.min(page, pages - 1);
        if (!folder.isEmpty()) {
            button(Component.translatable("gui.back").getString(), x, 38, column, this::onClose);
        } else if (!pack.presets().isEmpty()) {
            presetButton = button(presetLabel(), x, 38, panel - 28, () -> {
                preset = (preset + 1) % pack.presets().size();
                applyPreset(pack.presets().get(preset));
                rebuildWidgets();
            });
            presetButton.setTooltip(Tooltip.create(Component.translatable("raydiance.ui.presetTip")));
        }
        button("?", x + panel - 22, 38, 22, this::guide)
                .setTooltip(Tooltip.create(Component.translatable("raydiance.ui.guide")));
        for (int i = 0; i < count; i++) {
            int index = page * count + i;
            if (index >= entries) break;
            int bx = x + (i % 2) * (column + 6), by = 64 + (i / 2) * 24;
            if (index < children.size()) {
                String name = children.get(index);
                var target = new ArrayList<>(folder);
                target.add(name);
                button(categoryLabel(name) + "  >", bx, by, column, () -> open(target));
                continue;
            }
            var option = options.get(index - children.size());
            if(option.kind()==ShaderOption.Kind.TOGGLE){
                var control=button(option.label()+" · "+SettingsHelp.text(store.value(pack,option)>.5?"ON":"OFF").getString(),bx,by,column,()->{store.set(pack,option,store.value(pack,option)>.5?0:1);custom();rebuildWidgets();});
                if(!option.description().isBlank()||option.define())control.setTooltip(Tooltip.create(details(option)));
            }else if(option.kind()==ShaderOption.Kind.CHOICE){
                // Iris-style cycle button over the values listed in the source comment.
                var control=button(option.label()+": "+option.sourceValue(store.value(pack,option)),bx,by,column,()->{
                    store.set(pack,option,(store.value(pack,option)+1)%option.choices().size());custom();rebuildWidgets();});
                control.setTooltip(Tooltip.create(details(option)));
            }else{
                var slider=new AbstractSliderButton(bx,by,column,20,Component.empty(),(store.value(pack,option)-option.min())/(option.max()-option.min())){
                    {updateMessage();}
                    protected void updateMessage(){double actual=option.normalize(option.min()+value*(option.max()-option.min()));String shown=option.kind()==ShaderOption.Kind.INTEGER?String.format(Locale.ROOT,"%.0f",actual):String.format(Locale.ROOT,"%.2f",actual);setMessage(text(option.label()+": "+shown));}
                    protected void applyValue(){store.set(pack,option,option.min()+value*(option.max()-option.min()));custom();}
                };
                if(!option.description().isBlank())slider.setTooltip(Tooltip.create(details(option)));addRenderableWidget(slider);
            }
        }
        if (pages > 1) {
            button("<", x, height - 69, 35, () -> { page--; rebuildWidgets(); }).active = page > 0;
            button(">", x + panel - 35, height - 69, 35, () -> { page++; rebuildWidgets(); }).active = page + 1 < pages;
        }
        button(SettingsHelp.text("resetPack").getString(), x, height - 47, panel, () -> {
            reset();
            custom();
            rebuildWidgets();
        }).setTooltip(Tooltip.create(Component.translatable("raydiance.ui.resetTip")));
        button(Component.translatable("gui.done").getString(), x, height - 24, column,
                () -> minecraft.setScreen(parent)).setTooltip(Tooltip.create(Component.translatable("raydiance.ui.preview")));
        button(SettingsHelp.text("Cancel").getString(), x + column + 6, height - 24, column, this::cancel);
    }

    private void cancel() {
        store.restore(pack, initial);
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (folder.isEmpty()) cancel();
        else open(folder.subList(0, folder.size() - 1));
    }

    @Override
    public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g, x, y, partial);
        g.drawCenteredString(font, font.plainSubstrByWidth(breadcrumb(), width - 20), width / 2, 18, 0xffffff);
        if (pages > 1) g.drawCenteredString(font, (page + 1) + " / " + pages, width / 2, height - 63, 0xcccccc);
        String error = store.error(pack);
        if (!error.isEmpty()) g.drawCenteredString(font, font.plainSubstrByWidth(error, width - 16), width / 2, height - 80, 0xff9999);
    }
}
