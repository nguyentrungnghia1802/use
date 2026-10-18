!new AtomicAgent('JaCaMoLauncherAgEnv')
!new AtomicAgent('JaCaMoLauncherAgOrg')
!new AtomicAgent('alice')
!new AtomicAgent('bob')
!new Counter('counter')
!new R_worker('enactment')
!new R_reviewer('enactment_0056bc23c7da3ca6')
!new WorkspaceObservation('main')
!new WorkspaceObservation('main_0b967ed320d6801b')
!new WorkspaceObservation('org')
!new C_team('team')
!counter.p := -1
!insert (team,enactment) into InContext
!insert (team,enactment_0056bc23c7da3ca6) into InContext
!insert (alice,enactment) into Plays
!insert (bob,enactment_0056bc23c7da3ca6) into Plays
