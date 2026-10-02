# Terminal public icons

Built-in terminal apps use `window.TerminalIcons`. They declare an item ID,
resource ID, or supported semantic ID; they do not construct item texture paths
or maintain an app-local icon cache.

```js
const icons = window.TerminalIcons;
icons.release(dialog);
dialog.innerHTML = icons.html({
  kind: 'item', id: 'minecraft:enchanted_golden_apple', label: '附魔金苹果'
});
await icons.hydrate(dialog);
// Before replacing/removing the dialog's content:
icons.release(dialog);
```

`html({kind,id,label})` escapes labels and returns the common placeholder and
image markup. `hydrate(root)` loads all descriptors below a DOM root, retaining
existing loaded nodes. `release(root)` clears their image references and guards
against late responses. `release()` clears all tracked images and cached data.
`request({kind,id})` returns a PNG data URI or null. Normal apps should use
`html` and `hydrate` so fallback and lifecycle behavior stay shared.

Descriptors:

| Kind | ID | Rendering |
| --- | --- | --- |
| `item` | Registered item such as `minecraft:enchanted_golden_apple` | Actual MC GUI ItemStack renderer, model aliases, foil and block models |
| `resource` | Existing MC PNG such as `minecraft:textures/item/golden_apple.png` | Current resource manager; maximum 256 KiB and 256×256 pixels |
| `semantic` | `experience-levels` | Shared local pixel symbol; no fake item request |

Missing items/resources and malformed descriptors retain the common fallback.
The native endpoint accepts only trusted terminal queries. It does not fetch
external URLs or arbitrary local files. Item icons are rendered at 64×64; native
framebuffers, images and vertex buffers are released after each request.

The web LRU is capped at 96 entries and 4 MiB of source strings. Work has four
concurrent requests and a bounded queue. Resource reloads increment the native
revision; attached views poll it and refresh existing icons, including hidden
terminal views. `pagehide` disposes queries, timers and observers.

`invalidate()` manually refreshes live icons. `dispose()` permanently releases
the page service. `stats()` exposes bounded cache and pending-request counters
for QA. Apps should release their own roots, leaving page-wide disposal to the
shared service.

Run the isolated service checks with `node tests/terminal-icons.test.cjs`.
