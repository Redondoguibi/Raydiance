package com.redondoguibi.raydiance.client.shaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.io.IOException;
class PackRepositoryTest {
    @TempDir Path root;
    private Path pack(String json) throws Exception {Path dir=Files.createDirectory(root.resolve("test"));Files.writeString(dir.resolve("raydiance.json"),json);return dir;}
    @Test void loadsFolderAndOptionalGlsl() throws Exception{Path dir=pack("{\"format\":1,\"name\":\"Test\",\"options\":{\"bloom\":0.2}}");Files.writeString(dir.resolve("composite.fsh"),"#version 150\nvoid main(){}");var p=PackRepository.read(root,"test");assertEquals(.2,p.options().get("bloom"));assertNotNull(p.composite());}
    @Test void materialOptionsAndPresetsAreValidatedWithoutDirect() throws Exception {
        pack("""
            {"format":1,"options":{"materialSpecular":0.2,"materialWetness":0.3,"materialEmissive":0.4,"waterAbsorption":0.5},
             "presets":[{"name":"Fast","quality":0,"fxaa":false,"options":{"waterAbsorption":0},"shaderValues":{}}]}
            """);
        var loaded=PackRepository.read(root,"test");
        assertEquals(.5,loaded.options().get("waterAbsorption"));
        assertEquals(0,loaded.presets().getFirst().options().get("waterAbsorption"));
        for(String key:loaded.options().keySet()){
            assertTrue(com.redondoguibi.raydiance.client.profile.SettingsOptions.numbers().containsKey(key));
            assertThrows(IllegalArgumentException.class,()->PackRepository.validateOption(key,1.1));
        }
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.redondoguibi.direct.api.DirectRenderService"),"Standalone tests must not require the Direct JAR");
    }
    @Test void rejectsTraversal(){assertThrows(IOException.class,()->PackRepository.read(root,"../test"));assertThrows(IOException.class,()->PackRepository.read(root,"C:/test"));}
    @Test void rejectsUnknownFormat() throws Exception{pack("{\"format\":9}");assertThrows(IOException.class,()->PackRepository.read(root,"test"));}
    @Test void rejectsUnknownOrNonFiniteOptions(){assertThrows(IllegalArgumentException.class,()->PackRepository.validateOption("unknown",.1));assertThrows(IllegalArgumentException.class,()->PackRepository.validateOption("bloom",Double.NaN));assertThrows(IllegalArgumentException.class,()->PackRepository.validateOption("water",-1));}
    @Test void boundsReadsAndRejectsEmpty() throws Exception{Path file=root.resolve("data");Files.writeString(file,"12345");assertThrows(IOException.class,()->PackRepository.readText(file,4));assertEquals("12345",PackRepository.readText(file,5));Files.writeString(file,"");assertThrows(IOException.class,()->PackRepository.readText(file,10));}
    @Test void keepsPreviousPackOnReadFailure() throws Exception{pack("{\"format\":1}");var good=PackRepository.read(root,"test");PackRepository.accept(good);assertThrows(IOException.class,()->PackRepository.read(root,"missing"));assertSame(good,PackRepository.active());PackRepository.accept(new PackRepository.Pack("none","Base",java.util.Map.of(),null));}
    @Test void rejectsExternalShaderIncludes() throws Exception{Path dir=pack("{\"format\":1}");Files.writeString(dir.resolve("composite.fsh"),"#include \"../secret\"");assertThrows(IOException.class,()->PackRepository.read(root,"test"));}
    @Test void loadsFormatTwoOrderedPassesAndIncludes() throws Exception{
        Path dir=pack("{\"format\":2,\"passes\":[{\"file\":\"composite.fsh\",\"scale\":0.5,\"target\":\"rgba16f\",\"when\":\"ssao\"},{\"file\":\"final.fsh\"}]}");
        Files.writeString(dir.resolve("composite.fsh"),"#version 150\nvoid main(){}");Files.writeString(dir.resolve("final.fsh"),"#version 150\nvoid main(){}");
        var p=PackRepository.read(root,"test");assertEquals(2,p.passes().size());assertTrue(p.passes().get(0).floatingPoint());assertEquals("ssao",p.passes().get(0).when());assertNull(p.composite());
        assertEquals(6*100*100,PackRepository.targetBytes(p.passes(),100,100));
    }
    @Test void rejectsInvalidScaleOrUnboundedPassCount() throws Exception{
        Path dir=pack("{\"format\":2,\"passes\":[{\"file\":\"c.fsh\",\"scale\":2}]}");Files.writeString(dir.resolve("c.fsh"),"#version 150\nvoid main(){}");assertThrows(IOException.class,()->PackRepository.read(root,"test"));
        Files.writeString(dir.resolve("raydiance.json"),"{\"format\":2,\"passes\":["+String.join(",",java.util.Collections.nCopies(9,"{\"file\":\"c.fsh\"}"))+"]}");assertThrows(IOException.class,()->PackRepository.read(root,"test"));
    }
    @Test void loadsTheDirectExampleAsOnePackWithThreeStages() throws Exception {
        var examples = Path.of("src/main/resources/assets/raydiance/examples");
        var pack = PackRepository.read(examples, "direct_example");
        assertTrue(pack.canRunDirect());
        assertFalse(pack.hasGlsl());
        assertEquals("terrain.hlsl", pack.nativePrograms().terrain().getFirst().file());
        assertEquals(java.util.List.of("grade.hlsl", "final.hlsl"),
                pack.nativePrograms().world().stream().map(PackRepository.NativePass::file).toList());
        assertTrue(pack.nativePrograms().world().getFirst().hdr());
        assertFalse(pack.nativePrograms().world().getLast().hdr());
        assertTrue(pack.nativePrograms().geometry().sceneInputs());
        assertEquals("Terrain / Water", pack.uniformOptions().get(1).category());
        assertEquals(0, pack.presets().getFirst().shaderValues().get("refraction"));
    }

    @Test void nativePacksRejectStandaloneSettingsAndInvalidSceneInputs() throws Exception {
        Path directory = pack("""
                {"format":4,"terrainPasses":["terrain.hlsl"],"options":{"bloom":0.5}}
                """);
        var error = assertThrows(IOException.class, () -> PackRepository.read(root, "test"));
        assertTrue(error.getMessage().contains("shaderOptions"));
        Files.writeString(directory.resolve("raydiance.json"), """
                {"format":4,"geometry":{"file":"geometry.hlsl","sceneInputs":true}}
                """);
        error = assertThrows(IOException.class, () -> PackRepository.read(root, "test"));
        assertTrue(error.getMessage().contains("requires translucent"));
    }

}
