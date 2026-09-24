# Plugin UI and User Workflow

## 1. UX goal

Một người dùng USE phải có thể:
1. Import JaCaMo Project;
2. xem extraction/mapping status;
3. inspect generated model;
4. load/add OCL;
5. run offline verification;
6. connect runtime;
7. xem live violations;
8. navigate về source.

---

## 2. Main actions

Use `Plugins > JaCaMo > Open Workbench...` to open the workbench. `Plugins >
JaCaMo > Status` and the `jacamo status` shell command confirm plugin loading.

Workbench toolbar:
- `Import JaCaMo Project...`;
- `Rebuild`;
- `Load OCL...`;
- `Run Full Verification`;
- `Export Report...`.

The Runtime tab contains Connect, Disconnect, Reconnect, Resync, and Refresh.
The six tabs are Project, Trace, Diagnostics, Verification, Runtime, and Binding.
The Project tab shows the active Metamodel V2 version and full SHA-256 plus the
Mapping V2 ID, schema version, working/frozen status and full SHA-256. Exact binding
candidates are handled in Binding. The source path and line can be copied from the
Trace tab.

The production workbench does not expose connector construction or endpoint fields.
`Connect` therefore works only after a host/integration has called the facade's
`configureRuntime` API. The UI is not a standalone external `.jcm` launcher.

---

## 3. Import wizard

Step:
1. select `.jcm`;
2. detect project root/source paths;
3. parse;
4. show diagnostics;
5. show mapping fingerprint compatibility;
6. show optional binding requirements;
7. generate/load USE representation.

Do not hide warnings.

---

## 4. Views

### Project Overview
- project;
- files;
- dimensions;
- counts;
- hashes.

### Mapping/Trace View
- source semantic element;
- mapping rule;
- USE target;
- resolution status.

### Diagnostics View
Filter by phase/severity/file.

### Verification View
- constraint;
- status;
- context object;
- source links.

### Runtime View
- connection state;
- queue;
- last event;
- sync status;
- latency.

---

## 5. Source navigation

Nếu IDE integration không có:
- show absolute/relative path;
- line/column;
- copy path;
- open via OS/editor action nếu safe.

---

## 6. Binding resolution UX

Khi ambiguous:
- show exact candidates;
- show owner/type/source;
- user chooses;
- persist explicit `<project-root>/binding.json`.

The current panel can persist a request supplied by the host workflow. Production
import consumes project-root `binding.json` automatically, but import does not itself
open a binding dialog or invent a request after failure. Without a valid binding, an
ambiguous formal operation remains `RESOLUTION_AMBIGUOUS`.

Không auto-select candidate bằng fuzzy ranking.

---

## 7. Headless/CLI parity

Core pipeline phải chạy được không cần GUI để:
- tests;
- CI;
- experiment runs;
- thesis reproducibility.

GUI chỉ gọi service layer.
