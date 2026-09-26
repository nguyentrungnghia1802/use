# JaCaMo project/JCM API inventory

## Inspected baseline

- Repository: `https://github.com/jacamo-lang/jacamo`
- Source revision: `3866858a7ebf6be85d9199c13a09cf4bfb8191be`
- Declared build version: `1.3.1`; Java 21
- JCM grammar: `jacamo/src/main/javacc/JaCaMoProjectParser.jj`
- Generated parser class: `jacamo.project.parser.JaCaMoProjectParser`
- Project root: `jacamo.project.JaCaMoProject extends jason.mas2j.MAS2JProject`

## Exact parse/load path

```text
.jcm InputStream
 -> new jacamo.project.parser.JaCaMoProjectParser(InputStream)
 -> JaCaMoProjectParser.parse(directory)                  [grammar line 170]
    -> new JaCaMoProject
    -> project.importProject(directory, file) for `uses`  [lines 182-184]
    -> add agent/workspace/organisation/institution/platform declarations
    -> project.parserFinished()                           [line 216]
 -> JaCaMoLauncher.init(args)
    -> project.setupDefault()
    -> project.addSourcePath(...)
    -> project.registerDirectives()
    -> Include.setSourcePath(project.getSourcePaths())
    -> loadPackages()
 -> JaCaMoLauncher.create()
    -> createCustomPlatforms()
    -> createEnvironment()
    -> createOrganisation()
    -> createInstitution()
    -> createAgs()
 -> JaCaMoLauncher.start()
    -> Platform.start() for each platform
    -> Jason RunLocalMAS.start() for agents
```

Evidence: `JaCaMoLauncher.java:92-215,242-267,343-361`; `JaCaMoProject.java:75-131,191-243`; grammar lines above.

## Project representation

| Declaration | Exact API/object | Contents and limit |
|---|---|---|
| Agents | inherited `getAgents()` containing `JaCaMoAgentParameters` | Source/class/options, initial beliefs/goals config, workspace memberships, role triplets, focus triplets, instance count/names. It is configuration, not a parsed Jason AST or live agent. |
| Workspaces | `JaCaMoProject.getWorkspaces()` (`:211`) -> `JaCaMoWorkspaceParameters` | Workspace name; `Map<String,ClassParameters> getArtifacts()`; runtime `WorkspaceId` appears only after creation. |
| Organisations | `getOrgs()` (`:228`) -> `JaCaMoOrgParameters` | OS source, configured group and scheme instances. |
| Institutions | `getInstitutions()` (`:243`) | Institution source/configuration. Outside frozen V2 scope unless separately approved. |
| Platforms | `getCustomPlatforms()`, `getPlatformParameters()` | Official extension classes and arguments. |
| Source paths/packages | inherited/project accessors plus `loadPackages()` | Official directive/include environment. |

`JaCaMoAgentParameters.getWorkspaces()` is at line 77, `getRoles()` at 103, and `getFocus()` at 147. `JaCaMoWorkspaceParameters.getArtifacts()` is at line 38. `JaCaMoOrgParameters.getGroups()`/`getSchemes()` are at lines 46/63.

## Includes and instances

- JCM `uses` is not a text splice. Grammar actions call `JaCaMoProject.importProject(directory,fileName)`, which creates the appropriate parser (`.jcm` or inherited `.mas2j`) and merges the resulting project (`JaCaMoProject.java:75-129`). The Bridge must consume the merged official project and retain imported-file provenance; it must not reimplement merge semantics.
- Agent `instances` is parsed into official `AgentParameters`. Named instances clone parameters and set names; numeric instances are expanded by launcher creation. `createAgs()` later replaces the declaration list with concrete launch parameters (`JaCaMoLauncher.java:403-426`). ModelSnapshot must distinguish declaration/template, requested instances and actual runtime incarnations.
- ASL includes are resolved later by Jason's `Include` directive. JaCaMo configures its `SourcePath` at `JaCaMoLauncher.java:189-193`; JCM parse alone does not provide an expanded Jason program.

## Load-only and lifecycle findings

| Question | Answer | Classification |
|---|---|---|
| Is there a parser returning a project without launch? | Yes: instantiate `JaCaMoProjectParser` and call `parse(directory)`. | `FULLY_FEASIBLE` |
| Is there one public `loadOnly()` API that reproduces all launcher setup? | No. `JaCaMoLauncher.init` combines parsing, defaults, directives, package loading, logging/config and possible UI behavior. | `FEASIBLE_WITH_ADAPTER` |
| Is there a post-parse/pre-business point? | Yes, after successful `init`, before `create`; direct parser mode is even narrower. | `FULLY_FEASIBLE` |
| Is there a universal initialized/pre-business point? | No. `create` constructs agents/platform objects; `start` orders platforms then agents, but platform internals create artifacts in their own `start`. | `FEASIBLE_WITH_LIMITATION` |

The Bridge load-only service should replicate only source-path/directive/package setup required by official loaders. It should not call `JaCaMoLauncher.init` blindly because that method parses CLI arguments, configures logging/UI and can call `System.exit` for missing input. An integration probe must compare its result with launcher initialization for the same project.

## Official extension point

`jacamo.platform.Platform` is a public four-method interface: `init(String[])`, `setJcmProject(JaCaMoProject)`, `start()`, `stop()`. JCM platform declarations are parsed into class parameters. `JaCaMoLauncher.createCustomPlatforms()` reflectively creates them, injects the project, initializes them and adds them to the platform list (`JaCaMoLauncher.java:242-250`).

This is sufficient for `JaCaMoBridgePlatform` without a core patch. During initialization it can:

1. receive the exact parsed project;
2. start a transport/service endpoint;
3. add a Bridge `AgArch` to agent parameters using Jason `AgentParameters.addArchClass` before `createAgs`;
4. register CArtAgO/Moise observation only after their authorities exist;
5. expose readiness/capabilities rather than assuming platform ordering.

The hook cannot itself guarantee that every CArtAgO/Moise artifact is ready when its `start()` runs. Snapshot coordination therefore waits for explicit readiness and uses retry/resync.

## Dependency graph confirmed from build

`build.gradle:34-80` resolves Jason 3.3.2, CArtAgO 3.1, JaCa 3.1, Moise 1.1, NPL 0.6.1, IntMAS 1.0.0, SAI 0.5.4 and additional runtime/build libraries. Only JaCaMo integration/project/launcher source is in this repository. The Bridge must compile/test against this exact graph or a recorded compatible distribution fingerprint.

## Tests and evidence

- `JaCamoProjectTest` invokes the official parser and checks project configuration.
- `WorkspaceCreationTest` launches environment behavior and uses CArtAgO controller/artifact information.
- The current JaCaMo Gradle test run passed 6/6.

## Bridge extraction policy

| Fact | Source | ModelSnapshot policy |
|---|---|---|
| MAS name, declared agents/workspaces/orgs/platforms | `JaCaMoProject` | Exact |
| Agent instance request | `AgentParameters` | Preserve declaration plus instance policy; do not call it a live instance |
| Workspace artifact declaration | `JaCaMoWorkspaceParameters.getArtifacts()` | Exact class parameters, not initialized operations/properties |
| Org group/scheme instance declaration | `JaCaMoOrgParameters` | Exact configuration linked to separately loaded OS spec |
| Jason program | Not held by `JaCaMoProject` | Obtain via Jason official loader after JaCaMo directive setup |
| Artifact live identity/state | Not held by project object | RuntimeSnapshot from CArtAgO |
| Moise runtime state | Not held by project object | RuntimeSnapshot/events from ORA4MAS boards/NPL |

No class-name or case-name dispatch is permitted. Unknown custom platforms/directives are recorded as capabilities/provenance and may make the load-only snapshot partial.
