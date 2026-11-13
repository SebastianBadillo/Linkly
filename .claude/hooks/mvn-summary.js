// Reads Maven output from stdin and prints only what matters:
// test counts, failures/errors (without stack-trace noise), and the BUILD result.
// The full log is saved to a temp file so nothing is lost.
const fs = require("fs");
const os = require("os");
const path = require("path");

const KEEP = [
  /Tests run:/,
  /BUILD (SUCCESS|FAILURE)/,
  /\[ERROR\]/,
  /<<< (FAILURE|ERROR)/,
  /Caused by:/,
  /expected|but was|Expecting/i,
  /APPLICATION FAILED TO START/,
  /Could not find a valid Docker environment/,
  /COMPILATION ERROR/,
  /Total time:/,
];
const DROP = [/^\s+at /, /^\[ERROR\]\s*$/, /Re-run Maven|full stack trace|-> \[Help|cwiki\.apache/];
const MAX_LINES = 60;

const logFile = path.join(os.tmpdir(), "linkly-last-maven.log");
let raw = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (c) => (raw += c));
process.stdin.on("end", () => {
  fs.writeFileSync(logFile, raw);
  const lines = raw.split(/\r?\n/);

  const kept = [];
  const seen = new Set();
  for (const line of lines) {
    if (!KEEP.some((r) => r.test(line)) || DROP.some((r) => r.test(line))) continue;
    const short = line.length > 300 ? line.slice(0, 300) + " …" : line;
    if (seen.has(short)) continue; // Maven repeats the same error several times
    seen.add(short);
    kept.push(short);
  }

  const shown = kept.slice(0, MAX_LINES);
  console.log(shown.join("\n"));
  if (kept.length > MAX_LINES) console.log(`… ${kept.length - MAX_LINES} more matching lines hidden.`);
  console.log(`[mvn-summary] ${lines.length} lines filtered to ${shown.length}. Full log: ${logFile}`);

  // Keep the real result: fail if Maven reported a failure.
  process.exit(/BUILD FAILURE/.test(raw) ? 1 : 0);
});
