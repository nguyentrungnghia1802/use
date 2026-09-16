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
- Interactive installed-distribution GUI testing and other OS/JDK/component versions
  are outside the automated release gate.
- The plugin requires its documented host/runtime libraries on the USE classpath;
  the release does not redistribute the JaCaMo component dependencies.
