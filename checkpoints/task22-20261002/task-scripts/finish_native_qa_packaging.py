from pathlib import Path
root=Path(__file__).resolve().parent
target=root/'package_native_camera.py'
source=target.read_text(encoding='utf-8')
start=source.index("readme='''")
end=source.index("(out/'README.md').write_text",start)
source=source[:start]+"readme=(root/'native-camera-readme.txt').read_text(encoding='utf-8')\n"+source[end:]
source=source.replace("['run-camera-qa.py','qa_fml_config.py','NativeCameraEntry.ps1','RegisterAndRunOneShot.ps1','candidate-pins.json','build-qa.py']", "['run-camera-qa.py','qa_fml_config.py','shared_progress_io.py','NativeCameraSession2Fixed.ps1','HANDOFF.md','candidate-pins.json','build-qa.py']")
target.write_text(source,encoding='utf-8')
