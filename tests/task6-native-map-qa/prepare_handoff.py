from pathlib import Path
import hashlib,json
HERE=Path(__file__).resolve().parent
task=HERE.parent
kit=task/'task6-native-map-131-qa-kit.zip'
receipt={'targetHost':'192.168.110.131','expectedComputer':'JBC_FCRL','developerOwner':'task6','heavyMinecraftOn008':False,'authorizedSshRoute':{'account':'JBC-1','actualIdentity':'JBC_FCRL\\JBC-1','actualProfile':'C:/Users/ranzh','batchMode':True,'strictHostKeyChecking':'yes','verified':True,'credentialChanges':False},'interactiveTask':{'name':'MuxiDesktopQA','currentPolicy':'Parallel','maintenanceOwner':'task14','changedByTask6':False},'privateEntry':'C:/Users/ranzh/Documents/Codex/task6-native-map-qa-20261002/desktop/run_task6_native_map.ps1','parameters':'-Mode all','qaKitSha256':hashlib.sha256(kit.read_bytes()).hexdigest(),'session0PreparationPassed':True,'nativeQAStarted':False,'releaseReady':False,'blocker':'Task14-coordinated one-time start of the fixed private entry in interactive session 2 is pending. Session 0 cannot launch the client. Task6 retains all feature/visual/exit validation responsibility.','focus':'One 6 GiB / four-worker MC. About 120 seconds foreground after world ready; random loopback fixture port; bounded 900 seconds.','sharedSourcesModified':False,'productionOperations':False}
(HERE/'access-preflight.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
root=task/'teleport-network-implementation'
export=root/'export_full_review.py'
text=export.read_text(encoding='utf-8').replace("'revision':6","'revision':7")
text=text.replace("'nativeQAConnectionBlocker':'Known ranzh@192.168.110.131 route rejected default SSH keys; workspace connector has no connected workspaces; existing authorized route/interactive launcher needed'","'nativeQAInteractiveBlocker':'Authorized JBC-1 SSH verified and own 131 QA kit prepared; task14 one-time coordinated interactive start of fixed task6 entry pending'")
text=text.replace("'remainingValidation':'Task6 developer-owned native client/server integration QA on a parent-coordinated 131 slot: actual Mixin load, GL/UI clarity/dense labels, real valid registered gate, native XP/activation/cooldown/events and real arrival. No 131/008 or live game was occupied.'","'remainingValidation':'Task6 developer-owned native client/server integration QA: actual Mixin load, GL/UI clarity/dense labels, valid registered gate, native XP/activation/cooldown/events and real arrival. Own 131 files prepared through verified JBC-1 SSH; no native QA or 008 heavy MC started; no live game occupied.'")
export.write_text(text,encoding='utf-8')
readme=root/'FULL-REVIEW-README.txt'
lines=readme.read_text(encoding='utf-8').splitlines()
for i,line in enumerate(lines):
    if line.startswith('状态：'):
        lines[i]='状态：真实 131 功能验收仍未完成，不是可发布包。已验证 JBC-1@192.168.110.131 身份为 JBC_FCRL\\JBC-1，并完成 task6 自有隔离 QA 文件准备；待 task14 协调一次交互桌面启动。SSH Session 0 不启动游戏。task6 自行承担真实功能、截图和退出验收。'
lines+=['','131 已准备的独立入口（不是验收结果）',receipt['privateEntry'],'参数 -Mode all；X/原生入口/CEF 边界优先可用 -Mode ui。1 个私有 MC，6 GiB、4 工作线程、45 FPS，随机本地端口，世界就绪后约 120 秒前台焦点，最多 900 秒。task14 只启动固定入口；不接替功能测试。','独立 QA 包 task6-native-map-131-qa-kit.zip SHA256 '+receipt['qaKitSha256']+'。QA-only 终端由地图候选仅替换独立编译的 X TerminalScreen.class；两份产品源码补丁保持独立，原始稳定/好友/X交付不变。','原始 PNG、真实原生包处理/完成事件、真实外部 JS 拒绝、退出收据必须待实际运行并由 task6 审阅后才能确认。']
readme.write_text('\n'.join(lines)+'\n',encoding='utf-8')
print(json.dumps(receipt,ensure_ascii=False,indent=2))
