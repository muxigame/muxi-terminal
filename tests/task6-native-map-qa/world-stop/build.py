from pathlib import Path
import subprocess,zipfile,hashlib,json
root=Path(__file__).resolve().parent;classes=root/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['C:/Program Files/Java/jdk-24/bin/javac.exe','--release','21','-encoding','UTF-8','-d',str(classes),*map(str,(root/'java').glob('*.java'))],check=True)
jar=root/'task6-world-stop.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as out:
 out.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nAgent-Class: Task6WorldStopAgent\nMain-Class: Task6AttachWorldStop\n\n')
 for p in classes.glob('*.class'):out.write(p,p.name)
print(json.dumps({'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'pid':34736,'worldGuard':True,'forceOsTermination':False}))
