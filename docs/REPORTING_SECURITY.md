# Executive Reporting & Strategic Goals security model

## Nguyên tắc

Reporting là read model tổng hợp từ các phân hệ, không phải lối tắt bỏ qua quyền dữ liệu. Backend kiểm tra permission trước, sau đó áp dụng scope ngay trong từng truy vấn KPI. Frontend chỉ quyết định cách hiển thị.

## Phân quyền

| Quyền | Khả năng |
|---|---|
| `reporting.access` | Hiển thị menu trung tâm điều hành |
| `reporting.read` | Xem KPI, xu hướng, sức khỏe dự án và snapshot được phép |
| `reporting.manage` | Chốt snapshot báo cáo toàn công ty |
| `reporting.export` | Xuất báo cáo CSV |
| `strategy.read` | Xem mục tiêu và Key Result trong phạm vi |
| `strategy.write` | Tạo/cập nhật mục tiêu và Key Result trong phạm vi |

`EXECUTIVE_MANAGER` có toàn bộ quyền nhưng vẫn cần scope `COMPANY` nếu quyền đến từ vai trò trong database. Quyền realm trực tiếp của Keycloak và `super-admin` được xem là quyền toàn cục. `MANAGER` chỉ có quyền xem báo cáo và quản lý mục tiêu trong scope được gán; không có `reporting.manage` hoặc `reporting.export`. `AUDITOR` có quyền đọc nhưng không thay đổi hoặc xuất mặc định.

## Áp dụng phạm vi

- Nhân sự: công ty, chi nhánh, phòng ban, team, self hoặc hồ sơ gắn với subject hiện tại.
- Dự án/task: chủ dự án, thành viên dự án, người được giao/giám sát hoặc scope project/department/branch.
- Tài chính: chỉ bản ghi gắn phòng ban/dự án nằm trong scope; quyền công ty mới xem toàn bộ.
- CRM: cơ hội do người dùng sở hữu hoặc nhân sự thuộc phòng ban/chi nhánh được cấp.
- Phê duyệt: người gửi, người đang duyệt hoặc scope phòng ban/dự án.
- Kho: số liệu tổng hợp toàn kho chỉ trả khi có scope công ty.
- Mục tiêu: chủ sở hữu hoặc scope khớp `DEPARTMENT`/`PROJECT`; mục tiêu `COMPANY` yêu cầu quyền toàn cục.

## Tính đúng dữ liệu

- Thu, chi, lợi nhuận, pipeline và công nợ chỉ cộng bản ghi cùng `base_currency` của công ty.
- KPI tài chính trên dashboard tính từ ngày đầu năm tới hiện tại. Snapshot sử dụng đúng khoảng ngày người dùng chọn.
- Key Result hỗ trợ cả mục tiêu tăng và giảm bằng công thức `(current - baseline) / (target - baseline)`, giới hạn tiến độ từ 0–100% và tính trung bình theo trọng số.
- Snapshot lưu payload JSON tại thời điểm tạo và không có API sửa/xóa, giúp đối chiếu audit.
- CSV chỉ chứa số liệu tổng hợp, có BOM UTF-8 và header tải xuống; quyền xuất tách khỏi quyền xem.

## Giới hạn hiện tại

- Chưa quy đổi tỷ giá giữa nhiều đồng tiền; dữ liệu khác `base_currency` bị loại khỏi tổng tiền.
- Chưa có kho dữ liệu/OLAP; KPI được đọc trực tiếp từ PostgreSQL và phù hợp quy mô modular monolith hiện tại.
- Chưa có AI giải thích xu hướng hoặc gửi báo cáo ra email. Hai khả năng này cần quy trình kiểm tra quyền và kênh egress riêng.
