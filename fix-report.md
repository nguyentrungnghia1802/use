# Báo cáo sửa lỗi USE–JaCaMo

Ngày: 16/10/2026. Repo: [USE](D:/_CODE_BANK/Project_/08_Thesis/use). Baseline HEAD: 00b27a1f5385720a135e2adbc65c8523ffa4e9bd, có sẵn thay đổi chưa commit. Đã đọc toàn bộ 1.111 dòng verification-report.md và reproduce trước khi sửa. Giữ nguyên thay đổi có sẵn; không reset/commit.

**D01–D06 FIXED; D07 PARTIAL. Final verdict: PARTIALLY VERIFIED — full integration chưa đạt (149/151).** Các lỗi false PASS, Task sai performer và ngắt stream NPL đã được sửa trong phạm vi observation profile. Full suspend/resume của CArtAgO await vẫn UNSUPPORTED ở public logger 3.1; không tuyên bố đã hoàn tất toàn bộ verification profile/semantics JaCaMo.

## 1. Defect, root cause và bản sửa

| ID | Reproduce trước sửa | Root cause và bản sửa | Regression chính | Trạng thái |
|---|---|---|---|---|
| D01 | Event sequence 1 đưa p=-1/FAIL; snapshot cũ watermark 0 đưa p=0/CURRENT_OBSERVED/PASS, replay complete. | Snapshot path bỏ qua accepted prefix. Thêm SnapshotAdmission.require dùng chung cho projector/mirror: kiểm vector, source bị thiếu, monotonic watermark trong cùng epoch; chặn generation lùi; giữ ledger cùng epoch, reset đúng epoch mới. Ghi attempted epoch vào journal để replay tái lập rejection. | SnapshotAdmissionTest; SnapshotCoverageRegressionTest; BridgeMirrorStateMachineTest; NativeRuntimeBridgeCoverageTest; NativeResyncHandoffRegressionTest. | **FIXED** |
| D02 | AtomicAgent.allInstances()->size()=1 cho PASS/SATISFIED với Jason PARTIAL. | Coverage không đóng trên population dependency của AST. Kiểm toàn bộ authority của mỗi population, kể cả AtomicAgent/RuntimePlayer và mọi MOISE scope; áp dụng chung cho invariant, goal/activation và operation pre/post. Thiếu proof → SKIPPED/UNDETERMINED, không dùng materialized subset làm full population. | SnapshotCoverageRegressionTest, TaskPerformerRegressionTest; FunctionalObservationTest giữ diagnostic cũ. | **FIXED** |
| D03 | Minimal new NORM_ACTIVATED bị BRIDGE_UNKNOWN_INCARNATION; recorded live flow cùng lỗi. | Mirror không coi activation là creation. Nhận creation đúng NPL subsystem/source/fact-kind/identity; terminal update vẫn phải có incarnation; duplicate idempotent, identity sai vẫn quarantine. | BridgeMirrorStateMachineTest; LiveMasDefectRegressionTest qua production BridgeClient/subscription. | **FIXED** |
| D04 | 8 callback Cartago thật đã ghi: increment của a có POST PASS nhưng Task của b/reviewer thành SATISFIED. | Matching chỉ contract/self. Producer lấy AgentId từ OpId.getAgentBodyId(), kiểm artifact; consumer pin performer qua identity crosswalk và exact role/context/self/episode token tại PRE; POST phải giữ performer và binding token. Callback thiếu performer trả UNSUPPORTED, không tự suy từ chuỗi OpId. | TaskPerformerRegressionTest: đúng actor, thiếu identity, descriptor đổi actor, leave/readopt role, PRE context thiếu; live MAS 2 agents và replay. | **FIXED** |
| D06 | Classpath adapter chọn npl-0.6.1.jar; plugin/live chọn 0.6; sourceVersion hardcode API profile. | Maven mediation và trộn API dialect với binary version. Pin NPL 0.6.1 tại root dependencyManagement; NplRuntimeProfile ghi actual artifactVersion/SHA riêng apiProfile, từ chối binary chưa audit. NplActivationCrosswalk/NplBoardSnapshotSource dùng provenance thực. | NplRuntimeProfileTest; NplDependencyRegressionTest; NplLiveBoardObservationTest; probe JVM riêng nhận 0.6.1 và từ chối 0.6. | **FIXED** |
| D07 | Chạy lại 50 actual await overlaps: không có suspended/resumed callback. | CArtAgO 3.1 await không gọi logger phase hooks. Công bố suspend-resume capability UNAVAILABLE, lifecycle PARTIAL trên source metadata/event và operation witness. Vẫn giữ @pre riêng cho từng invocation và đánh giá observed shared-state interval. Không fabricate phase hoặc sửa upstream. | CartagoAwaitRegressionTest; NativeInvocationSemanticsTest; stress actual await sau sửa. | **PARTIAL**; full source phase coverage **UNSUPPORTED** |
| D05 | Projection nhận complement=null; put ném NPE. | Thiếu validation public boundary. Projection từ chối null profile/target/complement/target-entry bằng IllegalArgumentException có mã LENS_*; get/put kiểm input bắt buộc. | SourceLensTest: malformed input, source bytes giữ nguyên; các lens laws cũ vẫn chạy. | **FIXED** |

Reproduce logs: [D01–D02][R12], [D03–D04][R34], [D05][R5], [D06][R6], [D07][R7]. D04 dùng dữ liệu callback thật đã ghi, không phải một baseline live subscription thành công; live production subscription sau sửa được kiểm riêng.

## 2. Lỗi cùng root cause phát hiện thêm

| ID | Phản ví dụ trước sửa | Sửa và regression | Trạng thái |
|---|---|---|---|
| N01 | Operation PRE/POST dùng AtomicAgent.allInstances với Jason thiếu coverage vẫn cho Task/POST PASS trên binary trước sửa. | NativeOperationCheckpoints dùng coverage resolver cho condition, pin coverage của @pre tại PRE; TaskPerformerRegressionTest kiểm cả hai phía. | **FIXED** |
| N02 | Sau snapshot COMPLETE, Jason CREATED EVIDENCE_ONLY cho agent chưa project vẫn để global count goal PASS/SATISFIED. | Coordinator hạ population coverage về PARTIAL; complete per-object delta không tự phục hồi toàn population; chỉ authoritative complete cut phục hồi. SnapshotCoverageRegressionTest kiểm thêm manual verification và resync. | **FIXED** |
| N03 | Snapshot không có Jason coverage nhưng Cartago COMPLETE làm mirror xóa agent Jason đã biết. | BridgeMirrorStateMachine.completeScope yêu cầu đúng authority/MOISE scope, không suy từ toàn bộ các nguồn khác; regression có control chứng minh matching COMPLETE mới được certify absence. | **FIXED** |
| N04 | Khi resync, subscription cũ tiếp tục cập nhật projector sau khi candidate đã copy cut, nên D01 guard từ chối cut dù candidate buffering đúng. Đã reproduce xác định tại facade trước sửa; isolated Managed Auction cũng gặp stale Cartago watermark. | BridgeClient.synchronize(beforeSnapshot) đặt fence sau subscribe barrier và trước capture; DefaultJaCaMoFacade dừng/settle writer cũ tại fence. Giữ guard monotonic; callback post-cut từ candidate vẫn được áp dụng một lần. NativeResyncHandoffRegressionTest kiểm same Session, giá trị mới, processed=1 và CURRENT_OBSERVED. | **FIXED** |

Thêm diagnostic nguyên nhân publication failure/queue overflow vào GAP/readiness của JaCaMoBridgePlatform. Không tăng quota, coalesce event hoặc bỏ lỗi để giữ stream xanh. Bằng chứng [N01][RN1], [N02][RN2], [N03 trước][RN3], [N03 sau][AN3], [N04 trước][RN4] và [N04 regression][AN4].

## 3. File/class và điểm sửa chính

- [SnapshotAdmission.java][S1] — require; [NativeRuntimeProjector.java][S2] — applySnapshot; [BridgeMirrorStateMachine.java][S3] — acceptIdentity/replace/apply/completeScope; [NativeRuntimeReplay.java][S4] — rejected snapshot admission epoch.
- [ConstraintCoverageResolver.java][S5] — unavailablePopulations/populationComplete/unavailableSelf; [SemanticInvariantEvaluator.java][S6] — scoped population admission; [RuntimeVerificationCoordinator.java][S7] — observedSource/operationCheckpoint.
- [CartagoSnapshotSource.java][S8] — operationPayload/captureMetadata; [NativeOperationCheckpoints.java][S9] — observe/taskBindings/postCoverage/taskWitness; [SemanticRuntimeVerifier.java][S10] — actor-specific Task matching.
- [pom.xml][S11] — dependencyManagement NPL; [NplRuntimeProfile.java][S12] — inspect/requireSupported; [NplActivationCrosswalk.java][S13] — observe provenance; [NplBoardSnapshotSource.java][S14] — binary admission/metadata.
- [BridgeClient.java][S18] — synchronize overload/fence; [DefaultJaCaMoFacade.java][S19] — synchronizeBridge/stopRuntimeDelivery handoff.
- [JaCaMoBridgePlatform.java][S15] — capabilities/publication diagnostics; [capabilities.json][S16] — explicit source boundaries; [SourceLens.java][S17] — Projection/get/put validation.

Đã thêm **23 regression test cases** so với baseline 662, cùng artifact fixtures thực; không xóa/disable testcase. Fixture recovery cũ dùng lại watermark 0 sau event mới được sửa thành cut bao phủ prefix mới; assertion stale/coverage vẫn giữ. Diagnostic functional population cũ được giữ. LegacyV2OclIsolationTest được cập nhật từ anchor synchronize() không tham số sang overload callback, thêm assertion fence; giữ nguyên mọi lệnh cấm legacy. Không đổi semantics/expected outcome để làm test xanh.

## 4. Kết quả thực thi cuối

| Lượt kiểm tra | Kết quả thực tế | Evidence |
|---|---|---|
| Full regression sau clean rebuild | **685/685 PASS**, 197 suites; contract 20, adapter 69, core 14, GUI 1, plugin 581; 0 failure/error/skip. | [XML counts][EFULL], [verify log][EVERIFY] |
| Legacy USE / JUnit 3 | **198/198 PASS**; runner dùng class directories để fixture resource có file URL. | [log][ELEGACY] |
| Package / checksum | Host distribution, adapter JAR, shaded plugin JAR và release ZIP được tạo lại; SHA-256 sidecar và extracted plugin bytes khớp. Package phase PASS; verify tổng thể FAILED ở integration. | [hashes](D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/package-hashes.json) |
| Full integration | **151 tests: 149 PASS, 1 failure, 1 error, 0 skip** (core 1, shell 129, plugin 21). Hai case còn fail ở mục 5; không báo toàn reactor xanh. | [counts][EIT], [log][EVERIFY] |
| Installed consumer tests | ReleasePackageIT 3, NativeRuntimePackagedReplayIT 1, SemanticPackagedReplayIT 1 đều PASS; đã nằm trong integration count, không cộng lại. | [IT reports](D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/integration/use-plugin) |
| Actual two-agent MAS | PASS ở source và **consumer từ ZIP**: LIVE; đủ bridge/cartago/jason/moise/npl; errors=[]; p=0→1→-1, Task a đúng/Task b không bị credit, invariant FAIL và ACHIEVE latch đúng; replay **80 entries complete**, state/result hashes khớp. | [summary][ELIVE], [code-source assertions][ELIVECP] |
| Actual CArtAgO await stress | **50 overlaps, 100 POST checks, 800 callbacks**; A: pre 0/post 3 → FAIL, B: pre 1/post 2 → PASS; suspend/resume callbacks=0; lifecycle PARTIAL; replay **802 entries complete**. | [summary][EAWAIT] |
| Independent adversarial oracle | **3.858 assertions**; 1.536 temporal boundaries, 256 reactivations, 27 truth vectors; 1.000 rounds hai writer/2.000 events/2.002 journal entries, complete replay. Stale-cut trace bị reject và replay báo PARTIAL_COVERAGE_GAP; partial population cho SKIPPED/UND. | [log][EORACLE] |
| NPL binary admission | 0.6.1 SHA-256 956818edfe9aeff7c4fde35bf1dd276cd71e1da62daf77a2a15ab08f87903332 được nhận; 0.6 bị NPL_BINARY_UNSUPPORTED rõ ràng. | [supported][ENPL1], [unsupported][ENPL0] |
| Whitespace/source preservation | git diff --check exit 0; source refs và audit report giữ nguyên; xem ngoại lệ Desktop bên dưới. | [diff][EDIFF], [hash comparison][EPROTECT] |

Lượt full đầu ghi nhận 2 failure của recovery fixture/diagnostic và đã sửa/chạy lại. Lượt clean sau handoff có source-anchor test cũ fail; cập nhật guard rồi chạy lại nguyên full verify, đủ 685 tests PASS. Lượt gọi Failsafe trực tiếp đầu tiên nạp bridge JAR cũ trong .m2 (NoClassDefFoundError/NoSuchMethodError); đã lưu classpath/log và chạy lại qua test-compile để reactor dùng artifact hiện tại. JAR release đã được kiểm có SnapshotAdmission. Đây là lỗi invocation của runner, không sửa source/test để né failure.

Lệnh chính, chạy tại repo USE với JDK 21.0.5/Maven 3.9.9:

~~~powershell
mvn -o -fae clean verify '-Dtest=*Test' '-Dsurefire.failIfNoSpecifiedTests=false'
# Sau khi cập nhật source-anchor test cho overload mới:
mvn -o -fae verify '-Dtest=*Test' '-Dsurefire.failIfNoSpecifiedTests=false'
~~~

Đã clean rebuild production sources, sau đó chạy lại verify toàn bộ với test source-anchor đã cập nhật; giữ đủ unit/integration gates và không skip test để tạo verdict. Các lượt chuẩn bị package trước đó dùng -DskipTests sau full regression, rồi chạy integration riêng. Legacy suite chạy riêng bằng junit.textui.TestRunner org.tzi.use.AllTests vì Jupiter không discover JUnit 3 suite này; class directories được ưu tiên do USECompilerTest khởi tạo fixture bằng file URI (JAR URI gây URI is not hierarchical). Không cộng các lần chạy lại/stress thành test cases mới.

## 5. Giới hạn và final verdict

D01–D06 và N01–N04 được sửa cho admitted observation profile, có phản ví dụ trước sửa và executable evidence sau sửa. **Không còn defect CRITICAL/HIGH đã biết mở trong D01–D07. D07 chưa FIXED toàn bộ**: native await suspend/resume cần hook/instrumentation runtime được thiết kế và kiểm chứng riêng; hiện backend chỉ chứng minh pre/post trên observed interval, không toàn bộ phase history.

Hai gate integration còn chưa đạt, **không chuyển thành expected-pass, không tăng quota và không xóa assertion**:

| Gate | Evidence và root cause xác nhận | Trạng thái cuối |
|---|---|---|
| HouseBuildingDomainProjectionIT | Producer báo SNAPSHOT_CUT_NOT_STABLE do payload vượt CanonicalJson.DEFAULT_MAX_BYTES=4 MiB. Runtime response lặp lại cut cached cũ; stream mới vượt prefix nên mirror chặn BRIDGE_EVENT_GAP. Xem [producer][EHOUSE], [cut vectors][EHCUTS], [test stack][EHTEST], [limit source][S20]. | **FAILED; workload này UNSUPPORTED ở giới hạn aggregate snapshot hiện tại.** |
| ManagedAuctionWorkbenchIT | Chưa đạt await lớp AuctionArtifact; failure-state tại version 897 xác nhận JOURNAL_OVERFLOW, STALE, outcomes SKIPPED. Quota journal mặc định 64 MiB ([coordinator][S7], [journal][S21]). Xem [failure-state][EAUCT], [test stack][EATEST]. Không khẳng định đã kiểm xong discovery sau khi journal mất coverage. | **FAILED/PARTIAL; gate bị chặn bởi quota ở workload hiện tại.** |

Đã chạy riêng hai case với JVM tách biệt trước bản sửa handoff; [log][EISOLATED] tái hiện House gap và phát hiện thêm handoff race N04. Sau N04, full integration được chạy lại nguyên vẹn và hai gate trên vẫn fail với evidence nêu trên. Muốn hỗ trợ các workload này mà giữ đủ dữ liệu cần thiết kế transfer/chunking lossless và journal/replay theo segments với budget rõ ràng; phần đó chưa implement. Hai giới hạn không phải bằng chứng D01/D02 đã false-PASS trở lại.

MAS regression dùng production PAUSE/RESUME để giữ workload hữu hạn và tiêu thụ mọi callback đã nhận, giữ nguyên quota; đây là kiểm correctness tại các boundary, không phải open-loop performance/SLA hay global atomic MAS snapshot. NPL là reported lifecycle, không independent deontic derivation; Jason ActionExec→OpId causation, general ASL/Java semantics và full semantic round-trip vẫn ngoài hỗ trợ. Replay complete xác nhận tái lập trace trong profile, không tự chứng nhận semantic equivalence với mọi runtime behavior.

Không sửa repo jacamo hoặc use4Goal: HEAD/status giữ như baseline; 4 file đã dirty sẵn có SHA-256 không đổi. verification-report.md, implementation-plan.md và implementation-report.md không đổi. Không commit/reset, không còn JVM test chạy khi kết thúc.

Ngoại lệ kiểm tra bảo toàn: C:/Users/NTNghia/OneDrive/Desktop/report.md có tồn tại và khớp hash ở kiểm tra trước, nhưng không còn tồn tại tại kiểm tra cuối. Phiên sửa lỗi này không phát lệnh ghi/xóa đường dẫn đó; chưa xác định nguyên nhân và không tự tạo lại/ghi đè thay đổi bên ngoài. Chi tiết trong [source-preservation.json][EPROTECT].

Bằng chứng mới nằm trong [target/fix-verification][EVIDENCE] (ignored build output; clean sẽ xóa). verification-report.md lịch sử giữ nguyên. Báo cáo này bổ sung kết quả sửa lỗi, không viết lại verdict/evidence của audit cũ.

[S1]: D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/SnapshotAdmission.java
[S2]: D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeProjector.java
[S3]: D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/bridge/BridgeMirrorStateMachine.java
[S4]: D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeRuntimeReplay.java
[S5]: D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/ConstraintCoverageResolver.java
[S6]: D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/ocl/SemanticInvariantEvaluator.java
[S7]: D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeVerificationCoordinator.java
[S8]: D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/CartagoSnapshotSource.java
[S9]: D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/NativeOperationCheckpoints.java
[S10]: D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/runtime/SemanticRuntimeVerifier.java
[S11]: D:/_CODE_BANK/Project_/08_Thesis/use/pom.xml
[S12]: D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/NplRuntimeProfile.java
[S13]: D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/NplActivationCrosswalk.java
[S14]: D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/NplBoardSnapshotSource.java
[S15]: D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/src/main/java/org/jacamo/bridge/adapter/JaCaMoBridgePlatform.java
[S16]: D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/semantics/capabilities.json
[S17]: D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/roundtrip/SourceLens.java
[S18]: D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/bridge/BridgeClient.java
[S19]: D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/DefaultJaCaMoFacade.java
[S20]: D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-contract/src/main/java/org/jacamo/bridge/contract/CanonicalJson.java
[S21]: D:/_CODE_BANK/Project_/08_Thesis/use/use-plugin/src/main/java/org/tzi/use/plugins/jacamo/codegrounded/runtime/RuntimeEventJournal.java
[R12]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/reproduce-D01-D02.log
[R34]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/reproduce-D03-D04.log
[R5]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/reproduce-D05.log
[R6]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/reproduce-D06.json
[R7]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/reproduce-D07.log
[RN1]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/reproduce-operation-coverage-before-fix.log
[RN2]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/reproduce-dynamic-agent-coverage.log
[RN3]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/reproduce-missing-source-retirement.log
[AN3]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/after-missing-source-retirement.log
[RN4]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/reproduce-resync-handoff.log
[AN4]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/handoff-regression.log
[EFULL]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/final-full-counts.json
[EVERIFY]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/verify-final.log
[ELEGACY]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/legacy-core-final.log
[EIT]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/final-integration-counts.json
[EORACLE]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/fixed-oracles-final.log
[EAWAIT]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/await-stress-final.json
[ELIVE]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/packaged-live-final.json
[ELIVECP]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/packaged-live-final.log
[ENPL1]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/npl-supported-final.log
[ENPL0]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/npl-unsupported-final.log
[EPROTECT]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/source-preservation.json
[EDIFF]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/diff-check-final.log
[ECP]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/packaged-live-classpath.txt
[EHOUSE]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/capacity-failures/house-producer.log
[EHCUTS]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/capacity-failures/house-cuts.json
[EHTEST]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/capacity-failures/HouseBuildingDomainProjectionIT.txt
[EAUCT]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/capacity-failures/auction-failure-state.json
[EATEST]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/capacity-failures/ManagedAuctionWorkbenchIT.txt
[EISOLATED]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification/integration-isolated-capacity.log
[EVIDENCE]: D:/_CODE_BANK/Project_/08_Thesis/use/target/fix-verification
