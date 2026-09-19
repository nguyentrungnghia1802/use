# Agent Working Rules

## 1. Mục tiêu

Bạn là implementation agent cho dự án USE JaCaMo Plugin.

Ưu tiên:
1. semantic correctness;
2. testability;
3. traceability;
4. minimal, maintainable implementation;
5. hoàn thành task đã chọn trước khi mở rộng scope.

Không tối ưu UI/performance trước correctness.

---

## 2. Source of truth

Thứ tự:
1. `Core/Metamodel/JaCaMo-Metamodel.ecore`
2. `Core/Mapping/jacamo-use-mapping-v1.json` + schema/freeze/audit
3. file liên quan trong `docs/project/`
4. `docs/agent/task.md`
5. code hiện tại

Nếu code mâu thuẫn specification đã freeze, sửa code.
Nếu specification tự mâu thuẫn, dừng phần mâu thuẫn và ghi diagnostic/issue rõ ràng; không tự đoán semantics.

---

## 3. Quy tắc đọc tài liệu — tránh lãng phí token

Không đọc toàn bộ repository hoặc toàn bộ `docs/project/` theo mặc định.

Trước mỗi task:
1. đọc task tương ứng trong `docs/agent/task.md`;
2. đọc `agent.md`;
3. đọc tối đa các tài liệu project được task chỉ định;
4. search symbol/file liên quan;
5. chỉ mở source files trực tiếp cần sửa;
6. mở thêm tài liệu khi dependency thực sự yêu cầu.

Không dump toàn bộ cây source nếu không cần.
Không đọc file generated/build/vendor trừ khi task yêu cầu.

---

## 4. Git workflow bắt buộc

### 4.1 Trước khi làm việc
Chạy:
```bash
git status
git branch --show-current
git log -n 8 --oneline
```

Nếu working tree có thay đổi chưa commit:
1. xem `git diff` và `git diff --staged`;
2. xác định thay đổi thuộc task/phase nào;
3. đọc task/docs liên quan;
4. nếu là công việc dang dở hợp lệ, hoàn thiện nó;
5. chạy test;
6. commit;
7. push nếu remote branch tồn tại;
8. chỉ sau đó bắt đầu task mới.

Không discard/reset thay đổi không phải do bạn tạo nếu chưa hiểu rõ.

### 4.2 Branch
Mỗi phase dùng branch:
```text
phase/<NN>-<short-name>
```

Ví dụ:
```text
phase/04-use-transformation
```

Nếu task lớn/rủi ro có thể tạo:
```text
feat/<phase>-<feature>
fix/<phase>-<bug>
```

Không code feature trực tiếp trên `main`/`master`.

### 4.3 Trong khi triển khai
- commit theo đơn vị thay đổi có test;
- không gom nhiều subsystem độc lập vào một commit;
- message theo Conventional Commits:
  - `feat:`
  - `fix:`
  - `test:`
  - `docs:`
  - `refactor:`
  - `build:`
  - `chore:`

### 4.4 Kết thúc branch/phase
1. `git status` sạch;
2. chạy test bắt buộc của phase;
3. chạy regression liên quan;
4. cập nhật checklist `task.md`;
5. commit docs/test cuối;
6. push branch;
7. merge vào branch chính theo policy repository;
8. chạy smoke test sau merge;
9. push branch chính;
10. không xóa branch trước khi xác nhận merge/push thành công.

Không force-push branch chính.
Không dùng destructive reset/clean nếu chưa chắc chắn.

---

## 5. Development loop

Cho mỗi task:
1. xác định interface/acceptance criteria;
2. viết hoặc cập nhật failing test trước khi sửa logic khi khả thi;
3. chạy test và xác nhận fail đúng lý do;
4. implement nhỏ nhất đúng spec;
5. chạy test mục tiêu;
6. chạy regression gần nhất;
7. inspect diff;
8. cập nhật docs/checklist nếu contract thay đổi;
9. commit.

Không claim "done" khi chưa có command/test evidence.

---

## 6. Semantic rules

### 6.1 Không đoán
Nếu:
- mapping unresolved;
- symbol ambiguous;
- runtime entity chưa trace;
- datatype không biết;
- operation owner không rõ;

thì:
- giữ unresolved;
- emit diagnostic;
- yêu cầu explicit binding nếu phù hợp.

Không dùng fuzzy/name similarity làm formal semantics.

### 6.2 Phân tầng
Không trộn:
- metamodel mapping;
- instance transformation;
- binding/resolution;
- constraint translation;
- runtime state;
- OCL verification.

### 6.3 Norm/OCL
Không tự động biến Moise obligation/permission/prohibition thành OCL invariant.
Giữ normative semantics riêng.
Combined verification chỉ làm khi rule/evidence được định nghĩa rõ.

### 6.4 Runtime
USE là verification mirror.
JaCaMo là execution engine.
Baseline plugin quan sát và report; không block Agent action.

---

## 7. Architecture boundaries

Parser:
- không gọi UI;
- không gọi USE APIs trực tiếp.

Semantic model:
- không phụ thuộc UI/runtime connector.

Mapping:
- đọc declarative mapping;
- không hard-code Auction.

Constraint translator:
- phải ghi translation status/provenance.

USE adapter:
- cô lập dependency USE.

Runtime adapter:
- dùng trace;
- không reparse project cho mỗi event.

UI:
- chỉ gọi service/facade;
- không chứa transformation logic.

---

## 8. Testing rules

Bắt buộc test:
- happy path;
- invalid input;
- ambiguity;
- missing source/reference;
- deterministic output;
- regression cho bug.

Mapping thay đổi:
- chạy full mapping audit.

Parser thay đổi:
- chạy fixtures dimension tương ứng + Auction import.

Transformation/OCL thay đổi:
- compile generated USE/OCL.

Runtime thay đổi:
- synthetic event tests + reconnect/resync relevant tests.

Trước merge phase:
- chạy full suite khả thi trong repository;
- ghi command và kết quả trong commit/phase notes nếu project có convention.

---

## 9. Documentation rules

Khi thay public/internal contract:
- cập nhật đúng file `docs/project/`;
- cập nhật `task.md`;
- không tạo tài liệu trùng lặp nếu có file source-of-truth.

Khi phát hiện assumption mới:
- ghi explicit;
- phân loại source-derived vs our extension.

Không viết "TBD" để né quyết định trong task đang thực hiện; nếu chưa giải được, tạo explicit blocker/diagnostic với evidence.

---

## 10. Generated artifacts

Không chỉnh tay generated output để "làm test pass".
Sửa generator/source.

Golden files chỉ update khi:
- behavior change intentional;
- diff đã review;
- source/mapping version tương ứng.

---

## 11. Error handling

Mọi lỗi user-facing phải:
- có diagnostic code;
- phase;
- source location nếu có;
- message actionable.

Không catch exception rồi bỏ qua.
Không silently fallback sang heuristic.

---

## 12. Performance

Correctness trước.
Chỉ optimize sau khi:
- test coverage có;
- benchmark chỉ ra bottleneck.

Không đổi semantics để nhanh hơn.

---

## 13. Security

Không tự chạy arbitrary script từ imported JaCaMo project.
Validate path/include.
Không cho path traversal khỏi project root trừ explicit configured source path.
Reflection/class loading phải được cô lập và document.

---

## 14. Task completion definition

Một task chỉ `[x]` khi:
- code hoàn thành;
- test mục tiêu pass;
- regression liên quan pass;
- diagnostics/docs cập nhật;
- diff review;
- commit tồn tại.

Một phase chỉ `[x]` khi:
- tất cả task phase done;
- acceptance gate pass;
- branch merged;
- push thành công.

---

## 15. Khi bị gián đoạn

Khi quay lại:
1. đọc `git status`;
2. inspect branch/log/diff;
3. đọc phase task hiện tại;
4. xác định bước cuối đã hoàn thành bằng evidence;
5. tiếp tục từ đó.

Không bắt đầu lại từ đầu và không tạo implementation song song nếu nhánh hiện tại đã có công việc hợp lệ.
