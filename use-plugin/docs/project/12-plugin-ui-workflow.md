# Plugin UI and User Workflow

## 0. Source-tree launch preflight

From the repository root, build before starting the GUI:

```powershell
mvn -B -pl use-plugin -am -DskipTests package
java -jar .\use-gui\target\use-gui.jar
```

The package phase copies the newly shaded production JAR to
`use-gui/lib/plugins/use-jacamo-plugin-1.0.1.jar`. This is a generated, ignored
runtime artifact; `GuiPluginStagingIT` checks that it is byte-identical to the
production JAR, contains the neutral contract/Bridge client, excludes the legacy
authority classes, and excludes JaCaMo-side adapter classes.

For live Bridge use, put all `-Duse.jacamo.bridge.*` options before `-jar`. The GUI
does not become JaCaMo authority or edit Bridge configuration. To reproduce the
supported live original Hello cut on Windows/JDK 21, run from the `use` root:

```powershell
powershell -ExecutionPolicy Bypass -File .\use-plugin\tools\live-hello-bridge.ps1 `
  -JcmPath ..\jacamo\doc\tutorials\hello-world\code\helloworld\helloworld.jcm `
  -ProjectKey helloworld -CaseName original-tutorial-hello-world `
  -ObservationSeconds 15 -Headless:$false `
  -EvidenceDirectory .\use-plugin\target\final-system-acceptance\hello-rerun
```

The helper copies the original project, compiles project-local Gradle classes in the
derived copy when needed, injects the official platform entry, derives the runtime
fingerprint, starts JaCaMo and a classpath-isolated USE consumer, and records
producer/consumer logs plus `summary.json`. It never edits `jacamo/` or the original
input. The runtime cut is per-source buffered rather than a globally atomic
cross-subsystem snapshot; evidence-only facts cannot become USE truth. Final
Hello/Auction/House evidence is indexed in
`evidence/final-system-acceptance/README.md`.

### Interactive GUI demo

For a live presentation, use the helper's manual GUI mode instead of opening an
unconfigured USE process:

```powershell
cd D:\_CODE_BANK\Project_\08_Thesis\use
powershell -ExecutionPolicy Bypass -File .\use-plugin\tools\live-hello-bridge.ps1 `
  -JcmPath ..\jacamo\doc\tutorials\hello-world\code\helloworld\helloworld.jcm `
  -ProjectKey helloworld -CaseName hello-gui-demo `
  -InteractiveGui -Headless:$false -TimeoutSeconds 1800 `
  -EvidenceDirectory .\use-plugin\target\gui-demo
```

The helper builds a disposable USE install, launches it with the exact Bridge
endpoint/secret/distribution fingerprint, and passes the derived `.jcm` path to the
Workbench. The Import chooser therefore opens in the correct temporary folder with
that file preselected. In the GUI:

1. open `Plugins > JaCaMo > Open Workbench...`;
2. click **Import JaCaMo Project...** and click **Open**; the derived `.jcm` is
   already selected in its temporary folder;
3. inspect Project, Trace, Diagnostics, Verification, Runtime and Binding;
4. in Runtime, confirm authority `BRIDGE` and readiness `LIVE`, then click
   **Reconnect** and **Resync**;
5. click **Run Full Verification**, then **Export Report...**;
6. close USE to stop the derived producer and finish the helper.

For Auction use `..\jacamo\examples\auction\auction.jcm` with project key
`auction`. For House use `..\jacamo\examples\house-building\house-building.jcm`
with project key `house_building`; state explicitly that the accepted 50-second run
did not satisfy root goal `house_built`.

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

The production workbench reads the validated Bridge endpoint configuration from the
documented `use.jacamo.bridge.*` system properties. The Runtime tab shows semantic
authority, readiness, negotiated capabilities, completeness, model revision,
session/generation, endpoint and diagnostics. Selecting a `.jcm` is an exact
project/digest assertion against the official ModelSnapshot, not a request to parse
it in USE. `Connect`, `Reconnect` and `Resync` obtain a fresh authoritative cut.
The UI is not a standalone external `.jcm` launcher and never silently falls back.

---

## 3. Import wizard

Step:
1. select the `.jcm` already hosted by the JaCaMo Bridge;
2. negotiate schema/distribution/capabilities;
3. validate exact project key and JCM digest;
4. validate ModelSnapshot and RuntimeSnapshot;
5. adapt canonical identities into the neutral semantic IR;
6. show diagnostics and mapping fingerprint compatibility;
7. generate/load the USE representation and apply faithfully projectable runtime facts.

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

The current panel can preserve an explicit historical binding request supplied by a
host workflow. The production Bridge path does not consume parser-era `binding.json`;
ambiguous official references remain explicit contract diagnostics.

Không auto-select candidate bằng fuzzy ranking.

---

## 7. Headless/CLI parity

Core pipeline phải chạy được không cần GUI để:
- tests;
- CI;
- experiment runs;
- thesis reproducibility.

GUI chỉ gọi service layer.
