# Agent Working Rules — USE JaCaMo Plugin

> Purpose: execute the current `task.md` correctly, efficiently, and with evidence.  
> This file is permanent working guidance; `task.md` is the replaceable task-specific contract.

# 1. Role and task authority

You are the implementation agent for the USE JaCaMo Plugin.

For every task, first read:

1. `docs/agent/task.md`
2. this `docs/agent/agent.md`
3. only the source/tests/docs directly needed for that task

`task.md` defines the current objective, mapping decisions, scope, checklist, acceptance criteria, and definition of done.

Do not hardcode assumptions from an older task into future work. When `task.md` changes, follow the new task.

If `task.md` conflicts with current production code/API/runtime evidence:
- do not guess;
- inspect the smallest relevant implementation/API/test evidence;
- identify the mismatch;
- make the smallest correct change consistent with the task and real framework behavior;
- report any task requirement that is technically impossible or contradicted by authoritative evidence.

# 2. Source-of-truth order

Use this priority when implementation details are uncertain:

1. current `task.md` for requested target behavior;
2. current production source code;
3. exact JaCaMo/Jason/CArtAgO/Moise/USE APIs or bytecode actually used by the project;
4. executable tests and runtime evidence;
5. maintained current documentation;
6. historical Ecore, Mapping V2, archived docs, old reports only when the task explicitly requires them.

Do not silently treat historical artifacts as current semantic authority.

# 3. Read narrowly

Do not scan the whole repository by default.

Before opening a file, be able to state why it is needed for the current checklist item.

Preferred workflow:

```text
task.md section
→ locate relevant production symbol
→ inspect its direct callers/callees
→ inspect focused tests
→ edit
→ run focused tests
→ expand scope only if evidence requires it
```

Rules:
- search symbols/filenames before reading large directories;
- read targeted ranges/files rather than dumping entire files;
- do not read archived/history/generated/build/vendor content unless necessary;
- do not repeatedly reread unchanged large documents;
- stop expanding once enough evidence exists to implement the current item.

# 4. Core implementation principles

- Use official JaCaMo/Jason/CArtAgO/Moise semantics and APIs where available.
- Do not invent semantics from names or string similarity.
- No fuzzy formal mapping.
- Keep source identity and target traceability explicit.
- Fail closed when a required identity/relation cannot be resolved safely.
- Do not hardcode Hello World, Auction, House Building, or any other case study in production logic.
- Case studies are acceptance/regression tests only.
- Do not patch JaCaMo/Jason/CArtAgO/Moise core unless `task.md` explicitly requires it and no plugin-side solution exists.
- Do not create a second hidden USE model/runtime/OCL engine.
- Keep the active USE `MModel` / `MSystem` / `MSystemState` consistent with the task architecture.
- Generated/exported artifacts are derived outputs, not semantic authority, unless `task.md` explicitly states otherwise.
- Preserve unrelated user work.

# 5. Git workflow

Before editing:

```bash
git status
git branch --show-current
git log -n 5 --oneline
```

Work directly on `main` unless the user explicitly requests another branch.

This is a single-developer workflow:
- do not create phase/feature branches automatically;
- do not add branch-management overhead;
- do not force-push;
- do not destructive-reset or clean unknown work;
- do not discard existing uncommitted changes you do not understand.

Keep commits coherent and tested. Use a simple Conventional Commit message when committing.

# 6. Development loop

For each meaningful checklist item:

```text
1. Read the relevant task.md requirement.
2. Locate the smallest affected production path.
3. Inspect the directly relevant tests/API evidence.
4. Add or update a focused failing test when practical.
5. Implement the smallest correct change.
6. Run focused tests.
7. Run nearby regression tests.
8. Inspect the diff.
9. Update task.md checkbox/evidence only after the requirement is proven.
```

Do not continue past a mandatory gate that is failing.

Do not combine unrelated cleanup/refactoring with the current task unless the task requires it.

# 7. Testing

Testing depth should match the change.

Always run:
- focused tests for changed behavior;
- nearby regression tests.

Before marking the whole task DONE, run the broader build/test commands required by `task.md`.

If `task.md` does not specify final commands, run the relevant full module/reactor verification and package/build checks.

Do not claim:
- runtime support from compile-only evidence;
- live behavior from fixture-only tests;
- generic support from one case study only.

For UI/runtime tasks, perform the manual smoke checks explicitly required by `task.md`.

# 8. Documentation

`task.md` is the active implementation checklist and evidence record.

Update a checkbox only when its acceptance condition actually passes.

Update other docs only when:
- the task changes maintained architecture/behavior; or
- an existing maintained document becomes materially incorrect.

Do not create extra design/audit/status documents unless required by `task.md` or clearly useful long-term.

Prefer one current authoritative document over multiple overlapping documents.

# 9. Scope and compatibility

Do not preserve legacy behavior merely because it existed before if `task.md` explicitly replaces it.

At the same time:
- do not break unrelated modes/features;
- do not modify frozen/historical artifacts unless explicitly required;
- do not delete compatibility paths without checking direct callers/tests;
- do not introduce fallback semantics that hide unsupported behavior.

If a requested change intentionally changes an existing contract, update affected tests and maintained docs in the same task.

# 10. Evidence and final report

Before declaring DONE:

- inspect `git diff` / `git diff --stat`;
- confirm no accidental unrelated edits;
- confirm required tests/builds passed;
- confirm all checked task items have evidence;
- leave unresolved items unchecked and explain why.

Final report should be concise and include:

```text
- what changed
- key files changed
- tests/builds run and results
- important behavior verified
- remaining unchecked/unsupported items
- current git status/commit if a commit was requested
```

Do not claim completion beyond the evidence.

# 11. Final rule

The permanent workflow is:

```text
current task.md
→ narrow evidence search
→ focused implementation
→ focused verification
→ broader regression only when needed
→ task evidence update
→ concise final report
```

Optimize for correctness and maintainability without unnecessary repository reading, documentation churn, branching, or process overhead.
