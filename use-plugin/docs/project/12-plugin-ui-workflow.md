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

Phase 1 provides only `Plugins > JaCaMo > Status` and the `jacamo status`
shell command to confirm plugin loading. The actions below belong to later
phases and are not enabled by the skeleton.

Menu/toolbar:
- `Import JaCaMo Project...`
- `Rebuild JaCaMo Model`
- `Validate Mapping`
- `Load Verification Profile...`
- `Run Full Verification`
- `Connect JaCaMo Runtime`
- `Disconnect Runtime`
- `Resync Runtime State`
- `Export Verification Report`

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
- persist explicit `binding.json`.

Không auto-select candidate bằng fuzzy ranking.

---

## 7. Headless/CLI parity

Core pipeline phải chạy được không cần GUI để:
- tests;
- CI;
- experiment runs;
- thesis reproducibility.

GUI chỉ gọi service layer.
