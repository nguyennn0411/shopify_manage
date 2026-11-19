# Productivity security model

## Phạm vi

Module `productivity` cung cấp các năng lực dùng chung tại `/productivity`: tìm kiếm, hộp việc cần xử lý, thông báo, cấu hình nhận thông báo, bộ lọc đã lưu và trang đọc audit. Mọi truy vấn đều yêu cầu membership đang hoạt động trong đúng công ty.

## Quyền

| Quyền | Mục đích |
|---|---|
| `platform.search` | Mở Trung tâm cá nhân và tìm kiếm |
| `notification.read` | Xem, đánh dấu và lưu trữ thông báo của chính mình |
| `notification.write` | Tạo nhắc việc cho chính mình |
| `notification.manage` | Gửi thông báo cho thành viên khác |
| `saved-view.read` | Xem bộ lọc cá nhân và bộ lọc được chia sẻ |
| `saved-view.write` | Tạo, cập nhật, xóa bộ lọc của chính mình |
| `saved-view.share` | Chia sẻ bộ lọc với công ty |
| `audit.read` | Xem nhật ký kiểm toán của công ty |

`MANAGER` và `EMPLOYEE` có tìm kiếm, thông báo cá nhân và saved views. `AUDITOR` có tìm kiếm, thông báo đọc, saved views đọc và `audit.read`. `notification.manage` và `saved-view.share` không được cấp mặc định cho nhân viên.

## Tìm kiếm không làm lộ dữ liệu

`platform.search` chỉ cho phép gọi endpoint tìm kiếm; nó không thay thế quyền nghiệp vụ. Mỗi nhóm kết quả phải vượt qua hai lớp kiểm tra:

1. Người dùng có permission đọc của phân hệ tương ứng.
2. Đối tượng cụ thể nằm trong phạm vi được cấp: quan hệ thành viên/chủ sở hữu, dự án, phòng ban, ACL tài liệu hoặc người tham gia quy trình.

Hiện tại chỉ lập chỉ mục logic cho task, khách hàng, tài liệu, bài viết tri thức và yêu cầu phê duyệt. Dữ liệu lương, định danh cá nhân, số tài khoản, nội dung pháp lý nhạy cảm và metadata audit không được đưa vào kết quả tìm kiếm phổ thông.

## Thông báo và saved views

Thông báo luôn được đọc theo `target_subject` bằng subject đăng nhập; client không thể truyền subject khác để xem. Tạo thông báo cho người khác yêu cầu `notification.manage` và người nhận phải là thành viên đang hoạt động. Saved view chỉ được xóa bởi chủ sở hữu; cấu hình JSON được parse và kiểm tra là object trước khi lưu.

Email hiện mới có preference và chưa gửi ra ngoài. Khi bổ sung worker email phải áp dụng quiet hours, retry có giới hạn, unsubscribe theo nhóm và không đưa dữ liệu nhạy cảm vào tiêu đề/nội dung email.

## Audit và kiểm thử

Trang audit yêu cầu riêng `audit.read`; quyền tìm kiếm không bao hàm quyền này. `ProductivitySecurityTest` kiểm tra quyền mở trung tâm, validation truy vấn, tách quyền đọc/ghi thông báo và quyền audit. `FlywayMigrationIT` kiểm tra migration V14, tạo/đọc thông báo, saved view và tìm kiếm theo phạm vi trên PostgreSQL thật khi Docker khả dụng.
