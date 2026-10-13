# Published shop source baseline

Live1.4.37 terminal SHA256: bd4a181268a4b1df7e555ac41f22d79407639e3694337aad3b979e4587512292, version0.2.4-shop-ui-qa.3. Historical build used9fc5ab7ea897a1e6b40a0e3a75623f695f777b3c plus seven unpublished-to-Git files: PreviewResource.java, TerminalIcons.java, terminal/index.html, terminal/terminal-icons.js, minigames/app.js, app.css and model.js. These exact published overlays were already in the canonical dev working tree. This commit records them and their existing regression tests; it does not introduce a new shop behavior.

A current canonical full rebuild preserves every non-music live class byte-for-byte, including PreviewResource, TerminalIcons and the original TerminalScreen. The only Java changes from the live jar are4433b37's later music filtering, which remains retained. Three resource differences are existing line-ending changes; no shop resource behavior is replaced. Eleven direct GIF transport/path/size tests and36 actual Chromium shop tests pass, including GIF animation, PNG fallback, media node reuse, scroll retention and preview cleanup. Browser gameplay APIs are explicit fixtures.

Historical evidence is read-only. The canonical repository is the source of truth; no historical private source tree is used for development. Native window/shop acceptance for the separate window revision is still required.
