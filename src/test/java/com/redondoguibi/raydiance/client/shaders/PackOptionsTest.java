package com.redondoguibi.raydiance.client.shaders;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;

class PackOptionsTest {
    @TempDir Path directory;
    private ShaderOption option(){return new ShaderOption("sky","Céu","Atmosfera","Prévia","PackSky",ShaderOption.Kind.NUMBER,0,1,.05,.75);}
    private PackRepository.Pack pack(String name){return new PackRepository.Pack(name,name,Map.of(),null,List.of(),List.of(option()),List.of());}
    @Test void savesPerPackAndRestoresAfterRestartWithoutEditingShaderFiles() throws Exception {
        var store=new PackOptionStore(directory);var a=pack("lus");var b=pack("other");store.prepare(a);store.prepare(b);
        store.set(a,option(),.3);store.save(a);store.set(b,option(),.9);store.save(b);
        var restarted=new PackOptionStore(directory);restarted.prepare(a);restarted.prepare(b);
        assertEquals(.3,restarted.value(a,option()),.00001);assertEquals(.9,restarted.value(b,option()),.00001);
        try(var files=Files.list(directory)){assertEquals(2,files.count());}
    }
    @Test void cancelSnapshotDoesNotPersistPreviewAndResetUsesPackDefaults() throws Exception {
        var store=new PackOptionStore(directory);var pack=pack("lus");store.prepare(pack);store.set(pack,option(),.4);store.save(pack);
        var snapshot=store.snapshot(pack);store.set(pack,option(),1);store.restore(pack,snapshot);assertEquals(.4,store.value(pack,option()),.00001);
        store.reset(pack);assertEquals(.75,store.value(pack,option()));
        var restarted=new PackOptionStore(directory);restarted.prepare(pack);assertEquals(.4,restarted.value(pack,option()),.00001);
    }
    @Test void corruptSavedValuesFallBackWithoutBreakingPack() throws Exception {
        Files.writeString(directory.resolve("lus.json"),"{\"format\":1,\"values\":{\"sky\":\"NaN\"}}");
        var store=new PackOptionStore(directory);var pack=pack("lus");store.prepare(pack);
        assertEquals(.75,store.value(pack,option()));assertFalse(store.error(pack).isBlank());
        store.save(pack);assertEquals("",store.error(pack));
    }
    @Test void ignoresRemovedOptionsAndClampsValuesWhenPackBoundsChange() throws Exception {
        Files.writeString(directory.resolve("lus.json"),"{\"format\":1,\"values\":{\"sky\":6,\"removed\":9,\"engine.quality\":2}}");
        var store=new PackOptionStore(directory);var pack=pack("lus");store.prepare(pack);
        assertEquals(Map.of("sky",1.0),store.snapshot(pack));
    }
    @Test void rejectsReservedUniformNamesAndDuplicateControls() {
        assertThrows(IllegalArgumentException.class,()->new ShaderOption("sky","Sky","Sky","","Scene",ShaderOption.Kind.NUMBER,0,1,.1,.5));
        JsonObject manifest=JsonParser.parseString("{\"shaderOptions\":[{\"key\":\"sky\",\"label\":\"Sky\",\"uniform\":\"PackSky\",\"min\":0,\"max\":1,\"step\":0.1,\"default\":0.5},{\"key\":\"sky\",\"label\":\"Sky\",\"uniform\":\"PackSky2\",\"min\":0,\"max\":1,\"step\":0.1,\"default\":0.5}]}").getAsJsonObject();
        assertThrows(IllegalArgumentException.class,()->ShaderOption.parse(manifest));
    }
    @Test void validatesPresetsBeforeReplacingWorkingPack() {
        var manifest=JsonParser.parseString("{\"presets\":[{\"name\":\"Low\",\"shaderValues\":{\"unknown\":0}}]}").getAsJsonObject();
        assertThrows(IllegalArgumentException.class,()->ShaderPreset.parse(manifest,List.of(option())));
        var badQuality=JsonParser.parseString("{\"presets\":[{\"name\":\"Low\",\"quality\":9}]}").getAsJsonObject();
        assertThrows(IllegalArgumentException.class,()->ShaderPreset.parse(badQuality,List.of(option())));
    }
}
