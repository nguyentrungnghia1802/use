!new AtomicAgent('JaCaMoLauncherAgEnv')
!new AtomicAgent('JaCaMoLauncherAgOrg')
!new AtomicAgent('alice')
!new AtomicAgent('bob')
!new Counter('counter')
!new R_worker('enactment')
!new R_reviewer('enactment_e4b912b334b2340c')
!new WorkspaceObservation('main')
!new WorkspaceObservation('main_bdba6bc483efef0f')
!new WorkspaceObservation('org')
!new C_team('team')
!counter.p := -1
!insert (team,enactment) into InContext
!insert (team,enactment_e4b912b334b2340c) into InContext
!insert (alice,enactment) into Plays
!insert (bob,enactment_e4b912b334b2340c) into Plays
