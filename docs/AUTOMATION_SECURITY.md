# Automation & Integration security model

## Phạm vi

Module `automation` điều phối workflow nội bộ, lịch chạy, sự kiện, kết nối dịch vụ và hàng đợi webhook theo từng công ty. AI/LLM và đồng bộ hai chiều với nhà cung cấp bên ngoài không nằm trong Phase 9.

## Phân quyền

| Quyền | Khả năng |
|---|---|
| `automation.access` | Mở trung tâm tự động hóa và xem số liệu tổng hợp |
| `automation.read` | Xem workflow, bước và lịch sử chạy |
| `automation.write` | Tạo, bật, tạm dừng hoặc lưu trữ workflow |
| `automation.run` | Chạy thủ công và phát sự kiện workflow |
| `integration.read/write` | Xem hoặc cấu hình connector |
| `webhook.read/write` | Xem hàng đợi, tạo endpoint hoặc yêu cầu retry |

`COMPANY_ADMIN` có toàn bộ quyền. `AUTOMATION_MANAGER` có quyền vận hành automation/integration và tạo thông báo/phê duyệt phục vụ workflow. `MANAGER` chỉ có quyền xem và chạy workflow; không được sửa kết nối hoặc webhook.

## Bảo vệ dữ liệu và secret

- Mọi bảng có `company_id`; controller kiểm tra membership và permission trước khi gọi service.
- Kết nối chỉ lưu `credential_reference` (ví dụ đường dẫn secret manager/vault), không nhận hoặc lưu token/mật khẩu thô.
- URL gửi đi phải là HTTPS, không chứa user/password và bị chặn nếu là localhost, miền local/internal hoặc địa chỉ IP private.
- Trước khi có dispatcher production, cần kiểm tra lại DNS tại thời điểm gửi, không theo redirect, giới hạn timeout/kích thước phản hồi và ký HMAC bằng secret lấy từ vault.
- Payload, lỗi, lần thử và trạng thái dead-letter được lưu để audit; không ghi secret vào payload hoặc error.

## Trạng thái thực thi

- `MANUAL`: người có `automation.run` chủ động chạy.
- `SCHEDULE`: scheduler quét theo chu kỳ, khóa bản ghi bằng `FOR UPDATE SKIP LOCKED` và tính lần chạy tiếp theo.
- `EVENT`: endpoint nội bộ chọn workflow đang hoạt động có `eventType` tương ứng, giới hạn tối đa 50 workflow cho mỗi lần phát.
- Từng bước có trạng thái độc lập; `continueOnError` quyết định có chạy bước tiếp theo khi lỗi hay không.
- `NOTIFICATION` và `APPROVAL` chạy thật trong transaction và dùng dữ liệu của Phase 8/7.
- `WEBHOOK` tạo delivery trong hàng đợi bền vững. Mặc định không có tiến trình tự gửi ra Internet; đây là fail-closed để tránh vô tình chuyển dữ liệu công ty ra đích chưa xác minh.

## Điều kiện bật outbound webhook

Chỉ bật worker sau khi đã chọn nhà quản lý secret, cấu hình allowlist domain, khóa ký HMAC theo endpoint, chính sách phân loại payload, cảnh báo dead-letter và kiểm thử với endpoint staging. Worker phải claim từng delivery theo transaction, gửi với idempotency key và cập nhật retry theo exponential backoff.
