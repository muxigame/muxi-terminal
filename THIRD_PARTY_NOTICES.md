# Third-party notices

Muxi Player Terminal's browser-in-Minecraft design is adapted from the MinePad
implementation in **WebDisplays / WebDisplays REMASTER**, originally by
Montoyo and maintained by CinemaMod contributors. WebDisplays is distributed
under the MIT License.

The project uses **MCEF (Minecraft Chromium Embedded Framework)** as a runtime
dependency. Chromium/JCEF binaries are not redistributed from this repository.

This repository intentionally does not depend on WebDisplays at runtime; only
the small, relevant interaction and rendering ideas are carried forward so the
terminal can evolve independently.



The MCEF Windows shutdown compatibility guard is original project code. It
identifies only the library's merged process-name kill handler and disables that
handler in memory; MCEF's normal per-JVM client/app disposal remains intact.
No MCEF or Chromium/JCEF dependency JAR/native binary is rewritten or redistributed by this guard.
The affected MCEF 2.1.6-1.21.1 source is CinemaMod/mcef commit
c89e242092b11be9a10ee9ffebecc7f9f5b55c0a, licensed LGPL 2.1:
https://github.com/CinemaMod/mcef/blob/c89e242092b11be9a10ee9ffebecc7f9f5b55c0a/LICENSE
