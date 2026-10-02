package net.muxigame.terminal.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

public final class TerminalBrowserSession {
    public static final String HOME_URL = "mod://muxi_terminal/terminal/index.html";
    public enum Kind { HOME, BUILTIN, WEB, ACCOUNT }
    public record State(long generation,Kind kind,String title,String url,boolean loading,String error,long viewId,String launchToken,boolean rendered) {}
    private static TerminalViewClient shell,app;
    private static long generation;
    private static int viewWidth=1280,viewHeight=720,barHeight=44;
    private static State state=new State(0,Kind.HOME,"主页",HOME_URL,false,"",0,"",false);
    private static long viewSerial;
    private static String pendingLaunchToken="";
    private static boolean contentVisible=true;
    private static boolean contentClosing;
    private static final TerminalContentTransition motion=new TerminalContentTransition();
    private static final java.util.concurrent.CopyOnWriteArrayList<java.util.function.Consumer<State>> listeners=new java.util.concurrent.CopyOnWriteArrayList<>();

    private TerminalBrowserSession() {}

    public static synchronized MCEFBrowser getOrCreate() {
        if (shell == null) {
            if (!MCEF.isInitialized()) throw new IllegalStateException("MCEF is not initialized");
            shell=new TerminalViewClient(Kind.HOME,HOME_URL);
            shell.browser.resize(viewWidth,viewHeight);
            shell.start();
        }
        return shell.browser;
    }

    public static synchronized MCEFBrowser current() {
        return shell==null?null:shell.browser;
    }

    public static synchronized MCEFBrowser content(){return app==null?null:app.browser;}
    public static synchronized MCEFBrowser activeBrowser(){return app==null || !contentVisible || contentClosing?getOrCreate():app.browser;}
    public static synchronized boolean contentVisible(){return app!=null && contentVisible;}
    static synchronized float contentToolbarRatio(){return barHeight/(float)viewHeight;}
    public static synchronized TerminalContentTransition.Frame contentMotion(){return motion.frame();}
    /** Called by the supported render path; a superseding open resets contentClosing. */
    public static synchronized void finishContentFrame(){if(contentClosing && !motion.frame().animating())home();}
    public static synchronized State state(){return state;}
    public static synchronized long generation(){return generation;}
    public static void addStateListener(java.util.function.Consumer<State> listener){listeners.addIfAbsent(listener);}
    public static void removeStateListener(java.util.function.Consumer<State> listener){listeners.remove(listener);}
    private static void publish(){
        State snapshot=state;
        Minecraft.getInstance().execute(()->{
            if(state()!=snapshot)return;
            if(current()!=null)current().executeJavaScript("window.terminalOnViewState?.("+new com.google.gson.Gson().toJson(snapshot)+")",HOME_URL,0);
            for(var listener:listeners){try{listener.accept(snapshot);}catch(RuntimeException ignored){}}
        });
    }
    public static synchronized void resizeViews(int width,int height,int toolbar){
        viewWidth=Math.max(1,width);viewHeight=Math.max(2,height);barHeight=Math.min(viewHeight-1,Math.max(1,toolbar));
        if(shell!=null)shell.browser.resize(viewWidth,viewHeight);
        if(app!=null)app.browser.resize(viewWidth,Math.max(1,viewHeight-barHeight));
    }

    public static synchronized void home() {
        closeContent();getOrCreate().executeJavaScript("window.terminalShellHome?.()",HOME_URL,0);
    }

    public static synchronized void closeContent(){
        TerminalPassportNavigation.clear();generation++;
        TerminalViewClient old=app;app=null;
        pendingLaunchToken="";contentVisible=true;contentClosing=false;motion.reset();
        state=new State(generation,Kind.HOME,"主页",HOME_URL,false,"",0,"",false);
        publish();
        if(old!=null)old.close();
        if(shell!=null)shell.browser.setFocus(true);
    }
    public static synchronized void back(){
        if(app!=null && app.browser.canGoBack()){TerminalPassportNavigation.clear();app.browser.goBack();}
        else requestHome();
    }
    public static synchronized void beginLaunch(String token,Kind kind,String title){
        closeContent();pendingLaunchToken=token;
        state=new State(generation,kind,title,"",true,"",0,token,false);publish();
    }
    public static synchronized boolean cancelLaunch(String token){
        if(!token.equals(state.launchToken()) && !token.equals(pendingLaunchToken))return false;closeContent();return true;
    }
    public static synchronized boolean revealView(long viewId,String token,boolean visible){
        return revealView(viewId,token,visible,false);
    }
    public static synchronized boolean revealView(long viewId,String token,boolean visible,boolean reduced){
        if(app==null || state.viewId()!=viewId || !token.equals(state.launchToken()))return false;
        if(contentClosing)return false;
        if(visible && !contentVisible)motion.open(reduced);
        contentVisible=visible;app.browser.setFocus(visible);shell.browser.setFocus(!visible);return true;
    }
    public static synchronized void animateHome(boolean reduced){
        if(app==null || !contentVisible || reduced){home();return;}
        if(contentClosing)return;
        // Disarm native/SSO access immediately. Keep only this view's pixels for the fade.
        TerminalPassportNavigation.clear();generation++;contentClosing=true;motion.close(false);
        app.browser.setFocus(false);app.browser.stopLoad();shell.browser.setFocus(true);
    }
    public static synchronized void requestHome(){
        if(shell==null){home();return;}
        shell.browser.executeJavaScript("window.terminalReturnHome ? window.terminalReturnHome() : window.muxi?.home()",HOME_URL,0);
    }

    public static synchronized void openApp(String app) {
        if(!"tasks".equals(app) && !"guide".equals(app)){home();return;}
        open(Kind.BUILTIN,HOME_URL+"#/"+app,"tasks".equals(app)?"任务":"游戏指南");
    }

    public static synchronized boolean openWebApp(String value,String name){
        try{String url=TerminalWebPolicy.normalize(value);open(Kind.WEB,url,name==null?"网页应用":name);return true;}
        catch(IllegalArgumentException e){return false;}
    }
    /** SSO keeps its one-use POST protocol; only its browser destination changes. */
    public static synchronized MCEFBrowser openAccountView(String url){
        if(!TerminalWebPolicy.account(url))throw new IllegalArgumentException("账户网址不在允许的 HTTPS 账户域名中");
        return open(Kind.ACCOUNT,url,"木夕账户");
    }

    public static synchronized boolean openExternal(String value) {
        if(!TerminalWebPolicy.account(value))return false;openAccountView(value);return true;
    }

    private static MCEFBrowser open(Kind kind,String url,String title){
        getOrCreate();String launchToken=pendingLaunchToken;closeContent();
        app=new TerminalViewClient(kind,url);
        contentVisible=launchToken.isEmpty();
        state=new State(generation,kind,title,url,true,"",++viewSerial,launchToken,false);
        publish();
        app.browser.resize(viewWidth,Math.max(1,viewHeight-barHeight));
        shell.browser.setFocus(!contentVisible);app.start();app.browser.setFocus(contentVisible);
        return app.browser;
    }
    static synchronized boolean owns(CefBrowser browser){return browser!=null &&
        ((shell!=null && browser==shell.browser) || (app!=null && browser==app.browser));}
    static synchronized boolean trusted(CefBrowser browser,CefFrame frame){
        boolean owner=browser!=null && ((shell!=null && browser==shell.browser) || (!contentClosing && app!=null && browser==app.browser && app.kind==Kind.BUILTIN));
        if(!owner || frame==null || !frame.isValid() || !frame.isMain()
            || browser.getMainFrame()==null || browser.getMainFrame().getIdentifier()!=frame.getIdentifier()
            || !TerminalWebPolicy.localDocument(frame.getURL()) || !TerminalWebPolicy.localDocument(browser.getURL()))return false;
        return true;
    }
    static synchronized boolean isShell(CefBrowser browser){return shell!=null && shell.browser==browser;}
    static synchronized void navigation(CefBrowser browser){
        if(!owns(browser))return;generation++;
        state=new State(generation,state.kind(),state.title(),state.url(),state.loading(),state.error(),state.viewId(),state.launchToken(),false);
        publish();
    }
    static synchronized void loaded(CefBrowser browser,String url,boolean loading,String error){
        if(app==null || browser!=app.browser)return;
        state=new State(generation,app.kind,state.title(),url,loading,error,state.viewId(),state.launchToken(),false);
        publish();
    }
    static synchronized void painted(CefBrowser browser){
        if(app==null || browser!=app.browser || state.loading() || state.rendered())return;
        state=new State(generation,app.kind,state.title(),state.url(),false,state.error(),state.viewId(),state.launchToken(),true);publish();
    }
    public static TerminalWebApps personalApps(){
        Minecraft mc=Minecraft.getInstance();String profile=mc.getUser().getProfileId().toString();
        return new TerminalWebApps(mc.gameDirectory.toPath().resolve("config/muxi-terminal/profiles").resolve(profile).resolve("web-apps.json"));
    }

    public static synchronized void close() {
        closeContent();TerminalViewClient old=shell;shell=null;if(old!=null)old.close();
    }
}

