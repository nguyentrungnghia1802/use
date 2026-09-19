# Known Limitations

- Compatibility evidence is limited to Windows 11 amd64, Oracle JDK 21.0.5, Maven
  3.9.9, USE 7.5.0, Jason 3.3.0, CArtAgO 3.1 and Moise 1.1.
- Live evidence uses real in-process component APIs; it does not launch the complete
  JaCaMo 1.3.0 application from `.jcm`.
- The Moise live scenario constructs a real programmatic OS/OE subset. The checked-in
  XML is static import provenance, not the runtime OS used by that scenario.
- Communication links, formation cardinality, sequence plans, normative time
  constraints and norm activation/fulfilment/violation are not executed.
- Arbitrary Java effects and unsupported Jason/CArtAgO syntax remain explicit
  diagnostics; they are not guessed or silently translated to OCL.
- A CArtAgO request whose guard stays false may suspend without a terminal callback;
  operation correlation begins at `opStarted`.
- Runtime verification observes and reports; it does not block JaCaMo actions.
- LIVE workspace replacement is supported for rebuild, user OCL profile load, and
  reimport. It drains the old event stream, rebinds mutation/verification consumers,
  transfers only exact matching runtime aliases, and applies an authoritative snapshot.
  In-flight operation correlations and historical runtime reports do not cross the
  workspace boundary. A snapshot failure leaves consumers aligned and disconnects in
  `ERROR`; it does not silently retain a stale `LIVE` state.
- Project-root `binding.json` is production input only for exact typed ambiguity.
  Invalid, duplicate, wrong-kind, malformed, or source-hash-stale entries block import;
  bindings do not create candidates or provide fuzzy resolution.
- Interactive installed-distribution GUI testing and other OS/JDK/component versions
  are outside the automated release gate.
- The plugin requires its documented host/runtime libraries on the USE classpath;
  the release does not redistribute the JaCaMo component dependencies.

## Runtime research development (Phase 17)

The runtime ledger currently retains all accepted/rejected outcomes in memory; long-duration retention is not bounded. Jason mind and Moise instance observations are not proof of corresponding USE state mutation. CArtAgO unknown/retired observations are explicitly quarantined outside the bound mirror subset. See docs/project/runtime-event-identity.md and the research reconciliation matrix. No new Ecore or OCL support is claimed.
