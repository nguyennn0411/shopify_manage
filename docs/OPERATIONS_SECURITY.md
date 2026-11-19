# Bảo mật phân hệ Vận hành

## Vai trò và quyền

- `OPERATIONS_MANAGER` có quyền quản lý nhà cung cấp, yêu cầu mua, đơn mua, kho, tài sản, bảo trì, license, sản xuất và vận chuyển.
- Quyền `procurement.approve` và `inventory.count.approve` không được cấp mặc định cho `OPERATIONS_MANAGER`; quản trị công ty phải cấp riêng cho người có thẩm quyền.
- Các quyền tài sản, bảo trì, license và sản xuất tách riêng đọc/ghi để tổ chức có thể tạo vai trò hẹp hơn.

## Phạm vi dữ liệu

- Yêu cầu mua được giới hạn theo người tạo, phòng ban, dự án hoặc toàn công ty.
- Tài sản được giới hạn theo người đang giữ, phòng ban hoặc toàn công ty.
- Nhà cung cấp, kho và danh mục vật tư là dữ liệu dùng chung cấp công ty; backend luôn kiểm tra membership công ty trước khi trả dữ liệu.
- Giao diện chỉ ẩn/hiện thao tác; backend kiểm tra lại permission và object scope cho từng request.

## Kiểm soát nghiệp vụ

- Duyệt yêu cầu mua giữ ngân sách; nhận hàng chuyển phần đã giữ sang thực tế và từ chối nếu vượt hạn mức.
- Không cho nhận vượt số lượng đơn mua hoặc xuất/chuyển làm tồn khả dụng âm.
- Kiểm kê ghi nhận snapshot tồn; nếu tồn thay đổi trước lúc duyệt thì phải tạo phiếu kiểm kê mới.
- Bàn giao tài sản chỉ áp dụng cho tài sản khả dụng; thu hồi đóng đúng lần bàn giao đang mở.
- Credential phần mềm không được lưu trực tiếp. Trường `credential_reference` chỉ chứa tham chiếu đến secret manager/vault.
- Mọi thao tác ghi quan trọng đều append vào audit log cùng company, người thực hiện, loại tài nguyên và trạng thái.
