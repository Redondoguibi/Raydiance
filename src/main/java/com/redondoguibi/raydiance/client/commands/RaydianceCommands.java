package com.redondoguibi.raydiance.client.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.redondoguibi.raydiance.Config;
import com.redondoguibi.raydiance.Raydiance;
import com.redondoguibi.raydiance.client.render.VisualPostProcessor;
import com.redondoguibi.raydiance.client.render.FogController;
import com.redondoguibi.raydiance.client.render.VisualSettings;
import com.redondoguibi.raydiance.client.shaders.ShaderPackFiles;
import com.redondoguibi.raydiance.compat.direct.DirectCompatibility;
import com.redondoguibi.raydiance.compat.direct.DirectShaderBridge;
import java.io.IOException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Local commands; no server permissions or Direct installation are required. */
public final class RaydianceCommands {
    private RaydianceCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Raydiance.MODID)
                .executes(RaydianceCommands::status)
                .then(Commands.literal("menu").executes(ctx -> { net.minecraft.client.Minecraft.getInstance().execute(() -> net.minecraft.client.Minecraft.getInstance().setScreen(new com.redondoguibi.raydiance.client.gui.RaydianceScreen(null))); return Command.SINGLE_SUCCESS; }))
                .then(Commands.literal("status").executes(RaydianceCommands::status))
                .then(Commands.literal("help").executes(RaydianceCommands::help))
                .then(Commands.literal("on").executes(ctx -> enabled(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> enabled(ctx, false)))
                .then(Commands.literal("reset").executes(RaydianceCommands::reset))
                .then(Commands.literal("preset")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> preset(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("fog")
                        .executes(RaydianceCommands::fogStatus)
                        .then(Commands.literal("status").executes(RaydianceCommands::fogStatus))
                        .then(Commands.literal("reset").executes(RaydianceCommands::fogReset))
                        .then(Commands.literal("color-off").executes(RaydianceCommands::fogColorOff))
                        .then(Commands.literal("distance")
                                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.25, 3.0))
                                        .executes(RaydianceCommands::fogDistance)))
                        .then(Commands.literal("color")
                                .then(Commands.argument("red", DoubleArgumentType.doubleArg(0.0, 1.0))
                                        .then(Commands.argument("green", DoubleArgumentType.doubleArg(0.0, 1.0))
                                                .then(Commands.argument("blue", DoubleArgumentType.doubleArg(0.0, 1.0))
                                                        .executes(RaydianceCommands::fogColor))))))
                .then(Commands.literal("set")
                        .then(Commands.argument("setting", StringArgumentType.word())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(-2.0, 2.0))
                                        .executes(RaydianceCommands::set))))
                .then(Commands.literal("shader")
                        .executes(RaydianceCommands::shaderStatus)
                        .then(Commands.literal("list").executes(RaydianceCommands::shaderList))
                        .then(Commands.literal("install-example").executes(RaydianceCommands::installExample))
                        .then(Commands.literal("status").executes(RaydianceCommands::shaderStatus))
                        .then(Commands.literal("reload").executes(RaydianceCommands::shaderReload))
                        .then(Commands.literal("off").executes(ctx -> shaderSelect(ctx, ShaderPackFiles.NONE)))
                        .then(Commands.literal("load")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> shaderSelect(ctx, StringArgumentType.getString(ctx, "name")))))));
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        say(ctx, "Raydiance: /raydiance on|off|reset|status");
        say(ctx, "Colors: /raydiance set <saturation|contrast|brightness|exposure|gamma|temperature|vignette|strength> <number>");
        say(ctx, "Presets: /raydiance preset <default|warm|vibrant|cinematic>");
        say(ctx, "Fog: /raydiance fog <distance <0.25-3>|color <r> <g> <b>|color-off|reset|status>");
        say(ctx, "Direct-only HLSL: /raydiance shader <list|install-example|load <name>|off|reload|status>");
        return Command.SINGLE_SUCCESS;
    }

    private static int enabled(CommandContext<CommandSourceStack> ctx, boolean value) {
        Config.ENABLE_EFFECTS.set(value);
        Config.SPEC.save();
        say(ctx, "Raydiance color grading " + (value ? "enabled" : "disabled") + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int preset(CommandContext<CommandSourceStack> ctx, String name) {
        if (!VisualSettings.preset(name)) {
            say(ctx, "Unknown preset: " + name + ". Available: default, warm, vibrant, cinematic.");
            return 0;
        }
        Config.SPEC.save();
        say(ctx, "Applied Raydiance preset: " + name);
        return Command.SINGLE_SUCCESS;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        VisualSettings.preset("default");
        FogController.reset();
        Config.SPEC.save();
        say(ctx, "Restored Raydiance defaults (color grading and fog).");
        return Command.SINGLE_SUCCESS;
    }

    private static int fogStatus(CommandContext<CommandSourceStack> ctx) {
        say(ctx, FogController.describe());
        return Command.SINGLE_SUCCESS;
    }

    private static int fogReset(CommandContext<CommandSourceStack> ctx) {
        FogController.reset();
        Config.SPEC.save();
        return fogStatus(ctx);
    }

    private static int fogColorOff(CommandContext<CommandSourceStack> ctx) {
        Config.FOG_CUSTOM_COLOR.set(false);
        Config.SPEC.save();
        return fogStatus(ctx);
    }

    private static int fogDistance(CommandContext<CommandSourceStack> ctx) {
        double value = DoubleArgumentType.getDouble(ctx, "multiplier");
        if (!FogController.setDistance(value)) {
            say(ctx, "Fog distance must be from 0.25 to 3.0.");
            return 0;
        }
        Config.SPEC.save();
        return fogStatus(ctx);
    }

    private static int fogColor(CommandContext<CommandSourceStack> ctx) {
        double red = DoubleArgumentType.getDouble(ctx, "red");
        double green = DoubleArgumentType.getDouble(ctx, "green");
        double blue = DoubleArgumentType.getDouble(ctx, "blue");
        if (!FogController.setColor(red, green, blue)) {
            say(ctx, "Fog RGB components must each be 0.0 - 1.0.");
            return 0;
        }
        Config.SPEC.save();
        return fogStatus(ctx);
    }

    private static int set(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "setting");
        double value = DoubleArgumentType.getDouble(ctx, "value");
        if (!VisualSettings.set(key, value)) {
            say(ctx, "Invalid property/value '" + key + "'. Allowed range: " + VisualSettings.range(key));
            return 0;
        }
        Config.ENABLE_EFFECTS.set(true);
        Config.SPEC.save();
        say(ctx, "Raydiance " + key + " = " + value);
        return Command.SINGLE_SUCCESS;
    }

    private static int shaderList(CommandContext<CommandSourceStack> ctx) {
        try {
            var packs = ShaderPackFiles.list();
            say(ctx, "HLSL shader directory: " + ShaderPackFiles.directory());
            say(ctx, packs.isEmpty() ? "No Raydiance HLSL files found." : "Available: " + String.join(", ", packs));
            return Command.SINGLE_SUCCESS;
        } catch (IOException exception) {
            say(ctx, "Cannot list HLSL packs: " + exception.getMessage());
            return 0;
        }
    }

    private static int installExample(CommandContext<CommandSourceStack> ctx) {
        try {
            say(ctx, "Installed example HLSL shader at " + ShaderPackFiles.installExample());
            say(ctx, "Use /raydiance shader load softwarm with Direct installed.");
            return Command.SINGLE_SUCCESS;
        } catch (IOException exception) {
            say(ctx, "Cannot install example (existing files are not overwritten): " + exception.getMessage());
            return 0;
        }
    }

    private static int shaderSelect(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            ShaderPackFiles.select(name);
            Config.SPEC.save();
            DirectShaderBridge.refresh();
            say(ctx, "Selected Raydiance Direct HLSL shader: " + name
                    + ". It only runs when a compatible Direct DX12 renderer is active.");
            return Command.SINGLE_SUCCESS;
        } catch (IOException exception) {
            say(ctx, "Cannot select shader: " + exception.getMessage());
            return 0;
        }
    }

    private static int shaderReload(CommandContext<CommandSourceStack> ctx) {
        DirectShaderBridge.refresh();
        VisualPostProcessor.reload();
        com.redondoguibi.raydiance.client.profile.DynamicProfiles.reload();
        say(ctx, "Shader and post-processing reload requested.");
        return Command.SINGLE_SUCCESS;
    }

    private static int shaderStatus(CommandContext<CommandSourceStack> ctx) {
        say(ctx, "Selected HLSL: " + ShaderPackFiles.selected());
        say(ctx, DirectShaderBridge.status());
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        say(ctx, "Raydiance active. " + VisualSettings.describe());
        say(ctx, FogController.describe());
        say(ctx, VisualPostProcessor.diagnostic());
        DirectCompatibility.Status direct = DirectCompatibility.probe();
        say(ctx, "Direct: " + direct.message());
        if (direct.apiCompatible()) {
            say(ctx, "API " + direct.apiVersion() + ", backend=" + direct.backendName()
                    + ", DX12 terrain=" + direct.nativeTerrain()
                    + ", HLSL passes=" + direct.shaderPasses() + ", depth=" + direct.depthInput());
        }
        say(ctx, DirectShaderBridge.status());
        return Command.SINGLE_SUCCESS;
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
    }
}
