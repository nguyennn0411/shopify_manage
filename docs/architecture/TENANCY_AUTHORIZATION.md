# Company scope and authorization

`company_id` là tenant boundary của dữ liệu nghiệp vụ. Một Keycloak subject chỉ truy cập công ty khi có bản ghi trong `company_memberships`, trừ `super-admin` phục vụ quản trị cấp hệ thống.

```text
Authenticated identity + permission/action + company membership/data scope = allowed request
```

## Mandatory controls

- Frontend chỉ dùng permission để ẩn/hiện UI; backend luôn kiểm tra lại.
- Mọi bảng tenant-owned phải có `company_id`, foreign key và index phục vụ query theo company.
- Query record theo ID phải đồng thời ràng buộc `company_id`; không query ID trước rồi kiểm tra sau.
- Response `404` được ưu tiên khi cần tránh tiết lộ record thuộc công ty khác.
- Dữ liệu lương, định danh, hợp đồng và tài chính có permission riêng ngoài quyền đọc module.
- Audit ghi actor subject, company, action, resource và metadata an toàn.
- Không ghi token, mật khẩu, khóa mã hóa hoặc dữ liệu riêng tư dạng rõ vào audit/log.

## Scope model cho Giai đoạn 1

RBAC hiện tại sẽ được mở rộng bằng `SELF`, `TEAM`, `DEPARTMENT`, `BRANCH`, `PROJECT` và `COMPANY`. Permission vẫn có dạng `domain.resource.action`; data scope được gán riêng, không mã hóa vào tên role.

Mỗi endpoint company-scoped cần test: đúng quyền + đúng công ty, đúng quyền + sai công ty, thiếu quyền, chưa đăng nhập và super-admin khi được cho phép.
