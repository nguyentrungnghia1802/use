# Refreshing the runnable plugin

Run from the USE repository root. Build the plugin **with its reactor dependencies**:

```powershell
mvn.cmd -o -B -pl use-plugin -am package -DskipTests -Dassembly.skipAssembly=true -Dmaven.antrun.skip=true
python use-plugin/tools/update-runnable-plugin.py --check-only
python use-plugin/tools/update-runnable-plugin.py
```

Run the focused tests appropriate to the change before delivery. The skipTests
example is only a packaging command, not a substitute for those tests.

Close USE before updating JARs on Windows. Backups are retained under
`use-assembly/target/new/work/package-backups`. The tool requires an existing
installation and validates every contract and semantic-engine class against the
reactor artifacts before replacing the plugin and bridge JARs. It also checks the
installed result. It does not update case studies or framework runtime JARs.
It validates packaged framework JARs against the reviewed sibling dependency
lock and the producer build, then refreshes the installed lock. JaCaMo must also
match its actual sibling source build. A stale manifest can be repaired only
when those binaries already agree; a binary mismatch stops the update.

A plugin-only Maven build can shade stale dependencies from the local Maven cache,
even when current plugin classes compile. Do not deploy that JAR by copying it
directly; use the validated update tool.

For a chosen existing package:

```powershell
python use-plugin/tools/update-runnable-plugin.py --check-only --plugin use-assembly/target/new/lib/plugins/use-jacamo-plugin-1.0.1.jar
```

This compares against the current reactor outputs, so build them first.
