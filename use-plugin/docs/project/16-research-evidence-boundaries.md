# Research Evidence and Semantic Boundaries

## 1. Evidence labels

Mọi claim/spec quan trọng nên phân loại:
- `[2024]` — trực tiếp từ DSML4JaCaMo baseline/paper reconstruction;
- `[SEMANTIC-CLARIFICATION]` — giải thích từ JaCaMo/Jason/CArtAgO/Moise semantics;
- `[OUR-EXT]` — extension phục vụ verification;
- `[UNCERTAIN]` — chưa đủ evidence.

---

## 2. Không overclaim

Không nói:
- "JaCaMo chưa từng dùng OCL";
- "Moise Norm chỉ là label";
- "2024 thiếu integration" khi relation đã có;
- "runtime metamodel bắt buộc";
- "translated OCL equivalent" nếu translation lossy.

---

## 3. Baseline vs extension

Baseline:
- preserve source structure.

Extension:
- Artifact concrete subclass projection;
- observable property typed state projection;
- USE operation projection;
- trace/provenance;
- runtime mirror;
- constraint translation;
- verification profiles.

Mỗi extension phải có reason và effect.

---

## 4. Normative semantics

Deontic/normative:
- responsibility;
- permission;
- obligation;
- prohibition khi applicable;
- lifecycle ở runtime engine.

OCL:
- state/model/operation boolean constraints.

Combined verification được phép nhưng không collapse semantics.

---

## 5. Runtime claims

Phân biệt:
- JaCaMo runtime có state/events;
- baseline metamodel có/không có class runtime state;
- USE `MSystemState` được dùng làm verification mirror.

Không suy ra một điều từ điều kia.

---

## 6. Reproducibility

Mọi experiment phải pin:
- source project commit;
- plugin commit;
- USE version/commit;
- JaCaMo version;
- metamodel/mapping hashes;
- OCL hashes;
- runtime scenario inputs.
