"""Verify native timing, interruption, supersession and reduced-motion without a game."""
from pathlib import Path
import importlib.util,json,subprocess
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('terminal_build',ROOT/'build.py');build=importlib.util.module_from_spec(spec);spec.loader.exec_module(build)
compiler,java=build.java_tools(None)
lab=ROOT/'build/motion-tests';lab.mkdir(parents=True,exist_ok=True)
source=ROOT/'src/main/java/net/muxigame/terminal/client/TerminalContentTransition.java'
build.compile_java(compiler,[source,*list((ROOT/'tests/motion/java').rglob('*.java'))],lab/'classes','',lab/'compile.args')
run=subprocess.run([str(java),'-cp',str(lab/'classes'),'net.muxigame.terminal.client.ContentTransitionTest'],capture_output=True,encoding='utf-8')
report={'success':run.returncode==0,'result':run.stdout.strip(),'error':run.stderr.strip()}
(lab/'result.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2));raise SystemExit(run.returncode)
