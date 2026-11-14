# Executed from D:\_CODE_BANK\Project_\08_Thesis\use after the 30-test focused rebuild.
# Compiled native consumer and official-runtime classpath were checked first.
& '.\use-plugin\tools\jacamo-bridge.ps1' `
  -JcmPath 'D:\_CODE_BANK\Project_\08_Thesis\jacamo\examples\auction\auction.jcm' `
  -OclProfilePath 'D:\_CODE_BANK\Project_\08_Thesis\use\use-plugin\src\test\resources\jacamo\ocl\auction\auction_demo.ocl' `
  -EvidenceDirectory 'D:\_CODE_BANK\Project_\08_Thesis\use\use-plugin\evidence\auction-ocl-transition' `
  -TimeoutSeconds 240 -ObservationSeconds 75 -MaxBufferedEvents 8192 -SkipBuild
