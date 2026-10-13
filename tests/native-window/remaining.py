import json,time,ctypes,traceback,psutil,sys
from ctypes import wintypes as W
from control import *
report={'productionChanged':False,'checks':{},'errors':{},'runId':None}
def persist():
 p=LAB/'missing-checks.json';p.write_text(json.dumps(report,ensure_ascii=True,indent=2),encoding='utf-8')
def check(name,value):
 report['checks'][name]=value;persist();print('CHECK',name,flush=True)
def step(name,fn):
 try:fn()
 except Exception as e:
  report['errors'][name]={'error':str(e),'trace':traceback.format_exc()};persist();print('FAILED',name,str(e)[:800],flush=True)
def settings_wait(test,timeout=12):
 end=time.monotonic()+timeout
 while True:
  q=s('observe')
  if test(q):return q
  if time.monotonic()>end:raise AssertionError({k:q.get(k) for k in ['screen','locale','draw','widgets','error']})
  time.sleep(.1)
def draw_label(label):
 return settings_wait(lambda q:not q['overlay'] and any(x['text']==label and x['width']>0 for x in q['draw']))
def f3t():
 s('world');before=s('observe');owner=alive();pid=owner['processes']['host']['pid']
 u=ctypes.WinDLL('user32',use_last_error=True);u.GetForegroundWindow.restype=W.HWND;u.GetWindowThreadProcessId.argtypes=[W.HWND,ctypes.POINTER(W.DWORD)];u.SetForegroundWindow.argtypes=[W.HWND];u.GetClientRect.argtypes=[W.HWND,ctypes.POINTER(W.RECT)];u.MapVirtualKeyW.argtypes=[W.UINT,W.UINT];u.MapVirtualKeyW.restype=W.UINT
 found=[];CB=ctypes.WINFUNCTYPE(W.BOOL,W.HWND,W.LPARAM)
 @CB
 def collect(hwnd,arg):
  q=W.DWORD();u.GetWindowThreadProcessId(hwnd,ctypes.byref(q));rect=W.RECT();u.GetClientRect(hwnd,ctypes.byref(rect))
  klass=ctypes.create_unicode_buffer(256);u.GetClassNameW(hwnd,klass,256)
  if q.value==pid and klass.value.startswith('GLFW') and rect.right-rect.left>=1000 and rect.bottom-rect.top>=700:found.append(hwnd)
  return True
 u.EnumWindows(collect,0)
 if not found:raise RuntimeError('No verified owned MC window')
 hwnd=found[0];k=ctypes.WinDLL('kernel32');cur=k.GetCurrentThreadId();fg=W.DWORD();ft=u.GetWindowThreadProcessId(u.GetForegroundWindow(),ctypes.byref(fg));wt=u.GetWindowThreadProcessId(hwnd,None);u.AttachThreadInput.argtypes=[W.DWORD,W.DWORD,W.BOOL];u.BringWindowToTop.argtypes=[W.HWND];u.ShowWindow.argtypes=[W.HWND,W.INT]
 u.AttachThreadInput(cur,ft,True);u.AttachThreadInput(cur,wt,True)
 try:u.ShowWindow(hwnd,9);u.BringWindowToTop(hwnd);u.SetForegroundWindow(hwnd)
 finally:u.AttachThreadInput(cur,wt,False);u.AttachThreadInput(cur,ft,False)
 time.sleep(.4);u.GetWindowThreadProcessId(u.GetForegroundWindow(),ctypes.byref(fg))
 if fg.value!=pid:
  u.ClientToScreen.argtypes=[W.HWND,ctypes.POINTER(W.POINT)];u.WindowFromPoint.argtypes=[W.POINT];u.WindowFromPoint.restype=W.HWND
  point=W.POINT(640,480);u.ClientToScreen(hwnd,ctypes.byref(point));atpid=W.DWORD();u.GetWindowThreadProcessId(u.WindowFromPoint(point),ctypes.byref(atpid))
  if atpid.value==pid:u.SetCursorPos(point.x,point.y);u.mouse_event(2,0,0,0,0);u.mouse_event(4,0,0,0,0);time.sleep(.3)
  u.GetWindowThreadProcessId(u.GetForegroundWindow(),ctypes.byref(fg))
 if fg.value!=pid:raise RuntimeError('Owned MC does not hold foreground; OS input withheld')
 u.keybd_event.argtypes=[W.BYTE,W.BYTE,W.DWORD,ctypes.c_size_t];u.GetKeyboardLayout.argtypes=[W.DWORD];u.GetKeyboardLayout.restype=W.HANDLE;u.LoadKeyboardLayoutW.argtypes=[W.LPCWSTR,W.UINT];u.LoadKeyboardLayoutW.restype=W.HANDLE;u.UnloadKeyboardLayout.argtypes=[W.HANDLE];u.PostMessageW.argtypes=[W.HWND,W.UINT,W.WPARAM,W.LPARAM]
 tid=u.GetWindowThreadProcessId(hwnd,None);old=u.GetKeyboardLayout(tid);temporary=None
 if old&0xffff!=0x409:
  temporary=u.LoadKeyboardLayoutW('00000409',0x80)
  if not temporary:raise RuntimeError('Temporary English keyboard layout unavailable')
  u.PostMessageW(hwnd,0x50,0,temporary);time.sleep(.5)
 scans={key:u.MapVirtualKeyW(key,0) for key in [0x72,0x54]}
 try:
  u.keybd_event(0x72,scans[0x72],0,0);time.sleep(.8);u.keybd_event(0x54,scans[0x54],0,0);time.sleep(1.2);u.keybd_event(0x54,scans[0x54],2,0);u.keybd_event(0x72,scans[0x72],2,0)
  after=settings_wait(lambda q:q['reloadCompleted']>before['reloadCompleted'] and not q['overlay'],20)
 finally:
  u.keybd_event(0x54,scans[0x54],2,0);u.keybd_event(0x72,scans[0x72],2,0)
  if temporary:
   u.PostMessageW(hwnd,0x50,0,old);time.sleep(.4)
   if u.GetKeyboardLayout(tid)!=old:raise RuntimeError('Owned MC keyboard layout restore failed')
   if not u.UnloadKeyboardLayout(temporary):raise RuntimeError('Temporary keyboard layout unload failed')
 check('os_f3t',{'physicalOSInput':True,'pid':pid,'scans':scans,'beforeRequests':before['reloadRequests'],'afterRequests':after['reloadRequests'],'beforeCompleted':before['reloadCompleted'],'afterCompleted':after['reloadCompleted'],'addedPassed':after['addedPassed'],'selectedPacks':after['visiblePacks'],'temporaryKeyboardLayoutRestored':True})

def labels():
 for locale,setting,download in [('zh_cn','\u8bbe\u7f6e','\u4e0b\u8f7d\u5149\u5f71\u5305'),('en_us','Settings','Download Shaders'),('zh_cn','\u8bbe\u7f6e','\u4e0b\u8f7d\u5149\u5f71\u5305')]:
  # ASCII escapes above are decoded once; source remains safe through Windows command encoding.
  setting=setting.encode().decode('unicode_escape') if setting.startswith('\\') else setting
  download=download.encode().decode('unicode_escape') if download.startswith('\\') else download
  s('world');s('locale',value=locale);settings_wait(lambda q:not q['overlay']);q=s('options');q=settings_wait(lambda q:bool(q['widgets']))
  button=next(x for x in q['widgets'] if x['text']==q['videoLabel'] and x['active'] and x['visible']);s('video')
  q=draw_label(setting);s('screenshot',filename='missing-iris-settings-'+locale+'.png');check('iris_settings_'+locale,{'screen':q['screen'],'locale':q['locale'],'draw':[x for x in q['draw'] if x['text']==setting]})
  s('shader');q=draw_label(download);s('screenshot',filename='missing-iris-download-'+locale+'.png');check('iris_download_'+locale,{'screen':q['screen'],'locale':q['locale'],'draw':[x for x in q['draw'] if x['text']==download]})
 s('world')
def dom_wait(expression,test,timeout=12):
 end=time.monotonic()+timeout
 while True:
  q=ev(expression)
  if test(q):return q
  if time.monotonic()>end:raise AssertionError(q)
  time.sleep(.15)
def shop():
 s('world');w('open');w('app',name='games');dom_wait('({ready:!!window.muxi?.invoke&&!!window.MuxiMinigamesApp})',lambda q:q['ready']);w('script',path=str(T/'tests/native-window/shop-fixture.js'))
 ev('(()=>{window.MuxiMinigamesApp.activate();return {started:true}})()')
 dom_wait('({n:document.querySelectorAll("#mg-shop-game option").length})',lambda q:q['n']==2)
 domclick('[data-mg-page="shop"]');dom_wait('({n:document.querySelectorAll(".mg-card-preview").length})',lambda q:q['n']>0)
 check('fixture_boundary',{'gameCatalog':'explicit fixture','iconsGet':'real Java resource manager transport','productionCandidateUnmodified':True})
 ev('(()=>{const e=document.getElementById("mg-content");e.scrollTop=155;return {scroll:e.scrollTop,max:e.scrollHeight-e.clientHeight}})()');time.sleep(.2)
 a=ev('({scroll:document.getElementById("mg-content").scrollTop})');assert a['scroll']>=100,a
 ev('(()=>{const s=document.getElementById("mg-shop-game");s.value="flight";s.dispatchEvent(new Event("change",{bubbles:true}));return {changed:true}})()');time.sleep(.2)
 b=ev('({scroll:document.getElementById("mg-content").scrollTop})');assert b['scroll']==0,b
 ev('(()=>{document.getElementById("mg-content").scrollTop=93;const s=document.getElementById("mg-shop-game");s.value="horse_racing";s.dispatchEvent(new Event("change",{bubbles:true}));return {changed:true}})()');time.sleep(.2)
 c=ev('({scroll:document.getElementById("mg-content").scrollTop})');assert c['scroll']==a['scroll'],(a,c)
 ev('(()=>{const s=document.getElementById("mg-shop-game");s.value="flight";s.dispatchEvent(new Event("change",{bubbles:true}));return {changed:true}})()');time.sleep(.2)
 d=ev('({scroll:document.getElementById("mg-content").scrollTop})');assert d['scroll']==93,d
 domclick('#mg-refresh');time.sleep(.5);e=ev('({scroll:document.getElementById("mg-content").scrollTop})');assert e['scroll']==93,e
 check('shop_scroll',{'horse':a,'firstFlight':b,'restoredHorse':c,'restoredFlight':d,'afterRefresh':e});shot('missing-shop-scroll.png')
 ev('(()=>{const s=document.getElementById("mg-shop-game");s.value="horse_racing";s.dispatchEvent(new Event("change",{bubbles:true}));document.getElementById("mg-content").scrollTop=0;window.__nativeShop.missing=true;window.MuxiMinigamesApp.activate();return {changed:true}})()')
 expr='(()=>{const a=document.querySelector(".mg-preview-primary img"),b=document.querySelector(".mg-preview-backup img"),c=document.querySelector(".mg-preview-backup");return {id:a?.dataset.terminalIconId,primaryHidden:a?.hidden,backupReady:!!b?.complete&&b.naturalWidth>0,backupWidth:b?.naturalWidth,backupDisplay:c?getComputedStyle(c).display:"missing",backupSrc:b?.src?.slice(0,22)}})()'
 q=dom_wait(expr,lambda q:q.get('id','').endswith('missing_qa.gif') and q.get('primaryHidden') and q.get('backupReady') and q.get('backupDisplay')!='none');check('native_png_fallback',q);shot('missing-native-png-fallback.png')
 ev('(()=>{window.__nativeShop.missing=false;window.MuxiMinigamesApp.activate();return {changed:true}})()');q=dom_wait(expr,lambda q:q.get('id','').endswith('.gif') and not q['id'].endswith('missing_qa.gif') and q.get('primaryHidden')==False and q.get('backupDisplay')=='none');check('native_preview_recovery',q);shot('missing-native-preview-recovery.png')
def request_stop():
 owner=alive();rows=[]
 for p in psutil.process_iter(['pid','ppid','exe','create_time','cmdline']):
  try:
   if p.info['exe'] and str(LAB).lower() in p.info['exe'].lower():rows.append(p.info)
  except (psutil.NoSuchProcess,psutil.AccessDenied):pass
 report['helpersBeforeStop']=rows;persist()
 stop={'runId':owner['runId'],'normalStopOnly':True,'requestedAt':time.time()};(LAB/'stop-request.json').write_text(json.dumps(stop),encoding='utf-8')
 for role in owner['roles']:(LAB/'coordinator'/('command-'+role+'.json')).write_text(json.dumps({'id':2000000000,'runId':owner['runId'],'type':'stop'}),encoding='utf-8')
 print('NORMAL_STOP_REQUESTED',flush=True)
if __name__=='__main__':
 sys.stdout.reconfigure(encoding='utf-8');report['runId']=alive()['runId'];persist()
 for name,fn in [('os_f3t',f3t),('iris_labels_locales',labels)]+([('png_scroll',shop)] if os.environ.get('MUXI_RECHECK_SHOP')=='1' else []):step(name,fn)

 if report['errors']:print('PENDING_REPAIR_INSTANCE_KEPT_ALIVE',flush=True)
 else:
  w('open');w('app',name='games');time.sleep(1);request_stop()
