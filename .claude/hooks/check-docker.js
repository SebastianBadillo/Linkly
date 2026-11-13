// SessionStart hook: warn early if Docker is not running.
// Testcontainers (integration tests) and docker compose both need it.
const { execSync } = require("child_process");

let running = false;
try {
  execSync("docker info", { stdio: "ignore", timeout: 8000 });
  running = true;
} catch {
  running = false;
}

if (!running) {
  const msg =
    "Docker is NOT running. Integration tests (Testcontainers) and docker compose will fail. " +
    "Start Docker Desktop before running ./mvnw verify.";
  console.log(
    JSON.stringify({
      systemMessage: "⚠️ " + msg, // shown to the developer
      hookSpecificOutput: {
        hookEventName: "SessionStart",
        additionalContext: msg, // given to Claude
      },
    })
  );
}
// Docker running → print nothing: a silent hook costs no context.
