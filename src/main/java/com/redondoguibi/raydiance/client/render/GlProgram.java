package com.redondoguibi.raydiance.client.render;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

public final class GlProgram implements AutoCloseable {
    public static final String VERTEX = "#version 150\nout vec2 texCoord; void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);texCoord=p;gl_Position=vec4(p*2.0-1.0,0,1);}";
    /** One active uniform as reported by the driver after linking. */
    public record Active(String name,int type,int size,int location) {}
    private final int id;
    private final Map<String,Integer> locations=new HashMap<>();
    private final Map<Integer,int[]> uploadedValues=new HashMap<>();
    private final Map<Integer,Matrix4f> uploadedMatrices=new HashMap<>();
    private final List<Active> active;
    /** Resolved binding plan owned by {@link FrameUniforms}; built once per program. */
    Object plan;
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
        List<Active> found=new ArrayList<>();
        try {
            f=shader(GL20.GL_FRAGMENT_SHADER,fragment);program=GL20.glCreateProgram();
            GL20.glAttachShader(program,v);GL20.glAttachShader(program,f);
            if(terrain){GL20.glBindAttribLocation(program,0,"Position");GL20.glBindAttribLocation(program,2,"UV0");}
            GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0) throw new IllegalArgumentException(GL20.glGetProgramInfoLog(program));
            try(MemoryStack stack=MemoryStack.stackPush()){
                var size=stack.mallocInt(1);var type=stack.mallocInt(1);
                for(int i=0;i<GL20.glGetProgrami(program,GL20.GL_ACTIVE_UNIFORMS);i++){
                    String name=GL20.glGetActiveUniform(program,i,256,size,type).replace("[0]","");
                    found.add(new Active(name,type.get(0),size.get(0),GL20.glGetUniformLocation(program,name)));
                }
            }
            ShaderUniformContract.validate(found);
        } catch(RuntimeException e) { if(program!=0)GL20.glDeleteProgram(program);throw e; }
        finally {GL20.glDeleteShader(v);if(f!=0)GL20.glDeleteShader(f);}
        id=program;active=List.copyOf(found);
    }
    public void use(){GL20.glUseProgram(id);}
    public List<Active> activeUniforms(){return active;}
    private int at(String n){return locations.computeIfAbsent(n,k->GL20.glGetUniformLocation(id,k));}
    public boolean has(String n){return at(n)>=0;}
    public boolean samples(String... names){for(String n:names)for(var a:active)if(a.name.equals(n))return true;return false;}
    public void validateScalars(Collection<String> names){
        if(names.isEmpty())return;
        for(var a:active)if(names.contains(a.name)&&(a.type!=GL20.GL_FLOAT||a.size!=1))
            throw new IllegalArgumentException("Shader setting must use a float uniform: "+a.name);
    }
    private boolean changed(int location,int x,int y,int z){
        int[] previous=uploadedValues.get(location);
        if(previous!=null&&previous[0]==x&&previous[1]==y&&previous[2]==z)return false;
        if(previous==null){previous=new int[3];uploadedValues.put(location,previous);}
        previous[0]=x;previous[1]=y;previous[2]=z;return true;
    }
    private static int bits(float v){return Float.floatToRawIntBits(v);}
    // Location-based setters with change tracking; the program must be in use.
    void set1f(int l,float v){if(l>=0&&changed(l,bits(v),0,0))GL20.glUniform1f(l,v);}
    void set1i(int l,int v){if(l>=0&&changed(l,v,0,0))GL20.glUniform1i(l,v);}
    void set2f(int l,float x,float y){if(l>=0&&changed(l,bits(x),bits(y),0))GL20.glUniform2f(l,x,y);}
    void set2i(int l,int x,int y){if(l>=0&&changed(l,x,y,0))GL20.glUniform2i(l,x,y);}
    void set3f(int l,float x,float y,float z){if(l>=0&&changed(l,bits(x),bits(y),bits(z)))GL20.glUniform3f(l,x,y,z);}
    void set3i(int l,int x,int y,int z){if(l>=0&&changed(l,x,y,z))GL20.glUniform3i(l,x,y,z);}
    void setMatrix(int l,Matrix4f value){
        if(l<0)return;
        Matrix4f previous=uploadedMatrices.get(l);if(previous!=null&&previous.equals(value))return;
        if(previous==null)uploadedMatrices.put(l,new Matrix4f(value));else previous.set(value);
        try(MemoryStack stack=MemoryStack.stackPush()){GL20.glUniformMatrix4fv(l,false,value.get(stack.mallocFloat(16)));}
    }
    public void scalar(String n,float v){set1f(at(n),v);}
    public void integer(String n,int v){set1i(at(n),v);}
    public void vec2(String n,float x,float y){set2f(at(n),x,y);}
    public void vec3(String n,float x,float y,float z){set3f(at(n),x,y,z);}
    public void ivec3(String n,int x,int y,int z){set3i(at(n),x,y,z);}
    public void matrix(String n,Matrix4f value){setMatrix(at(n),value);}
    // Texture bindings belong to the context, so only the program-owned sampler value is cached.
    public void texture(String n,int unit,int texture){int l=at(n);if(l<0)return;GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);if(changed(l,unit,0,0))GL20.glUniform1i(l,unit);}
    public void draw(){GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);}
    public void close(){GL20.glDeleteProgram(id);}
}
