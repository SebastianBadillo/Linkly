---
name: log
description: Record session progress in the project brief (Status line, milestone checkboxes, Progress log table). Use at the end of a work session or when the developer says "log this", "update progress", "end of session".
disable-model-invocation: true
---

# /log — record progress

The brief lives at `.local/linkly-brief.md` (gitignored).

1. Look at what happened this session: the conversation, plus `git log --oneline -15` and `git status`.
2. Draft one Progress log row: | today's date (YYYY-MM-DD) | What I did | Problems / what I learned | Next step |.
   Write it from the developer's point of view, short and concrete. "What I learned" should name real concepts.
3. Propose updates to the **Status** line (status, current milestone, repo, live URL) and tick any milestone items that are truly done.
4. Show the changes and ask the developer to confirm or edit them, then apply them with Edit.
5. End with one line: the next step for the next session.
