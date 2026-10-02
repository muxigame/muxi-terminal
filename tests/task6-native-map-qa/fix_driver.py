from pathlib import Path
p=Path(__file__).parent/'java/net/muxigame/terminal/nativeqa/NativeRuntimeQA.java'
s=p.read_text(encoding='utf-8')
lines=s.splitlines()
for i,line in enumerate(lines):
 if 'floor(home,50,0);share=' in line:
  lines[i]='''        // Optional native placement is outside the UI/warp scenarios below.
        floor(home,50,0);share=WaystonesAPI.placeSharestone(home,new BlockPos(50,64,0),DyeColor.WHITE).orElse(null);
        report.addProperty("nativeSharestonePlacementAvailable",share!=null);
        report.addProperty("nativeSharestoneWarpCovered",false);
        if(share!=null)((MutableWaystone)share).setName(Component.literal("Native sharestone"));'''
 if 'private static final String LONG_NAME=' in line:
  lines[i]=r'    private static final String LONG_NAME="\u7f51\u7edc\u77f3\u7891\u539f\u59cb\u5b8c\u6574\u540d\u79f0\u00b7\u6e05\u6668\u4e16\u754c\u00b7ABCDEFGH\u00b7\u672b\u5c3e\u5fc5\u987b\u53ef\u89c1";'
 if 'source=stone(home,' in line:
  lines[i]='        source=stone(home,new BlockPos(0,64,0),"Source stone",true);'
 if 'unactivated=stone(home,' in line:
  lines[i]='        unactivated=stone(home,new BlockPos(40,64,0),"Unactivated target",false);'
 if 'for(int i=0;i<8;i++)stone(' in line:
  lines[i]='        for(int i=0;i<8;i++)stone(home,new BlockPos(24+(i%4)*3,64,4+(i/4)*3),"Dense stone "+i+LONG_NAME,true);'
 if 'cross=stone(survival,' in line:
  lines[i]='        cross=stone(survival,new BlockPos(0,64,0),"Cross dimension stone",true);'
s='\n'.join(lines)+'\n'
s=s.replace('mc().disconnect(new TitleScreen());stage=99;age=0;','normalLogout();')
s=s.replace('Difficulty.PEACEFUL,true,new GameRules()','Difficulty.PEACEFUL,false,new GameRules()')
s=s.replace('if(mc().player==null||mc().level==null||mc().getOverlay()!=null)return;', 'if(mc().player==null||mc().level==null||mc().getOverlay()!=null||mc().screen instanceof ReceivingLevelScreen)return;')
old='''        }catch(Throwable error){report.addProperty("error",error.toString());report.addProperty("failedStage",stage);try{capture("failure-stage-"+stage);}catch(Exception ignored){}normalLogout();}'''
new='''        }catch(Throwable error){
            report.addProperty("error",error.toString());report.addProperty("failedStage",stage);report.addProperty("success",false);report.add("checks",checks);
            error.printStackTrace();
            try{capture("failure-stage-"+stage);Files.writeString(Path.of("native-map-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));java.io.StringWriter stack=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(stack));Files.writeString(Path.of("native-qa-failure-stack.txt"),stack.toString());}catch(Exception evidenceError){evidenceError.printStackTrace();}
            normalLogout();
        }'''
assert old in s
s=s.replace(old,new)
s=s.replace('    private void finish()throws Exception{','''    private void normalLogout(){
        stage=99;age=0;
        // tell queues even on the render thread; execute may run synchronously.
        // Enter closing state before any nested rendering or client events.
        mc().tell(()->mc().disconnect(new TitleScreen()));
    }
    private void finish()throws Exception{''')
assert s.count('mc().disconnect(')==1
assert '\ufffd' not in s
p.write_text(s,encoding='utf-8')
print('QA-only driver fixed; product files untouched')
