from pathlib import Path
import subprocess,zipfile,hashlib,json
root=Path(__file__).resolve().parent
classes=root/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['C:/Program Files/Java/jdk-24/bin/javac.exe','--release','21','-encoding','UTF-8','-d',str(classes),*map(str,(root/'java').glob('*.java'))],check=True)
jar=root/'task6-bootstrap-close.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as out:
 out.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nAgent-Class: Task6BootstrapCloseAgent\nMain-Class: Task6AttachBootstrapClose\n\n')
 for file in classes.glob('*.class'):out.write(file,file.name)
print(json.dumps({'path':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'singleAllowedPid':17272,'singleAllowedLab':'all-20261002-050954-5de742e6','bootstrapGuard':True,'RuntimeExitStatus':2,'forceOsTermination':False,'minecraftNormalExitAccepted':False}))
