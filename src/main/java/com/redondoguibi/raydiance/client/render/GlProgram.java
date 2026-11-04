package com.redondoguibi.raydiance.client.render;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

public final class GlProgram implements AutoCloseable {
    public static final String VERTEX = "#version 150\nout vec2 texCoord; void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);texCoord=p;gl_Position=vec4(p*2.0-1.0,0,1);}";
    private final int id;
    private final Map<String,Integer> locations=new HashMap<>();
    public static String resource(String name) throws IOException {
        try(InputStream in=GlProgram.class.getResourceAsStream("/assets/raydiance/shaders/"+name)) {
            if(in==null) throw new IOException("Missing shader "+name);
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private static int shader(int type,String source) {
        int id=GL20.glCreateShader(type); GL20.glShaderSource(id,source); GL20.glCompileShader(id);
        if(GL20.glGetShaderi(id,GL20.GL_COMPILE_STATUS)==0) {
            String log=GL20.glGetShaderInfoLog(id);GL20.glDeleteShader(id);throw new IllegalArgumentException(log);
        } return id;
    }
    public GlProgram(String fragment) { this(VERTEX,fragment,false); }
    public GlProgram(String vertex,String fragment,boolean terrain) {
        int v=shader(GL20.GL_VERTEX_SHADER,vertex), f=0, program=0;
        try {
            f=shader(GL20.GL_FRAGMENT_SHADER,fragment);program=GL20.glCreateProgram();
            GL20.glAttachShader(program,v);GL20.glAttachShader(program,f);
            if(terrain){GL20.glBindAttribLocation(program,0,"Position");GL20.glBindAttribLocation(program,2,"UV0");}
            GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0) throw new IllegalArgumentException(GL20.glGetProgramInfoLog(program));
            ShaderUniformContract.validate(program);
        } catch(RuntimeException e) { if(program!=0)GL20.glDeleteProgram(program);throw e; }
        finally {GL20.glDeleteShader(v);if(f!=0)GL20.glDeleteShader(f);}
        id=program;
    }
    public void use(){GL20.glUseProgram(id);}
    private int at(String n){return locations.computeIfAbsent(n,k->GL20.glGetUniformLocation(id,k));}
    public boolean has(String n){return at(n)>=0;}
    public void validateScalars(Collection<String> names){
        if(names.isEmpty())return;
        try(MemoryStack stack=MemoryStack.stackPush()){
            var size=stack.mallocInt(1);var type=stack.mallocInt(1);
            for(int i=0;i<GL20.glGetProgrami(id,GL20.GL_ACTIVE_UNIFORMS);i++){
                String name=GL20.glGetActiveUniform(id,i,256,size,type).replace("[0]","");
                if(names.contains(name)&&(type.get(0)!=GL20.GL_FLOAT||size.get(0)!=1))
                    throw new IllegalArgumentException("Shader setting must use a float uniform: "+name);
            }
        }
    }
    public void scalar(String n,float v){int l=at(n);if(l>=0)GL20.glUniform1f(l,v);}
    public void integer(String n,int v){int l=at(n);if(l>=0)GL20.glUniform1i(l,v);}
    public void vec2(String n,float x,float y){int l=at(n);if(l>=0)GL20.glUniform2f(l,x,y);}
    public void vec3(String n,float x,float y,float z){int l=at(n);if(l>=0)GL20.glUniform3f(l,x,y,z);}
    public void ivec3(String n,int x,int y,int z){int l=at(n);if(l>=0)GL20.glUniform3i(l,x,y,z);}
    public void matrix(String n,Matrix4f value){int l=at(n);if(l<0)return;try(MemoryStack stack=MemoryStack.stackPush()){GL20.glUniformMatrix4fv(l,false,value.get(stack.mallocFloat(16)));}}
    public void texture(String n,int unit,int texture){int l=at(n);if(l<0)return;GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL20.glUniform1i(l,unit);}
    public void draw(){GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);}
    public void close(){GL20.glDeleteProgram(id);}
}
