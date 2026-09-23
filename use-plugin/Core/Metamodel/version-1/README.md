# Core — DSML4JaCaMo 2024 Ecore

Canonical duy nhất: [JaCaMo-Metamodel.ecore](JaCaMo-Metamodel.ecore), reconstruction
bảo thủ từ **DSML4JaCaMo: A Modelling tool for Multi-agent Programming with JaCaMo**,
DOI `10.15439/2024F6157`; không phải Ecore gốc tác giả.

Thứ tự bằng chứng: [PDF](B%C3%A0i%20b%C3%A1o%201.pdf), Figure 1 trang PDF 3/trang in 639
→ Section III trang 2 → Section IV/Listing 1 trang 2,4 → draw.io visual aid → target Ecore.
[Metamodel-2024.jpg](Metamodel-2024.jpg) là ảnh Figure;
[draw.io](JaCaMo-Metamodel.drawio) là editable trace có metadata ChatGPT, không phải
bằng chứng tác giả độc lập.

## VERIFIED — phần nguồn nhìn thấy

Chạy lại ngày 2026-09-13:

- 37 EClass: abstract `AbsOperation`, `BodyTerm`, `Action`; 34 concrete.
- 67 EAttribute có datatype nhìn thấy; 13 default tường minh.
- 63 EReference: 27 containment, 36 non-containment; 14 generalization.
- Exact spelling, source/target/direction/bounds/diamond/self-reference/inheritance
  khớp [forensic audit](../audit/DSML4JaCaMo-2024-forensic-audit.md).
- XML, baseline validator + 20 mutation controls, EMF Diagnostician PASS;
  0 load errors/warnings, 0 unresolved proxies, severity 0.
- 174 EAnnotation provenance/decision/unresolved; **0 EOperation, 0 EParameter**.
  `Operation` là EClass, không phải khai báo operation signature.

SHA-256 canonical:
`c0aafab786c5ff3fcb468aeaf1b18b62865292e6590ffca2b9b2e962a9067fe7`.
Cả PDF/image/draw.io/Ecore giữ nguyên bytes so với forensic audit trước khi chuyển vào Core.

## UNRESOLVED

| Field | Bằng chứng và xử lý |
| --- | --- |
| `ObsProperty.initialValue` | Thấy tên; datatype/default bị che; EAnnotation trên lớp, chưa có EAttribute |
| `AbsOperation.paramName` | Thấy tên; datatype/default bị che; EAnnotation, chưa có EAttribute |
| `TriggeringEvent.addAndDel` | Thấy tên; datatype/default bị che; EAnnotation, chưa có EAttribute |
| `Message.isBroadcast` | Thấy `EBoolean =`, literal bị cắt; giữ EBoolean, `defaultValueLiteral=null`, annotation unresolved |

Effective EBoolean default `false` không phải default tác giả đã khôi phục.
70 tên attribute nhìn thấy gồm 67 khai báo + 3 annotation; tổng số thuộc tính ẩn
sau scrollbar không xác định được.

## INFERRED_NOT_PROVEN

- `nsURI=urn:dsml4jacamo:2024:reconstructed` và nsPrefix là metadata địa phương;
  package name được Listing 1 hỗ trợ nhưng không chứng minh namespace gốc.
- Attribute bounds `0..1` là mặc định Ecore, paper không in các bounds đó.
- Flags mặc định ordered/unique và việc không khai báo eOpposite không chứng minh
  toàn bộ serialization gốc. Default không hiển thị không được tự điền.

## Không tự sửa

Giữ `MAS.PlatformParamters`, `ObsProperty.obsproperty -> Belief [0..*]`
non-containment; Norm/Group/Role/Scheme kế thừa Organisation; hierarchy BodyTerm/Action,
bốn subtype AbsOperation và năm self-reference không diamond. Không đổi theo trực giác,
không thêm kiểu/default đoán, OCL hoặc runtime extensions.

Direct containment type graph không có cycle; khi xét feature thừa kế có 13 cycle
**mức kiểu**, ví dụ Norm → NormativeSpecification → Norm. Đây là recursion theo Figure,
không phải bằng chứng vòng containment EObject; không xóa inheritance để né recursion.

Từ root: `python validate_dsml4jacamo_ecore.py --self-test`.
Xem [hướng dẫn EMF/tái audit](../audit/README.md). Sau thay đổi có bằng chứng phải
reconcile [mapping](../mapping/README.md) bằng cả hash và structural diff; không chỉ đổi hash.
