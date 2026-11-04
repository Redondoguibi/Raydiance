package com.redondoguibi.raydiance.client.shaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class ShaderSourceLoaderTest {
    @TempDir Path root;
    private void file(String name,String source)throws Exception{Path p=root.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,source);}
    @Test void expandsNestedLocalIncludesWithLineNumbers()throws Exception{
        file("main.fsh","#version 150\n#include \"lib/a.glsl\"\nvoid main(){}");file("lib/a.glsl","#include \"b.glsl\"\nfloat a(){return b();}");file("lib/b.glsl","float b(){return 1.0;}");
        var loader=new ShaderSourceLoader(root);String s=loader.load("main.fsh");assertTrue(s.startsWith("#version 150"));assertTrue(s.contains("float b()"));assertTrue(s.contains("#line"));assertEquals(3,loader.sourceNames().size());assertFalse(s.contains("#include"));
    }
    @Test void rejectsCyclesEvenWithConditionalGuards()throws Exception{
        file("a.fsh","#version 150\n#if 0\n#include \"b.glsl\"\n#endif");file("b.glsl","#include \"a.fsh\"");assertThrows(IOException.class,()->new ShaderSourceLoader(root).load("a.fsh"));
    }
    @Test void rejectsAbsoluteTraversalAndMissingIncludes()throws Exception{
        for(String name:new String[]{"../outside.glsl","/absolute.glsl","C:/file.glsl","missing.glsl"}){
            file("main.fsh","#version 150\n#include \""+name+"\"");assertThrows(IOException.class,()->new ShaderSourceLoader(root).load("main.fsh"));
        }
    }
    @Test void ignoresCommentedIncludesAndRejectsMalformedDirective()throws Exception{
        file("main.fsh","#version 150\n/*\n#include \"missing.glsl\"\n*/\n// #include \"missing2.glsl\"\nvoid main(){}");assertDoesNotThrow(()->new ShaderSourceLoader(root).load("main.fsh"));
        file("main.fsh","#version 150\n#include <external>");assertThrows(IOException.class,()->new ShaderSourceLoader(root).load("main.fsh"));
    }
    @Test void boundsRecursiveExpansionAndRejectsLibraryVersion()throws Exception{
        file("main.fsh","#version 150\n"+"#include \"big.glsl\"\n".repeat(512));file("big.glsl","//"+"x".repeat(8192));assertThrows(IOException.class,()->new ShaderSourceLoader(root).load("main.fsh"));
        file("main.fsh","#version 150\n#include \"lib.glsl\"");file("lib.glsl","#version 150\nfloat x;");assertThrows(IOException.class,()->new ShaderSourceLoader(root).load("main.fsh"));
    }
}
