// Shared room UI integration uses a real hidden Chromium DOM and explicit native fixtures.
import('./minigames-browser/run-browser-qa.mjs').catch(error=>{console.error(error);process.exitCode=1;});
