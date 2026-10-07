"""Read-only final APP input gate. Never starts Minecraft, edits mods, or declares native pass."""
from pathlib import Path
import argparse,hashlib,json,tomllib,zipfile

EXPECTED={
 "muxi-minigames":"c8818fece84986ddeb3878befb7edeafb2e69b432970bbf2b0c4c911182eb3b6",
 "muxi-terminal":"9f2a73bd9f99f99fadc001bb45d32437987ef27b6d08da46a5a1b5a983f42864",
 "muxi-zombie-challenge":"cc493c34ce9579a96a85d64dab1952cad0e2acc969afcdfcb35d7967ba8757f0",
 "muxi-tower-defense":"65c8dc9da17d96ffcafa0bae1e13c4712b14ce3cf6117f2f8e1781ea9cc9d8e0",
}
MODULES=(*EXPECTED,"muxi-outbreak")
FULL_RUNTIME=("GameRuntime","PlayerReturns","GameNetwork","GameModule","RoomTeam","RoomPresentation","RoomAiSeat","RoomAiService")
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def ids(path):
 with zipfile.ZipFile(path) as z:
  name=next((n for n in ("META-INF/neoforge.mods.toml","META-INF/mods.toml") if n in z.namelist()),None)
  if not name:return set()
  return {m.get("modId") for m in tomllib.loads(z.read(name).decode("utf-8")).get("mods",[])}

def validate(manifest_path,client_mods=(),server_mods=None,expected=None):
 expected=dict(EXPECTED if expected is None else expected);manifest_path=Path(manifest_path)
 errors=[];verified=[];installed=[]
 client_mods=tuple(Path(p) for p in client_mods)
 if not client_mods:errors.append("Actual client mod directory not supplied")
 if not server_mods:errors.append("Actual server mod directory not supplied")
 if len({p.resolve() for p in client_mods})!=len(client_mods):errors.append("Duplicate client instance directory; it does not prove two installations")
 try:data=json.loads(manifest_path.read_text(encoding="utf-8"))
 except Exception as e:return {"readyForNativeWindow":False,"nativePassed":False,"errors":["Manifest unavailable: "+str(e)]}
 if data.get("superseded"):errors.append("Manifest superseded: old Zombie combination must not be used")
 rows={}
 for row in data.get("artifacts",[]):
  module=row.get("module")
  if module in MODULES:
   if module in rows:errors.append("Duplicate artifact declaration: "+module)
   rows[module]=row
 for module in MODULES:
  row=rows.get(module)
  if not row:errors.append("Missing artifact: "+module);continue
  wanted=expected.get(module,row.get("sha256"))
  if row.get("sha256")!=wanted:errors.append("Approved hash mismatch: "+module);continue
  if not isinstance(wanted,str) or len(wanted)!=64 or any(c not in "0123456789abcdef" for c in wanted):errors.append("Invalid SHA256: "+module);continue
  if not row.get("path"):errors.append("Artifact path not supplied: "+module);continue
  path=Path(row["path"]);path=path if path.is_absolute() else manifest_path.parent/path
  try:
   if digest(path)!=wanted:raise ValueError("file hash mismatch")
   if module.replace("-","_") not in ids(path):raise ValueError("mod ID mismatch")
   with zipfile.ZipFile(path) as z:
    names=z.namelist()
    if any("endgame" in n.lower() for n in names):raise ValueError("paused Endgame entry included")
    if module=="muxi-minigames" and any("net/muxigame/minigames/"+c+".class" not in names for c in FULL_RUNTIME):raise ValueError("compile-only/incomplete framework")
   verified.append({"module":module,"filename":row.get("filename",path.name),"sha256":wanted,"path":str(path)})
  except Exception as e:errors.append(module+": "+str(e))
 for side,folder in [("client",Path(p)) for p in client_mods]+([("server",Path(server_mods))] if server_mods else []):
  found={};actual={}
  if not folder.is_dir():errors.append(side+" mod directory missing: "+str(folder));continue
  for path in folder.glob("*.jar"):
   try:
    for modid in ids(path):found.setdefault(modid,[]).append(path)
   except Exception as e:errors.append(side+" unreadable mod metadata: "+path.name+": "+str(e))
  for module in MODULES:
   matches=found.get(module.replace("-","_"),[])
   if len(matches)!=1:errors.append(side+" requires one "+module+" jar; found "+str(len(matches)));continue
   wanted=expected.get(module,rows.get(module,{}).get("sha256"))
   actual[module]=digest(matches[0])
   if actual[module]!=wanted:errors.append(side+" installed hash mismatch: "+module)
  for modid in ("touhou_little_maid","yes_steve_model","tacz")+(("mcef",) if side=="client" else ()):
   if len(found.get(modid,[]))!=1:errors.append(side+" requires one dependency: "+modid)
  installed.append({"side":side,"path":str(folder),"gameSha256":actual})
 return {"readyForNativeWindow":not errors,"nativePassed":False,"installedClientCount":len(client_mods),"dualClientPackageVerified":not errors and len(client_mods)>=2,"scope":"Read-only package/hash/metadata gate; no Minecraft start, real players, rendering or gameplay claim", "expectedZombieSha256":expected.get("muxi-zombie-challenge"),"artifacts":verified,"installed":installed,"errors":errors}

if __name__=="__main__":
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--manifest",type=Path,required=True);parser.add_argument("--client-mods",type=Path,action="append",default=[]);parser.add_argument("--server-mods",type=Path);parser.add_argument("--output",type=Path)
 parser.add_argument("--expected-framework-sha256",help="Only supply a newer parent-approved freeze hash");parser.add_argument("--expected-terminal-sha256",help="Only supply a newer parent-approved freeze hash")
 args=parser.parse_args();expected=dict(EXPECTED)
 if args.expected_framework_sha256:expected["muxi-minigames"]=args.expected_framework_sha256
 if args.expected_terminal_sha256:expected["muxi-terminal"]=args.expected_terminal_sha256
 result=validate(args.manifest,args.client_mods,args.server_mods,expected)
 if args.output:args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf-8")
 print(json.dumps(result,ensure_ascii=False,indent=2));raise SystemExit(0 if result["readyForNativeWindow"] else 1)
