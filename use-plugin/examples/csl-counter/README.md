# Counter: JaCaMo → CSL/iStar → native USE OCL

Chạy trên USE 7.5, Java 21, chọn `USE4GOAL_OBSERVED_V1` và file `mapping.json`
cùng thư mục. Xem `docs/use4goal-run.md` trong release (source:
`use-plugin/docs/project/use4goal-run.md`). Không dùng distribution USE 7.1.1
của repository tham chiếu use4Goal.

`counter.jcm` khai báo hai agent alice/bob, Artifact `demo.counter.Counter`,
Group team với worker/reviewer, Scheme workflow và Mission m. Alice commit m,
gọi `increment` hai lần rồi `set(-1)`. Bob tiếp tục sống để kiểm tra monitor.
Organization được nạp từ `organization.xml` bằng JaCaMo/MOISE thật.

Mapping là specification được tác giả chọn, không suy diễn active Jason goal
thành iStar satisfaction. File giữ CSL/iStar source thật, binding đến observable
property p và role/player/context, invariant `p >= 0`, operation pre/post với
`p = p@pre + 1`. Sau workload, invariant FAIL tại p=-1 là kết quả mong đợi.
Các achievement Goal/Task có thể giữ FULFILLED theo history gốc trong khi
invariant hiện tại FAIL; cột invocation conformance hiển thị riêng.

Trong GUI: Import → Start → Pause/Resume. Stop monitor chỉ ngắt USE observer;
JaCaMo vẫn chạy. Restart monitor bắt đầu history mới. Tạo `stop-mas.flag`
trong **thư mục staging** được launcher in ra để alice thực thi `.stopMAS`;
USE phải tiếp tục sống. Đóng USE để launcher dọn các process do nó sở hữu.

Fixture fault riêng: producer JVM `-Dcounter.demo.step=2` làm increment tăng2,
nhưng postcondition vẫn tăng1, phải tạo invocation FAIL. Mặc định step=1.
Các properties `counter.demo.ready/release/stopMas` chỉ là file gate của demo
để acceptance test kiểm soát thời điểm; giao tiếp USE–JaCaMo vẫn là TCP Bridge.
