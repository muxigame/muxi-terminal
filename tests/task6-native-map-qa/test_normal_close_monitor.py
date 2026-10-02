"""Exercise the real monitor loop: deadline must leave a live process unforced."""
import ast,types,json
from pathlib import Path
source=Path(__file__).parent/'desktop/run_131_qa.py'
tree=ast.parse(source.read_text(encoding='utf-8'))
body=None
def process_loop(node):
 return isinstance(node,ast.While) and isinstance(node.test,ast.Compare) and isinstance(node.test.left,ast.Call) and isinstance(node.test.left.func,ast.Attribute) and node.test.left.func.attr=='poll' and isinstance(node.test.left.func.value,ast.Name) and node.test.left.func.value.id=='process'
for node in ast.walk(tree):
 if isinstance(node,ast.With) and any(process_loop(n) for n in node.body):body=node.body;break
assert body is not None
start=next(i for i,n in enumerate(body) if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='deadline' for t in n.targets))
code=compile(ast.fix_missing_locations(ast.Module(body=body[start:],type_ignores=[])),str(source),'exec')
class Process:
 pid=17272
 def __init__(self,done=False):self.done=done;self.waited=False
 def poll(self):return 0 if self.done else None
 def wait(self):
  if not self.done:raise AssertionError('Live process must not be blocked on or force-closed')
  self.waited=True;return 0
 def terminate(self):raise AssertionError('OS termination attempted')
 def kill(self):raise AssertionError('OS kill attempted')
def run(process):
 records=[];clock=iter([0,841,842,901,902,903])
 namespace={'process':process,'lab':Path('own-lab'),'timeout':900,'normal_close_requested':False,'timed_out':False,'left_running':False,'time':types.SimpleNamespace(monotonic=lambda:next(clock),sleep=lambda _:None),'write':lambda path,value:records.append((path,value)),'print':lambda *args,**kwargs:None}
 exec(code,namespace)
 return namespace,records
value,records=run(Process())
assert value['timed_out'] and value['left_running'] and value['code'] is None
assert len(records)==2
assert records[0][0].name=='request-normal-close.json' and records[0][1]['pid']==17272
assert records[1][0].name=='normal-close-pending.json' and records[1][1]['cleanExit'] is False
assert records[1][1]['forceTerminationPerformed'] is False
process=Process(done=True);value,records=run(process)
assert value['code']==0 and not value['timed_out'] and not value['left_running'] and process.waited
assert records==[]
print(json.dumps({'success':True,'cases':2,'scope':'real AST-extracted process monitor: normal completion, own live PID deadline with no force termination or false clean exit'}))
