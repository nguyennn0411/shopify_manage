# HR security model

HR là một domain trong backend modular monolith nhưng có ranh giới quyền riêng. Không cấp nhóm quyền HR bằng role trưởng phòng chung.

| Dữ liệu / thao tác | Quyền đọc | Quyền ghi / duyệt |
|---|---|---|
| Hồ sơ công việc cơ bản | `hr.employee.read.basic` | `hr.employee.write` |
| Định danh, liên hệ cá nhân, liên hệ khẩn cấp, hồ sơ riêng | `hr.employee.read.sensitive` | `hr.employee.write` |
| Tuyển dụng | `hr.recruitment.read` | `hr.recruitment.write` |
| Onboarding | `hr.onboarding.read` | `hr.onboarding.write` |
| Chấm công | `hr.attendance.read` | `hr.attendance.write`, `hr.attendance.approve` |
| Nghỉ phép | `hr.leave.read` | `hr.leave.write`, `hr.leave.approve` |
| Phiếu lương của chính mình | `hr.payroll.read.own` | Không có |
| Payroll toàn công ty | `hr.payroll.read.all` | `hr.payroll.write` |
| Đánh giá | `hr.performance.read` | `hr.performance.write` |
| Đào tạo | `hr.training.read` | `hr.training.write` |
| Offboarding | `hr.offboarding.read` | `hr.offboarding.write` |

`hr.access` chỉ hiển thị khu vực HR và cho phép tìm hồ sơ nhân viên gắn với chính Keycloak subject. Role này không cấp quyền đọc bất kỳ danh sách nghiệp vụ nào.

## Sensitive data

- `employee_private_profiles`, salary, payslip, offer compensation và lý do nghỉ việc chi tiết được mã hóa AES-256-GCM bằng `HR_DATA_ENCRYPTION_KEY`.
- Mỗi lần mã hóa dùng IV ngẫu nhiên; database không lưu plaintext.
- Danh sách nhân viên cơ bản không join bảng private và không trả các trường nhạy cảm.
- API private profile yêu cầu riêng `hr.employee.read.sensitive`.
- API payslip chỉ cho `hr.payroll.read.all`, hoặc `hr.payroll.read.own` khi Keycloak subject khớp chính xác với hồ sơ nhân viên.
- File đính kèm chỉ lưu object key và classification trong PostgreSQL; file production phải nằm trong private object storage, dùng signed URL ngắn hạn và được quét malware trước khi tải xuống.

Khóa mã hóa phải được tạo bằng secret manager, sao lưu có kiểm soát và có kế hoạch rotation. Mất khóa đồng nghĩa không thể khôi phục dữ liệu đã mã hóa.

`keycloak-role-sync` chạy idempotent khi khởi động Docker Compose để bổ sung các realm role HR cho cả realm mới và realm đã tồn tại. Script không in mật khẩu quản trị hoặc token ra log.

## Endpoint nhạy cảm được bảo vệ

Ngoài ranh giới quyền ở trên, các endpoint sau đòi hỏi quyền riêng và không được cấp mặc định cho vai trò trưởng phòng:

| Endpoint | Quyền | Ghi chú |
|---|---|---|
| `GET /api/v1/hr/employees` | `hr.employee.read.basic` | Danh sách cơ bản, không join bảng private, không có lương. |
| `GET /api/v1/hr/employees/{id}/private` | `hr.employee.read.sensitive` | Định danh, liên hệ cá nhân, liên hệ khẩn cấp; trả về đã giải mã. |
| `PATCH /api/v1/hr/employees/{id}/private` | `hr.employee.write` | Ghi đè vào `employee_private_profiles`, mọi giá trị được mã hóa AES-256-GCM. |
| `GET /api/v1/hr/employees/{id}/documents` | `hr.employee.read.sensitive` | Hồ sơ đính kèm chỉ trả metadata và object key, không trả nội dung file. |
| `POST /api/v1/hr/employees/documents` | `hr.employee.write` | Ghi object key + classification; file thật nằm ở private object storage. |
| `GET /api/v1/hr/payroll/runs` | `hr.payroll.read.all` | Bảng lương toàn công ty. |
| `GET /api/v1/hr/payroll/salary-structures/{employeeId}` | `hr.payroll.read.all` | Cấu trúc lương đã giải mã. |
| `GET /api/v1/hr/payroll/runs/{id}/history` | `hr.payroll.read.all` | Lịch sử điều chỉnh lương từ audit log. |
| `POST /api/v1/hr/payroll/runs/{id}/publish-payslips` | `hr.payroll.write` | Sinh phiếu lương mã hóa cho từng nhân viên. |
| `GET /api/v1/hr/payroll/payslips` | `hr.payroll.read.all`, hoặc `hr.payroll.read.own` khi subject khớp hồ sơ nhân viên | Kiểm tra bằng `@hrAccess.canViewPayslips`. |
| `GET /api/v1/hr/attendance/adjustments` | `hr.attendance.approve` | Danh sách đề xuất chỉnh sửa chấm công. |
| `POST /api/v1/hr/attendance/adjustments/{id}/review` | `hr.attendance.approve` | Phê duyệt thay đổi giờ vào/ra. |

Vai trò `hr.access` chỉ mở khu vực HR và cho phép tra cứu hồ sơ nhân viên gắn với chính Keycloak subject (`/hr/employees/me`). Nó không cấp quyền đọc bất kỳ danh sách nghiệp vụ nào.

Mọi thao tác ghi trong phân hệ HR đều ghi `audit_logs` với actor, hành động và loại tài nguyên để tra cứu về sau.
