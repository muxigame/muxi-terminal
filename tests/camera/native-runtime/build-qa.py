"""Compile the preserved camera QA sources; never copy or launch a Minecraft instance."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--product", required=True, type=Path)
    parser.add_argument("--java-home", required=True, type=Path)
    parser.add_argument("--server-libraries", required=True, type=Path)
    parser.add_argument("--client-libraries", required=True, type=Path)
    parser.add_argument("--mcef-jar", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path,
                        help="A new, small QA build directory; existing paths are refused.")
    args = parser.parse_args()
    here = Path(__file__).resolve().parent
    repo = here.parents[2]
    for path in (args.product, args.mcef_jar):
        if not path.is_file():
            parser.error("Missing compile input: " + str(path))
    for path in (args.server_libraries, args.client_libraries):
        if not path.is_dir():
            parser.error("Missing library directory: " + str(path))
    if args.output.exists():
        parser.error("Use a new output directory; existing paths are never overwritten.")
    spec = importlib.util.spec_from_file_location("terminal_build", repo / "build.py")
    build = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(build)
    compiler, _ = build.java_tools(args.java_home)
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    nested = out / "nested"
    nested.mkdir()
    libraries = sorted(set(args.server_libraries.resolve().rglob("*.jar")) |
                       set(args.client_libraries.resolve().rglob("*.jar")))
    jars = [args.product.resolve(), *libraries, args.mcef_jar.resolve()]
    # Reuse installed dependencies; extract only nested compile libraries, never mods/packs.
    for jar in [args.mcef_jar.resolve(), *libraries]:
        if jar == args.mcef_jar.resolve() or jar.name.endswith("-universal.jar"):
            jars.extend(build.nested_jars(jar, nested))
    build.compile_java(compiler, sorted((here / "java").rglob("*.java")),
                       out / "classes", os.pathsep.join(map(str, jars)), out / "compile.args")
    target = out / "camera-lifecycle-qa-only.jar"
    mixins = ["OfflineMcefMixin", "HardwareWmiTimeoutQAMixin", "AlbumAuthorityQAMixin",
              "ImageResourceQAMixin", "BrowserTextureQAMixin"]
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("META-INF/neoforge.mods.toml", '\n'.join([
            'modLoader="javafml"', 'loaderVersion="[4,)"', 'license="MIT"',
            '[[mixins]]', 'config="camera_lifecycle_qa.mixins.json"', '[[mods]]',
            'modId="camera_native_qa"', 'version="1.0.1"',
            'displayName="Isolated camera lifecycle QA"', '']))
        archive.writestr("camera_lifecycle_qa.mixins.json", json.dumps({
            "required": True, "minVersion": "0.8", "package": "net.muxigame.terminal.qa.mixin",
            "compatibilityLevel": "JAVA_21", "client": mixins,
            "injectors": {"defaultRequire": 1}}))
        for path in sorted((out / "classes").rglob("*.class")):
            archive.write(path, path.relative_to(out / "classes").as_posix())
    receipt = {"compiled": True, "runtimeTested": False, "jar": str(target),
               "productSha256": hashlib.sha256(args.product.read_bytes()).hexdigest(),
               "qaSha256": hashlib.sha256(target.read_bytes()).hexdigest(),
               "sourceFiles": len(list((here / "java").rglob("*.java")))}
    (out / "compile-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
