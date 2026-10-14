# Local-only historical acceptance inputs

Keep the two small self-owned jars listed in `../preservation-inputs.json` here.
They preserve the exact accepted 2026-10-02 mixed-working-tree binary snapshot.
They are excluded from Git and are not a Minecraft instance, dependency bundle or
cache. This directory must survive removal of the old Documents/Codex session.

The original source and reproducibility metadata are versioned in the parent
directory. The local jars together occupy 588,316 bytes. No third-party mod,
shader, model, world, key or credential is stored here. A GitHub clone alone
cannot restore these exact historical binary hashes.
