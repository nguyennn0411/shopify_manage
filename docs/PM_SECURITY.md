# Mô hình quyền Project & Work Management

Backend là nơi quyết định quyền truy cập. Việc ẩn nút hoặc trang ở frontend chỉ hỗ trợ trải nghiệm và không thay thế kiểm tra quyền tại API.

## Phạm vi truy cập

- `COMPANY`: truy cập toàn bộ dữ liệu PM trong công ty.
- `PROJECT`: truy cập dự án được cấp trực tiếp; thành viên dự án được tự động cấp phạm vi này.
- `DEPARTMENT` và `BRANCH`: truy cập dữ liệu gắn với nhân viên trong đơn vị tương ứng khi nghiệp vụ hỗ trợ.
- Chủ dự án, thành viên dự án, người được giao việc, người báo cáo sự cố và quản lý trực tiếp chỉ được truy cập các bản ghi liên quan.
- Quyền lấy trực tiếp từ OIDC dành cho quản trị hệ thống vẫn có hiệu lực toàn cục; quyền từ vai trò công ty luôn yêu cầu thành viên đang hoạt động.

## Nhóm quyền

- Dự án: `pm.project.read`, `pm.project.write`.
- Công việc: `pm.task.read`, `pm.task.write`.
- Sprint: `pm.sprint.read`, `pm.sprint.write`.
- Timesheet: `pm.timesheet.read`, `pm.timesheet.read.own`, `pm.timesheet.write`, `pm.timesheet.approve`.
- Nguồn lực: `pm.workload.read`, `pm.workload.write`.
- Báo cáo và sự cố: `pm.report.read`, `pm.issue.read`, `pm.issue.write`.

`pm.workload.read` không cho phép sửa công suất. Người dùng phải có `pm.workload.write` và còn phải nằm trong phạm vi của nhân viên được cập nhật.

## Vai trò mặc định

- `PROJECT_MANAGER`: quản lý dự án, công việc, sprint, timesheet, nguồn lực, báo cáo và sự cố trong phạm vi được giao.
- `MANAGER`: có các quyền vận hành PM trong phạm vi phòng ban, chi nhánh, dự án hoặc nhóm được cấp.
- `EMPLOYEE`: xem dự án/công việc được chia sẻ, cập nhật công việc, ghi và xem timesheet của chính mình, theo dõi sự cố liên quan.
- `COMPANY_ADMIN`: có toàn bộ permission catalog nhưng vẫn phải là thành viên công ty đang hoạt động khi dùng quyền database.

## Quy tắc toàn vẹn

- Sprint và công việc cha phải thuộc cùng dự án với công việc.
- Quan hệ phụ thuộc chỉ được tạo giữa các công việc cùng dự án.
- Timesheet có cả dự án và công việc thì công việc phải thuộc dự án đó.
- Sự cố có cả dự án và công việc thì công việc phải thuộc dự án đó.
- Các thao tác ghi quan trọng tiếp tục đi qua audit hiện có của hệ thống.
