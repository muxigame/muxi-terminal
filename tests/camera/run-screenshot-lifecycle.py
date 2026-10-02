from pathlib import Path
import importlib.util, subprocess, tempfile, os, json
import argparse
REPO=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description="Exercise production native screenshot resource wrappers with failure/cancellation/async fixtures.")
parser.add_argument('--java-home',type=Path)
parser.add_argument('--server-libraries',type=Path,required=True)
args=parser.parse_args()
spec=importlib.util.spec_from_file_location('terminal_build',REPO/'build.py');build=importlib.util.module_from_spec(spec);spec.loader.exec_module(build)
compiler,runtime=build.java_tools(args.java_home)
out=REPO/'build/screenshot-lifecycle-fixtures';out.mkdir(parents=True,exist_ok=True)
jars=list(args.server_libraries.rglob('*.jar'))
nested=out/'nested';nested.mkdir(exist_ok=True)
for jar in list(jars):
    if jar.name.endswith('-universal.jar'):jars.extend(build.nested_jars(jar,nested))
cp=os.pathsep.join(map(str,jars))
files={
'com/mojang/blaze3d/platform/NativeImage.java':'''package com.mojang.blaze3d.platform;public class NativeImage {public static int live;private boolean closed;public NativeImage(){live++;}public void close(){if(!closed){live--;closed=true;}}}''',
'com/mojang/blaze3d/pipeline/RenderTarget.java':'package com.mojang.blaze3d.pipeline;public class RenderTarget{}',
'net/minecraft/client/Screenshot.java':'package net.minecraft.client;public class Screenshot{}',
'net/minecraft/network/chat/Component.java':'package net.minecraft.network.chat;public class Component{}',
'LifecycleTest.java':r'''import java.io.File;import java.lang.reflect.*;import java.util.concurrent.*;import java.util.function.*;
import com.mojang.blaze3d.platform.NativeImage;import com.mojang.blaze3d.pipeline.RenderTarget;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;import net.muxigame.terminal.client.camera.NativeScreenshotResources;
public class LifecycleTest {
static int checks;static Runnable pending;
static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
static Object call(String name,Object... args){try{for(Method m:Class.forName("net.muxigame.terminal.client.camera.mixin.NativeScreenshotLifecycleMixin").getDeclaredMethods())if(m.getName().equals(name)){m.setAccessible(true);return m.invoke(null,args);}throw new AssertionError(name);}catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(Exception e){throw new RuntimeException(e);}}
static NativeImage capture(){return (NativeImage)call("muxi$ownImage",new RenderTarget(),(Operation<NativeImage>)(a)->new NativeImage());}
static void owned(Operation<Void> action){call("muxi$ownedCapture",new File("."),"fixture.png",new RenderTarget(),(Consumer<Object>)(a)->{},action);}
static void empty(String reason){check(NativeImage.live==0,reason);check(NativeScreenshotResources.current()==null,"render thread lease cleared: "+reason);}
public static void main(String[] args){
owned(a->{capture();return null;});empty("canceled native screenshot closes readback");
try{owned(a->{capture();throw new IllegalStateException("event failed");});throw new AssertionError();}catch(IllegalStateException expected){}empty("event listener failure releases readback");
try{owned(a->{capture();call("muxi$handoff",null,(Runnable)()->{},(Operation<Void>)(values)->{throw new RejectedExecutionException();});return null;});throw new AssertionError();}catch(RejectedExecutionException expected){}empty("rejected native IO handoff releases image");
owned(a->{capture();call("muxi$handoff",null,(Runnable)()->{},(Operation<Void>)(values)->{pending=(Runnable)values[1];return null;});return null;});check(NativeImage.live==1,"accepted IO task retains exactly its pending readback");check(NativeScreenshotResources.current()==null,"successful handoff releases thread-local reference");pending.run();pending=null;empty("native IO completion releases image");
owned(a->{capture();call("muxi$handoff",null,(Runnable)()->{throw new IllegalStateException("write error");},(Operation<Void>)(values)->{pending=(Runnable)values[1];return null;});return null;});try{pending.run();throw new AssertionError();}catch(IllegalStateException expected){}pending=null;empty("native IO error closes image even if original action fails");
owned(a->{capture();var outer=NativeScreenshotResources.current();owned(b->{capture();return null;});check(NativeImage.live==1,"nested cancel does not close outer readback");check(NativeScreenshotResources.current()==outer,"nested call restores outer lease");return null;});empty("nested screenshot leases release all references");
NativeImage download=new NativeImage();try{call("muxi$readback",download,0,true,(Operation<Void>)(a)->{throw new IllegalStateException("GL download failed");});throw new AssertionError();}catch(IllegalStateException expected){}empty("native readback failure closes allocation before it can be returned");
NativeImage flip=new NativeImage();try{call("muxi$flip",flip,(Operation<Void>)(a)->{throw new IllegalStateException("flip failed");});throw new AssertionError();}catch(IllegalStateException expected){}empty("flip failure closes allocation");
for(int i=0;i<1000;i++)owned(a->{capture();return null;});empty("1000 native cancellation scopes remain bounded without GC");
System.out.println("{\"passed\":true,\"checks\":"+checks+",\"nativeImagesLive\":"+NativeImage.live+",\"cancellationIterations\":1000,\"scope\":\"production screenshot lifecycle wrappers with deterministic native-image/executor fixtures; real mixin targets verified separately in 131 MC\"}");
}}'''}
with tempfile.TemporaryDirectory(dir=out) as temporary:
    temp=Path(temporary);sources=[]
    for name,text in files.items():
        p=temp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');sources.append(p)
    source=REPO/'src/main/java/net/muxigame/terminal/client/camera'
    sources.extend([source/'NativeScreenshotResources.java',source/'mixin/NativeScreenshotLifecycleMixin.java'])
    build.compile_java(compiler,sources,temp/'classes',cp,temp/'compile.args')
    runargs=temp/'run.args'
    runargs.write_text('\n'.join(chr(34)+v.replace('\\','/')+chr(34) for v in ['-cp',str(temp/'classes')+os.pathsep+cp,'LifecycleTest']),encoding='utf-8')
    run=subprocess.run([str(runtime),'@'+str(runargs)],capture_output=True,text=True);print(run.stdout);print(run.stderr);run.check_returncode()
    (out/'result.json').write_text(json.dumps(json.loads(run.stdout),indent=2))
