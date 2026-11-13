// PreToolUse hook (Bash | PowerShell): when Claude runs the Maven wrapper,
// rewrite the command so its output goes through mvn-summary.js.
// Claude then reads ~20 lines (test counts, errors) instead of thousands of log lines.
// Skipped when the command already has a pipe (Claude is filtering on purpose).
let raw = "";
process.stdin.on("data", (c) => (raw += c));
process.stdin.on("end", () => {
  let input;
  try {
    input = JSON.parse(raw);
  } catch {
    return; // never block a tool call because of a hook problem
  }
  const toolInput = input.tool_input || {};
  const cmd = toolInput.command || "";

  if (!/\bmvnw(\.cmd)?\b/.test(cmd) || cmd.includes("|")) return; // leave untouched

  const projectDir = (process.env.CLAUDE_PROJECT_DIR || process.cwd()).replace(/\\/g, "/");
  const filter = `node "${projectDir}/.claude/hooks/mvn-summary.js"`;

  const wrapped =
    input.tool_name === "PowerShell"
      ? `& { ${cmd} } | ${filter}`
      : `set -o pipefail; ( ${cmd} ) 2>&1 | ${filter}`;

  console.log(
    JSON.stringify({
      hookSpecificOutput: {
        hookEventName: "PreToolUse",
        updatedInput: { ...toolInput, command: wrapped },
      },
    })
  );
});
