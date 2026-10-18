# Canonical semantic replay 3.0

The `USE4GOAL_OBSERVED_V1` profile exports replay **3.0.0**. Native/legacy profiles
retain their existing **2.0.0** reader and semantics. No old recording is rewritten
or interpreted as an original-engine recording.

`program.json` stores deterministic JSON inputs: the preserved official source
snapshot, authored MappingRuleSet 2.0, mapping/engine profile, CSL/Goal/program,
binding and trace fingerprints. It also pins executable core/parser/adapter/native
backend classes, both grammar resources and source provenance. A different or
missing implementation produces an explicit incompatibility error. Original
mutable M1 objects are reconstructed with the original compilers/validators and
the selected native backend; Java object serialization is not used.

The archive also contains the static native `model.use`, `baseline.cmd`, exact
baseline `bindings.json`, final external `constraints.ocl`, the complete bounded
`runtime.jsonl` prefix and a file-hashed `manifest.json`. It exports no Bridge
connection configuration or secret-file contents. Source DTOs can retain source
platform arguments (including source file paths) as provenance. An export is not
a signature or an independent certification of producer authenticity.

Each journal entry retains its hash chain, accepted source/run/generation,
watermarks, native state/version, operation correlation, observation quality and
result fingerprint. PRE, native changes and POST reconstruct genuine native
before/after states and evaluate `@pre` again. Expected outcomes never supply
missing PRE state or Boolean inputs. Original Task history remains distinct from
per-invocation conformance.

Native admissions and asynchronous `ENGINE_RESULT` publications have separate
positions. The offline worker eagerly evaluates each exact accepted native cut,
keeps at most 1,024 pending completions/16 MiB, and compares each at its recorded
publication position. The selected result/history view follows recorded order;
a later completion cannot certify an earlier or different state. A mismatched
hash, missing input/completion, bad baseline, incompatible profile/engine or
unrecorded contract rejects validation.

Only a full baseline plus its complete recorded prefix is certified. An observed
gap remains incomplete even if a later resync/reset produces a usable state.
Missing observations are not reconstructed as false. Archive files are limited to
64 MiB and normal live journal quotas still apply. Export requires a completed
stable cut and rejects persistence gaps/capacity exhaustion; it never silently
exports a partial tail as complete. Disk IO runs on the worker after capturing an
immutable append-only prefix boundary.

Replay opens no connection to JaCaMo. It constructs one unattached candidate on
its worker and selects that read-only system atomically in the USE Session.
Canonical Next/Previous/Reset reconstruct candidates so evaluation never waits
inside the EDT writer and the visible read-only state is not mutated provisionally.
Switching from a disconnected canonical monitor revokes its old engine owner.
Close releases the offline engine, compiled expressions, pins, cursor and owned
copy. Legacy step/reanalysis functionality continues through its existing reader.

The programmatic entry points remain `JaCaMoFacade.exportRuntimeReplay(Path)`,
`replayRuntime(Path)` and the existing explicit step-replay API. A 3.0 archive is
not implicitly reanalyzed under a different engine/profile; such a change needs a
separately versioned policy and is explicitly unavailable in the legacy reanalysis
API. Replay proves accepted observed semantics, not unobserved JaCaMo executions,
global source atomicity, scheduling equivalence or liveness.
