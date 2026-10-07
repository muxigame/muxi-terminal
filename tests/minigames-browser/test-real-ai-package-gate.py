"""Directed ZIP/metadata fixtures only; these are not runnable Minecraft mods."""
from pathlib import Path
import importlib.util,json,shutil,tempfile,unittest,zipfile
spec=importlib.util.spec_from_file_location("gate",Path(__file__).with_name("validate-real-ai-app-package.py"));gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
class PackageGateTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name);self.client=self.root/"client";self.server=self.root/"server";self.client.mkdir();self.server.mkdir();self.rows=[];self.expected={}
  for module in gate.MODULES:
   path=self.root/(module+".jar");self.jar(path,module.replace("-","_"),gate.FULL_RUNTIME if module=="muxi-minigames" else ())
   self.expected[module]=gate.digest(path);self.rows.append({"module":module,"path":str(path),"filename":path.name,"sha256":self.expected[module]})
   shutil.copy2(path,self.client/path.name);shutil.copy2(path,self.server/path.name)
  for modid in ("touhou_little_maid","yes_steve_model","tacz","mcef"):
   self.jar(self.client/(modid+".jar"),modid)
   if modid!="mcef":self.jar(self.server/(modid+".jar"),modid)
  self.manifest=self.root/"manifest.json";self.save()
 def jar(self,path,modid,classes=()):
  with zipfile.ZipFile(path,"w") as z:
   z.writestr("META-INF/neoforge.mods.toml",'[[mods]]\nmodId="'+modid+'"\nversion="fixture"\n')
   for c in classes:z.writestr("net/muxigame/minigames/"+c+".class",b"not runnable: explicit test fixture")
 def save(self,extra=None):self.manifest.write_text(json.dumps({"artifacts":self.rows,**(extra or {})}),encoding="utf-8")
 def runGate(self):return gate.validate(self.manifest,[self.client],self.server,self.expected)
 def test_current_metadata_gate_never_claims_native_pass(self):
  r=self.runGate();self.assertTrue(r["readyForNativeWindow"],r);self.assertFalse(r["nativePassed"])
 def test_superseded_zombie_manifest_rejected(self):
  self.save({"superseded":True});self.assertFalse(self.runGate()["readyForNativeWindow"])
 def test_wrong_approved_zombie_hash_rejected(self):
  self.expected["muxi-zombie-challenge"]="0"*64;self.assertFalse(self.runGate()["readyForNativeWindow"])
 def test_installed_old_zombie_rejected(self):
  self.jar(self.client/"muxi-zombie-challenge.jar","muxi_zombie_challenge",["different"]);self.assertFalse(self.runGate()["readyForNativeWindow"])
 def test_duplicate_framework_even_under_different_filename_rejected(self):
  shutil.copy2(self.client/"muxi-minigames.jar",self.client/"old-duplicate.jar");self.assertFalse(self.runGate()["readyForNativeWindow"])
 def test_client_mcef_required_server_mcef_not_required(self):
  (self.client/"mcef.jar").unlink();self.assertFalse(self.runGate()["readyForNativeWindow"])
 def test_compile_only_framework_rejected(self):
  path=self.root/"muxi-minigames.jar";self.jar(path,"muxi_minigames",["RoomAiSeat","RoomAiService"]);self.expected["muxi-minigames"]=gate.digest(path);self.rows[0]["sha256"]=self.expected["muxi-minigames"];self.save();self.assertFalse(self.runGate()["readyForNativeWindow"])
 def test_missing_future_zombie_path_rejected(self):
  next(r for r in self.rows if r["module"]=="muxi-zombie-challenge")["path"]=None;self.save();self.assertFalse(self.runGate()["readyForNativeWindow"])
 def test_artifacts_without_installed_directories_not_ready(self):
  r=gate.validate(self.manifest,expected=self.expected);self.assertFalse(r["readyForNativeWindow"]);self.assertFalse(r["dualClientPackageVerified"])
 def test_duplicate_client_path_does_not_prove_two_installations(self):
  r=gate.validate(self.manifest,[self.client,self.client],self.server,self.expected);self.assertFalse(r["readyForNativeWindow"]);self.assertFalse(r["dualClientPackageVerified"])
 def test_two_distinct_installed_fixture_directories_still_not_native_acceptance(self):
  second=self.root/"client-two";shutil.copytree(self.client,second);r=gate.validate(self.manifest,[self.client,second],self.server,self.expected);self.assertTrue(r["dualClientPackageVerified"]);self.assertFalse(r["nativePassed"])
if __name__=="__main__":unittest.main(verbosity=2)
