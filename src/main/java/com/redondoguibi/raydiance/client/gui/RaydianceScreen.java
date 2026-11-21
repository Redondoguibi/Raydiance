package com.redondoguibi.raydiance.client.gui;

import com.redondoguibi.raydiance.Config;
import com.redondoguibi.raydiance.client.profile.DynamicProfiles;
import com.redondoguibi.raydiance.client.profile.SettingsOptions;
import com.redondoguibi.raydiance.client.pipeline.PackBackend;
import com.redondoguibi.raydiance.client.pipeline.PipelineManager;
import com.redondoguibi.raydiance.client.render.VisualPostProcessor;
import com.redondoguibi.raydiance.client.shaders.PackOptionStore;
import com.redondoguibi.raydiance.client.shaders.PackRepository;
import com.redondoguibi.raydiance.client.shaders.ShaderPackFiles;
import com.redondoguibi.raydiance.compat.direct.DirectShaderBridge;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public final class RaydianceScreen extends Screen {
    private enum Area {
        BASE("baseTab"), PACK("packTab"), DIRECT("directTab");
        final String label;
        Area(String label) { this.label = label; }
    }

    private enum Page {
        HOME(""), COLORS("Colors", "saturation","contrast","brightness","exposure","gamma","temperature","vignette","strength"),
        LIGHT("Lighting", "bloom","bloomThreshold","ssao","godRays","sharpen","toneMapping"),
        TERRAIN("terrain", "atmosphere","contactShadows","water","reflections","wind","fogDistance"),
        QUALITY("imageQuality", "Quality","FXAA"), FOG("fog", "Custom fog color","Fog red","Fog green","Fog blue"),
        PROFILES("Dynamic profiles", "Dynamic profiles","Edit profiles","Transition (s)","Reload profiles"),
        MATERIALS("Materiais DX12", "materialSpecular","materialWetness","materialEmissive","waterAbsorption"),
        PACK_SETTINGS("packSettings"), LIBRARY("library"), HLSL("terrainPass");

        final String label;
        final String[] controls;
        Page(String label, String... controls) { this.label = label; this.controls = controls; }
    }

    private final Screen parent;
    private final Map<ConfigValue<?>,Object> initial = new LinkedHashMap<>();
    private final Map<String,PackRepository.Pack> editedPacks = new LinkedHashMap<>();
    private final Map<String,Map<String,Double>> initialPackOptions = new LinkedHashMap<>();
    private Area area = Area.BASE;
    private Page page = Page.HOME;
    private PackRepository.Pack pack;
    private Component message = Component.empty();
    private int left, panelWidth, half, top;

    public RaydianceScreen(Screen parent) {
        super(Component.translatable("raydiance.configuration.title"));
        this.parent = parent;
    }

    private void remember(ConfigValue<?> value) { initial.putIfAbsent(value, value.get()); }

    @Override
    protected void init() {
        SettingsOptions.numbers().values().forEach(this::remember);
        for (ConfigValue<?> v : List.of(Config.ENABLE_EFFECTS, Config.DYNAMIC_PROFILES, Config.TRANSITION_SECONDS,
                Config.FXAA, Config.QUALITY, Config.FOG_CUSTOM_COLOR, Config.FOG_RED, Config.FOG_GREEN,
                Config.FOG_BLUE, Config.OPENGL_PACK, Config.PACK_BACKEND, Config.DIRECT_SHADER, Config.ENABLE_DIRECT_INTEGRATION,
                Config.DIRECT_DETAIL)) remember(v);

        panelWidth = Math.min(400, width - 20);
        left = (width - panelWidth) / 2;
        half = (panelWidth - 6) / 2;
        top = 77;
        int tabWidth = panelWidth / 3;
        for (Area tab : Area.values()) {
            var button = button(SettingsHelp.text(tab.label), left + tab.ordinal()*tabWidth, 28, tabWidth-2,
                    () -> { area = tab; open(Page.HOME); });
            button.active = tab != area;
        }

        pack = area == Area.PACK && PipelineManager.packReady() ? PipelineManager.pack() : null;
        if (area == Area.PACK && pack == null && page != Page.LIBRARY) page = Page.HOME;
        switch (page) {
            case HOME -> home();
            case PACK_SETTINGS -> categories();
            case COLORS, LIGHT, TERRAIN, MATERIALS -> {
                for (int i=0; i<page.controls.length; i++) number(page.controls[i], i);
            }
            case QUALITY -> quality();
            case FOG -> fog();
            case PROFILES -> profiles();
            case LIBRARY -> library();
            case HLSL -> hlsl();
        }

        if (page != Page.HOME) {
            button(Component.translatable("gui.back"), left, height-29, panelWidth/4, this::back);
        }
        int footerWidth = panelWidth/3;
        button(SettingsHelp.text("Cancel"), left+panelWidth-2*footerWidth-5, height-29, footerWidth, this::cancel);
        button(SettingsHelp.text("Save"), left+panelWidth-footerWidth, height-29, footerWidth, this::save);
        if (page.controls.length > 0) {
            var help = button(Component.literal("?"), left+panelWidth-20, 52, 20,
                    () -> minecraft.setScreen(new SettingsGuideScreen(this, SettingsHelp.text(page.label),
                            SettingsHelp.guide(page.controls,area.label))));
            help.setTooltip(Tooltip.create(SettingsHelp.text("guide")));
        }
    }

    private void home() {
        switch (area) {
            case BASE -> {
                toggle("baseEffects", Config.ENABLE_EFFECTS, 0);
                link(Page.QUALITY,1);
                link(Page.COLORS,2); link(Page.LIGHT,3);
                link(Page.TERRAIN,4); link(Page.FOG,5);
                link(Page.PROFILES,6);
            }
            case PACK -> {
                wide("Pack", displayId(Config.OPENGL_PACK.get()), 0, () -> files(() ->
                        choose("packHeading", PackRepository.list(), Config.OPENGL_PACK.get(), id -> {
                            Config.OPENGL_PACK.set(id);
                            VisualPostProcessor.reload();
                            DirectShaderBridge.refresh();
                        })));
                var options = rowButton("packOptions", 2, () -> {
                    trackPack();
                    minecraft.setScreen(new PackOptionsScreen(this, pack));
                });
                options.active = pack != null && (!pack.shaderOptions().isEmpty() || !pack.presets().isEmpty());
                link(Page.PACK_SETTINGS,3).active = pack != null;
                rowButton("library",4, () -> open(Page.LIBRARY));
                rowButton("diagnostics",5, () -> diagnostics(VisualPostProcessor.diagnostic()));
                var backend = PackBackend.parse(Config.PACK_BACKEND.get());
                wide("packBackend", SettingsHelp.text("backend." + backend.id()), 3, () -> {
                    Config.PACK_BACKEND.set(backend.next().id());
                    VisualPostProcessor.reload();
                    DirectShaderBridge.refresh();
                    rebuildWidgets();
                });
            }
            case DIRECT -> {
                var container = ModList.get().getModContainerById("direct");
                var factory = container.flatMap(mod -> mod.getCustomExtension(IConfigScreenFactory.class));
                var direct = wide("Direct settings", null, 0, () ->
                        minecraft.setScreen(factory.orElseThrow().createScreen(container.orElseThrow(), this)));
                direct.active = factory.isPresent() && (parent == null ||
                        !parent.getClass().getName().equals("com.redondoguibi.direct.DirectScreen"));
                if (!direct.active && factory.isPresent()) direct.setTooltip(Tooltip.create(SettingsHelp.text("finishDirectNavigation")));
                wide("terrainPass", null, 1, () -> open(Page.HLSL)).active = container.isPresent();
                if (container.isPresent()) wide("diagnostics", null, 2, () -> diagnostics(DirectShaderBridge.status()));
            }
        }
    }

    private void categories() {
        link(Page.COLORS,0); link(Page.LIGHT,1);
        link(Page.TERRAIN,2); link(Page.MATERIALS,3);
        link(Page.QUALITY,4);
        rowButton("resetPack",5, () -> { trackPack(); PackOptionStore.live().reset(pack); message=SettingsHelp.text("defaultsPreview"); });
    }

    private void quality() {
        var store = PackOptionStore.live();
        int current = pack == null ? Config.QUALITY.get() : store.quality(pack);
        boolean fxaa = pack == null ? Config.FXAA.get() : store.fxaa(pack);
        wide("Quality", SettingsHelp.text(switch(current) { case 0 -> "low"; case 2 -> "high"; default -> "medium"; }), 0, () -> {
            if (pack == null) Config.QUALITY.set((current+1)%3);
            else { trackPack(); store.modes(pack,(current+1)%3,fxaa); }
            rebuildWidgets();
        });
        wide("FXAA", onOff(fxaa), 1, () -> {
            if (pack == null) Config.FXAA.set(!fxaa);
            else { trackPack(); store.modes(pack,current,!fxaa); }
            rebuildWidgets();
        });
    }

    private void fog() {
        toggle("Custom fog color", Config.FOG_CUSTOM_COLOR, 0);
        slider("Fog red", Config.FOG_RED,0,1,2);
        slider("Fog green", Config.FOG_GREEN,0,1,3);
        slider("Fog blue", Config.FOG_BLUE,0,1,4);
    }

    private void profiles() {
        toggle("Dynamic profiles",Config.DYNAMIC_PROFILES,0);
        rowButton("Edit profiles",1, () -> minecraft.setScreen(new ProfilesScreen(this)));
        slider("Transition (s)",Config.TRANSITION_SECONDS,0,30,2);
        rowButton("Reload profiles",3, () -> { DynamicProfiles.reload(); message=Component.literal(DynamicProfiles.status()); });
    }

    private void library() {
        wide("Open shaderpacks folder",null,0, () -> files(() -> {
            Files.createDirectories(ShaderPackFiles.directory());
            Util.getPlatform().openFile(ShaderPackFiles.directory().toFile());
        }));
        wide("Reload shaders",null,1, () -> {
            VisualPostProcessor.reload(true); DirectShaderBridge.refresh(); open(Page.HOME);
        });
        wide("Install multi-pass example",null,2, () -> files(() -> {
            PackRepository.installPipelineExample(); message=SettingsHelp.text("packInstalled");
        }));
        wide("installProgramExample",null,3, () -> files(() -> {
            PackRepository.installProgramExample(); message=SettingsHelp.text("packInstalled");
        }));
    }

    private void hlsl() {
        toggle("enablePass",Config.ENABLE_DIRECT_INTEGRATION,0);
        rowButton("Reload DX12 pass",1, () -> { DirectShaderBridge.refresh(); message=SettingsHelp.text("reloadRequested"); });
        wide("HLSL",displayId(Config.DIRECT_SHADER.get()),1, () -> files(() ->
                choose("terrainPass", ShaderPackFiles.list(), Config.DIRECT_SHADER.get(), id -> {
                    ShaderPackFiles.select(id);
                    DirectShaderBridge.refresh();
                })));
        slider("Detalhe DX12",Config.DIRECT_DETAIL,0,1,4);
        rowButton("Install HLSL example",5, () -> files(() -> {
            ShaderPackFiles.installExample(); message=SettingsHelp.text("hlslInstalled");
        }));
    }

    private void number(String key,int slot) {
        var setting = SettingsOptions.numbers().get(key);
        var range = PackRepository.RANGES.get(key);
        double current = pack == null ? setting.get() : PackOptionStore.live().engine(pack,key);
        slider(Component.translatable("raydiance.configuration."+key),key,current,range[0],range[1],slot, value -> {
            if (pack == null) setting.set(value);
            else { trackPack(); PackOptionStore.live().engine(pack,key,value); }
        });
    }

    private void slider(String label, DoubleValue setting, double min, double max, int slot) {
        slider(SettingsHelp.text(label),label,setting.get(),min,max,slot,setting::set);
    }

    private void slider(Component name, String key, double current, double min, double max, int slot,
                        java.util.function.DoubleConsumer change) {
        var control = addRenderableWidget(new AbstractSliderButton(x(slot),y(slot),half,20,
                Component.empty(),(current-min)/(max-min)) {
            { updateMessage(); }
            protected void updateMessage() {
                setMessage(name.copy().append(" · ").append(String.format(Locale.ROOT,"%.2f",min+value*(max-min))));
            }
            protected void applyValue() { change.accept(min+value*(max-min)); }
        });
        control.setTooltip(Tooltip.create(SettingsHelp.description(area.label,key)));
    }

    private void toggle(String key,BooleanValue setting,int slot) {
        var control = button(SettingsHelp.text(key).copy().append(" · ").append(onOff(setting.get())),
                x(slot),y(slot),half, () -> { setting.set(!setting.get()); rebuildWidgets(); });
        SettingsHelp.tip(control,area.label,key);
    }

    private Component onOff(boolean value) { return SettingsHelp.text(value ? "ON" : "OFF"); }
    private Component displayId(String id) { return "none".equals(id) ? SettingsHelp.text("none") : Component.literal(id); }
    private int x(int slot) { return left+(slot%2)*(half+6); }
    private int y(int slot) { return top+(slot/2)*24; }

    private Button wide(String key, Component value, int row, Runnable action) {
        var label = SettingsHelp.text(key).copy();
        if (value != null) label.append(" · ").append(value);
        var control = button(label,left,top+row*24,panelWidth,action);
        SettingsHelp.tip(control,area.label,key);
        return control;
    }

    private Button rowButton(String key, int slot, Runnable action) {
        var control = button(SettingsHelp.text(key),x(slot),y(slot),half,action);
        SettingsHelp.tip(control,area.label,key);
        return control;
    }

    private Button button(Component label,int x,int y,int w,Runnable action) {
        return addRenderableWidget(Button.builder(label,b -> action.run()).bounds(x,y,w,20).build());
    }

    private Button link(Page target,int slot) { return rowButton(target.label,slot, () -> open(target)); }
    private void open(Page target) { page=target; message=Component.empty(); rebuildWidgets(); }

    private void back() {
        if (area == Area.PACK && page.controls.length > 0) open(Page.PACK_SETTINGS);
        else open(Page.HOME);
    }

    private void diagnostics(String detail) {
        minecraft.setScreen(new SettingsGuideScreen(this, SettingsHelp.text("diagnostics"),
                List.of(context(),Component.literal(detail))));
    }

    private interface Selection { void select(String id) throws IOException; }
    private void choose(String title, List<String> ids, String current, Selection select) {
        var choices = new ArrayList<String>();
        choices.add("none");
        choices.addAll(ids);
        minecraft.setScreen(new Screen(SettingsHelp.text(title)) {
            int offset = Math.max(0, choices.indexOf(current));
            protected void init() {
                int rows = Math.max(1,(height-94)/23);
                offset = Math.min(offset/rows*rows, (choices.size()-1)/rows*rows);
                int w = Math.min(320,width-20), x = (width-w)/2;
                for (int i=0; i<rows && offset+i<choices.size(); i++) {
                    String id = choices.get(offset+i);
                    var label = displayId(id).copy();
                    if (id.equals(current)) label.append("  ✓");
                    addRenderableWidget(Button.builder(label,b -> {
                        minecraft.setScreen(RaydianceScreen.this);
                        files(() -> { select.select(id); message=Component.empty(); RaydianceScreen.this.rebuildWidgets(); });
                    }).bounds(x,35+i*23,w,20).build());
                }
                addRenderableWidget(Button.builder(Component.literal("‹"),b -> { offset-=rows; rebuildWidgets(); })
                        .bounds(x,height-52,36,20).build()).active = offset>0;
                addRenderableWidget(Button.builder(Component.literal("›"),b -> { offset+=rows; rebuildWidgets(); })
                        .bounds(x+w-36,height-52,36,20).build()).active = offset+rows<choices.size();
                addRenderableWidget(Button.builder(Component.translatable("gui.back"),b -> onClose())
                        .bounds(width/2-65,height-28,130,20).build());
            }
            public void onClose() { minecraft.setScreen(RaydianceScreen.this); }
            public void render(GuiGraphics g,int x,int y,float partial) {
                super.render(g,x,y,partial);
                g.drawCenteredString(font,getTitle(),width/2,13,0xffffff);
                if (ids.isEmpty()) g.drawCenteredString(font,SettingsHelp.text("noFiles"),width/2,64,0xbacbd2);
                int rows = Math.max(1,(height-94)/23);
                g.drawCenteredString(font,(offset/rows+1)+" / "+((choices.size()-1)/rows+1),width/2,height-46,0xbacbd2);
            }
        });
    }

    private interface FileAction { void run() throws IOException; }
    private void files(FileAction action) {
        try { action.run(); }
        catch (IOException | IllegalArgumentException ex) { message = Component.literal(ex.toString()); }
    }

    private void trackPack() {
        editedPacks.put(pack.id(),pack);
        initialPackOptions.computeIfAbsent(pack.id(),id -> PackOptionStore.live().snapshot(pack));
    }

    private void save() {
        try {
            for (var edited : editedPacks.values()) PackOptionStore.live().save(edited);
            Config.SPEC.save(); DirectShaderBridge.refresh(); minecraft.setScreen(parent);
        } catch (Exception ex) { message = Component.translatable("raydiance.ui.saveError",ex.getMessage()); }
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private void cancel() {
        initial.forEach((k,v) -> ((ConfigValue)k).set(v));
        initialPackOptions.forEach((id,values) -> PackOptionStore.live().restore(editedPacks.get(id),values));
        VisualPostProcessor.reload(); DirectShaderBridge.refresh(); minecraft.setScreen(parent);
    }

    @Override public void onClose() {
        if (page == Page.HOME) cancel(); else back();
    }

    private Component context() {
        if (!message.getString().isEmpty()) return message;
        return switch (area) {
            case BASE -> PipelineManager.packSelected()
                    ? Component.translatable("raydiance.ui.basePaused", Config.OPENGL_PACK.get())
                    : SettingsHelp.text("baseContext");
            case PACK -> {
                if (PipelineManager.packSelected() && pack == null)
                    yield Component.literal(com.redondoguibi.raydiance.client.shaders.ShaderErrors.summary(PipelineManager.status(),2).replace("\n"," · "));
                if (pack == null) yield SettingsHelp.text("packContext");
                String reason = PipelineManager.decision().reason();
                yield Component.translatable("raydiance.ui.editingPack",pack.title()).copy().append(reason.isEmpty() ? "" : " · " + reason);
            }
            case DIRECT -> SettingsHelp.text(!ModList.get().isLoaded("direct") ? "directMissing"
                    : page == Page.HLSL ? "passContext" : "directContext");
        };
    }

    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        super.render(g,mx,my,partial);
        g.drawCenteredString(font,title,width/2,10,0xffffff);
        int tabWidth = panelWidth/3;
        int tabX = left+area.ordinal()*tabWidth;
        g.fill(tabX,48,tabX+tabWidth-2,50,0xff78bdc6);
        var heading = page == Page.HOME ? SettingsHelp.text(switch(area) {
            case BASE -> "baseHeading"; case PACK -> "packHeading"; case DIRECT -> "directHeading";
        }) : SettingsHelp.text(page.label);
        g.drawString(font,heading,left,58,0xe4eff0);
        var lines = font.split(context(),panelWidth);
        int count = Math.min(2,lines.size());
        for (int i=0;i<count;i++) g.drawString(font,lines.get(i),left,height-34-(count-i)*font.lineHeight,0xbacbd2);
    }
}
