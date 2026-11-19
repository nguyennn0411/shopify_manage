# Mô hình quyền CRM, Sales & Customer Success

Frontend chỉ ẩn hoặc hiện chức năng theo permission. Backend luôn kiểm tra lại thành viên công ty, quyền hành động và phạm vi dữ liệu trước khi đọc hoặc ghi.

## Quyền nghiệp vụ

- Khách hàng: `crm.customer.read`, `crm.customer.write`.
- Pipeline: `crm.pipeline.read`, `crm.pipeline.write`.
- Báo giá: `crm.quote.read`, `crm.quote.write`, `crm.quote.approve`.
- Hợp đồng: `crm.contract.read`, `crm.contract.write`.
- Đơn bán: `crm.order.read`, `crm.order.write`.
- Chăm sóc khách hàng: `crm.support.read`, `crm.support.write`.
- Marketing: `crm.marketing.read`, `crm.marketing.write`.

Quyền phê duyệt báo giá được tách khỏi quyền tạo báo giá. Vai trò `SALES_CRM` không mặc định có `crm.quote.approve`; quyền này dành cho vai trò tùy chỉnh hoặc quản trị viên theo chính sách công ty.

## Phạm vi dữ liệu

- `COMPANY`: toàn bộ dữ liệu CRM trong công ty.
- `DEPARTMENT` và `BRANCH`: khách hàng thuộc nhân viên phụ trách trong đơn vị được cấp.
- `TEAM` và `SELF`: khách hàng thuộc chính nhân viên hoặc nhóm quản lý trực tiếp.
- Cơ hội, báo giá, hợp đồng, đơn bán và ticket kế thừa phạm vi từ khách hàng liên kết.
- Khi người dùng tạo khách hàng mà không chọn người phụ trách, hệ thống tự gán hồ sơ nhân viên đang liên kết với tài khoản để bản ghi không bị mất khỏi phạm vi của người tạo.

## Toàn vẹn và kiểm toán

- Mọi bản ghi liên kết phải thuộc cùng công ty; backend kiểm tra lại khách hàng, lead, giai đoạn, báo giá, hợp đồng và đơn bán trước khi ghi.
- Tổng báo giá và đơn bán được tính lại từ dòng hàng tại backend, không tin giá trị tổng do frontend gửi.
- Tổng lịch thanh toán không được vượt giá trị hợp đồng; số tiền trả hàng không được vượt tổng đơn bán.
- Pipeline dùng xác suất của giai đoạn khi người dùng không nhập xác suất riêng.
- Các thao tác tạo và đổi trạng thái quan trọng đều ghi vào audit log append-only.
