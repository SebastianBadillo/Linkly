---
name: spec
description: Run the "you specify, I gap-check, I build, you explain back" workflow for a new feature or section. Use when the developer starts a new feature (e.g. "/spec cache-aside", "let's do the rate limiter") so the logic is understood before any code is written.
argument-hint: <feature or section name>
---

# /spec — design before code

Goal: the developer understands and can explain every line. Claude never writes feature code until the spec is complete.

Feature: $ARGUMENTS (if empty, ask which section of the plan we are doing).

## Step 1 — Problem first
In 5–10 lines: what breaks or is missing in Linkly *without* this feature? Use a concrete scenario with numbers
(e.g. "a viral link at 5,000 req/s exhausts the 10-connection DB pool"). Name the concepts involved, without explaining them yet.

Then ask the developer to specify the logic in their own words. Give a short checklist of what a complete spec covers:
inputs/outputs, the algorithm or data flow, where data lives, edge cases and failures, how we will test it.

**Stop here and wait for the answer.**

## Step 2 — Gap check (repeat until complete)
Compare the answer with the checklist. For each item:
- ✅ correct → confirm in one line, add any missing detail.
- ⚠️ vague → ask one precise question.
- ❌ wrong or missing knowledge → teach it briefly (≤10 lines, an example or small diagram), then ask them to re-specify that part.

Ask at most 3 questions per round. Do not give away the full design. Lead with questions and hints.
Only when every item is ✅, write a short **Final spec** (bullets) and ask: "Is this what we're building?"

**Wait for confirmation.**

## Step 3 — Build
Implement exactly the final spec, following CLAUDE.md conventions:
- Small, readable code with no clever tricks the developer can't explain.
- Tests first or alongside: unit tests (JUnit 5/Mockito), plus integration tests (Testcontainers) when it touches Postgres/Valkey.
- Run the build/tests and report the real result.
- If implementation reveals a spec gap, stop and ask instead of guessing.

## Step 4 — Explain back
Ask the developer to explain, in 2–3 sentences as in an interview: what the code does, why this design, and one trade-off.
Correct any misunderstanding. Then suggest: an ADR if a real decision was made, and `/log` to record progress.
