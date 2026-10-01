package net.muxigame.terminal.qa;
import com.google.gson.GsonBuilder;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

public final class TerminalRenderPreflight {
 public static void main(String[] args)throws Exception{
  var report=new LinkedHashMap<String,Object>();
  long window=0;ByteBuffer pixels=null;
  try{
   if(!GLFW.glfwInit())throw new IllegalStateException("GLFW initialization failed");
   GLFW.glfwDefaultWindowHints();
   GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_TRUE);
   GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);
   GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);
   GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
   window=GLFW.glfwCreateWindow(640,360,"Terminal QA - bounded render preflight",0,0);
   if(window==0)throw new IllegalStateException("GLFW window/context creation failed");
   GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();GLFW.glfwSwapInterval(1);
   report.put("renderer",GL11.glGetString(GL11.GL_RENDERER));
   report.put("vendor",GL11.glGetString(GL11.GL_VENDOR));
   report.put("version",GL11.glGetString(GL11.GL_VERSION));
   boolean expectedGpu=String.valueOf(report.get("vendor")).toLowerCase(Locale.ROOT).contains("nvidia");report.put("expectedHardwareRenderer",expectedGpu);if(!expectedGpu)throw new IllegalStateException("Expected NVIDIA hardware renderer not observed: "+report.get("renderer"));
   report.put("windowVisible",GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_VISIBLE)==GLFW.GLFW_TRUE);
   if(!Boolean.TRUE.equals(report.get("windowVisible")))throw new IllegalStateException("Window invisible");
   int[] width={0},height={0};GLFW.glfwGetFramebufferSize(window,width,height);
   report.put("framebufferWidth",width[0]);report.put("framebufferHeight",height[0]);
   if(width[0]<1 || height[0]<1)throw new IllegalStateException("Empty framebuffer");
   for(int frame=0;frame<45;frame++){
    if(GLFW.glfwWindowShouldClose(window))throw new IllegalStateException("Preflight window closed early");
    GL11.glViewport(0,0,width[0],height[0]);
    GL11.glClearColor(0.10f,0.20f,0.35f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(width[0]/4,height[0]/4,width[0]/2,height[0]/2);
    GL11.glClearColor(0.15f,0.70f,0.35f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);GL11.glDisable(GL11.GL_SCISSOR_TEST);
    if(frame==44){
     pixels=MemoryUtil.memAlloc(width[0]*height[0]*4);
     GL11.glReadPixels(0,0,width[0],height[0],GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
     BufferedImage image=new BufferedImage(width[0],height[0],BufferedImage.TYPE_INT_ARGB);
     for(int y=0;y<height[0];y++)for(int x=0;x<width[0];x++){
      int i=(y*width[0]+x)*4;
      image.setRGB(x,height[0]-1-y,0xff000000|((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));
     }
     int glError=GL11.glGetError();report.put("glError",glError);if(glError!=GL11.GL_NO_ERROR)throw new IllegalStateException("OpenGL error "+glError);
     ImageIO.write(image,"PNG",Path.of("render-preflight.png").toFile());
     int center=image.getRGB(width[0]/2,height[0]/2);
     if(((center>>>8)&255)<150)throw new IllegalStateException("Framebuffer calibration mismatch");
    }
    GLFW.glfwSwapBuffers(window);GLFW.glfwPollEvents();Thread.sleep(16);
   }
   report.put("scope","Standalone LWJGL context/framebuffer only; not Minecraft regression evidence");
   report.put("success",true);
  }catch(Throwable e){report.put("success",false);report.put("error",e.toString());throw e;}
  finally{
   if(pixels!=null)MemoryUtil.memFree(pixels);
   if(window!=0)GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
   Files.writeString(Path.of("render-preflight-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
  }
 }
}
