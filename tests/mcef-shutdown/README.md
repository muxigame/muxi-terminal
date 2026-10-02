# MCEF Windows shutdown guard

MCEF 2.1.6 for Minecraft 1.21.1 injects a Windows fallback at `Minecraft.close` TAIL. It invokes `tasklist`, then `taskkill /F /IM jcef_helper.exe`. This command targets every matching process rather than the closing Minecraft instance. Any unguarded instance can therefore terminate another instance's browser helpers during normal exit.

Verified installed input SHA256: `0c7696216fa5cfee659d687475873c847a9a17cc8ce3a56119a69c946eeb8772`.
Official source at tag `2.1.6-1.21.1`, commit `c89e242092b11be9a10ee9ffebecc7f9f5b55c0a`: [CefWindowsShutdownMixin.java](https://github.com/CinemaMod/mcef/blob/c89e242092b11be9a10ee9ffebecc7f9f5b55c0a/common/src/main/java/com/cinemamod/mcef/mixins/CefWindowsShutdownMixin.java), [LGPL 2.1 license](https://github.com/CinemaMod/mcef/blob/c89e242092b11be9a10ee9ffebecc7f9f5b55c0a/LICENSE).

The terminal compatibility plugin runs after Mixin's injection passes. A Minecraft marker mixin selects the exact merged owner through `@MixinMerged.mixin`, not an unstable renamed handler name. The production guard requires the known callback descriptor, instance method, command constants and `ProcessBuilder.start` call before replacing that handler body with `RETURN`. It removes obsolete frames, local regions and exception regions, and preserves the method signature, source metadata and all other methods. An upstream handler without the dangerous commands is left unchanged. A dangerous but unsupported shape or duplicate source handler fails explicitly.

The installed MCEF jar and shared caches are never rewritten. Normal MCEF per-JVM client/app disposal and native shutdown remain unchanged. The terminal does not add another process killer or globally shut down the shared browser engine when the album closes. A still-running older instance can still kill guarded instances externally, so coordinate its exit before a parallel acceptance window.

Fixture verification uses the actual installed handler bytecode with explicitly synthetic merged metadata. It never invokes the unsafe body and is not native runtime acceptance:

```powershell
python tests/mcef-shutdown/run-fixtures.py --jdk '<existing JDK21>' --classpath '<existing ASM/Mixin dependency jar>' --mcef '<installed MCEF jar>' --output '<private writable output>'
```

For native acceptance, build the terminal candidate and use `tests/album-runtime/run_native_qa.py --require-shutdown-guard --export-mixins`. The runtime must report `muxi_terminal.mcef_shutdown_guard=disabled:1`, and the exported Minecraft handler must contain only `RETURN`. Record the unchanged installed MCEF hash independently.

Prepare without launching, then execute only after the other owners release the MC/CEF start/stop window:

```powershell
python tests/album-runtime/prepare_shutdown_pair.py --full-template '<approved full-pack QA template>' --other-instance '<same workdir>/album-runtime-<previous-id>/screenshots'
python tests/album-runtime/run_shutdown_pair.py --manifest '<printed album-peer directory>/pair-inputs.json'
```

The peer protocol prepares two owned fresh labs with the same candidate. Start full-pack B using `--peer-coordinator '<workdir>/album-peer-<id>'`, then start A only after B writes `peer-b-ready.json`. After A's actual normal exit and helper-residue check, the coordinator writes `peer-a-exit.json` with `success`, `cleanExit`, `exitCode=0`, and `ownedHelperResidue=0`. B requests fresh DOM/query data and opens the new F2 image after that exit before continuing its 24-round bounded resource protocol. Stale visible pixels do not establish survival.

Observe helper identity through the owned process tree using PID plus creation time. After normal Minecraft exit, check only those observed identities for a bounded ten seconds. Remaining helpers are a failed gate; there is no force-termination fallback. Complete the fixed-photo holds, content-browser destruction and all-terminal-view destruction. Return terminal-owned client/texture counts to the measured shared-engine baseline and check settled queries and screenshot image ownership. A crashed/disappeared renderer cannot count as released memory.

Native acceptance on 131 completed: guarded A exited normally, B performed fresh real DOM/native image queries after that exit, and B completed the bounded 24-round full-pack protocol with terminal resources returning to the shared-engine baseline. Both normal Minecraft exits had no observed owned helper residue. See [actual evidence and private-memory limitations](../../docs/album/mcef-guard-131-20261002.md). Earlier failed original-package resource runs remain recorded in `docs/album/fullpack-131-20261002.md` and must not be relabeled as passes.

Actual exported-class verification (read only; never starts Minecraft):

```powershell
python tests/mcef-shutdown/verify-runtime-export.py --lab '<own completed lab>' --jdk '<existing JDK21>'
```
