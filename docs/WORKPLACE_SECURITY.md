# Workplace security model

## Phạm vi

Module `workplace` gồm quản lý tài liệu, kho tri thức, phê duyệt, biểu mẫu, xác nhận điện tử nội bộ, helpdesk, trao đổi nội bộ và hồ sơ pháp lý. Mọi bản ghi đều thuộc đúng một `company_id`; backend luôn kiểm tra membership, permission và phạm vi trước khi đọc hoặc thay đổi dữ liệu.

## Quyền

| Nhóm | Quyền chính |
|---|---|
| Workplace | `workplace.access` |
| Tài liệu | `document.read`, `document.write`, `document.share` |
| Kho tri thức | `knowledge.read`, `knowledge.write` |
| Phê duyệt | `approval.read`, `approval.write`, `approval.decide` |
| Biểu mẫu | `form.read`, `form.write`, `form.submit` |
| Xác nhận | `signature.read`, `signature.write`, `signature.sign` |
| Helpdesk | `helpdesk.read`, `helpdesk.write`, `helpdesk.manage` |
| Trao đổi | `communication.read`, `communication.write`, `communication.moderate` |
| Pháp lý | `legal.read`, `legal.write` |

`COMPANY_ADMIN` có toàn bộ quyền. `WORKPLACE_MANAGER` vận hành các phân hệ nhưng không mặc định có `approval.decide`; quyền quyết định phải được cấp riêng. `EMPLOYEE` và `MANAGER` có quyền cơ bản, trong đó `MANAGER` có thể được cấp quyền quyết định theo chính sách công ty.

## Kiểm soát theo đối tượng

- Tài liệu và thư mục hỗ trợ `PRIVATE`, `DEPARTMENT`, `PROJECT`, `COMPANY`; chủ sở hữu và ACL trực tiếp được kiểm tra trước khi trả dữ liệu.
- Yêu cầu phê duyệt chỉ hiển thị cho người tạo, người duyệt hoặc người có phạm vi phù hợp. Chỉ người đang ở bước chờ hiện tại và có `approval.decide` mới quyết định được.
- Yêu cầu xác nhận chỉ hiển thị cho người yêu cầu, người nhận hoặc quản trị được cấp quyền. Ký tuần tự; người ở bước sau không thể ký sớm.
- Ticket chỉ hiển thị cho người yêu cầu, người được phân công hoặc helpdesk manager. Ghi chú nội bộ có endpoint và quyền riêng.
- Kênh phòng ban/dự án yêu cầu phạm vi tương ứng; kênh công ty yêu cầu quyền công ty.

## An toàn tài liệu và bằng chứng

Phiên bản tài liệu lưu object key, MIME type, dung lượng, checksum SHA-256 và trạng thái quét `PENDING`, `CLEAN`, `BLOCKED`. Chỉ phiên bản mới nhất đã `CLEAN` mới được xuất bản. Production phải dùng object storage riêng tư, URL tải xuống có thời hạn, giới hạn loại/dung lượng tệp và dịch vụ quét malware thực; database không lưu binary.

Xác nhận điện tử lưu người ký, thứ tự, thời điểm, quyết định và evidence hash. Cơ chế này phục vụ xác nhận nội bộ/audit, không tự động đáp ứng yêu cầu chữ ký số chuyên dùng, chữ ký số công cộng hoặc quy định pháp lý tại từng quốc gia.

## Audit và kiểm thử

Các thay đổi quan trọng như tạo/xuất bản tài liệu, chia sẻ, phê duyệt, xác nhận, cập nhật ticket và nghĩa vụ pháp lý đều ghi audit append-only. `WorkplaceSecurityTest` kiểm tra ranh giới truy cập, quyền chỉnh sửa tài liệu, quyền quyết định phê duyệt và tách quyền đọc/ghi pháp lý; `FlywayMigrationIT` smoke-test các luồng nghiệp vụ trên PostgreSQL thật khi Docker khả dụng.
