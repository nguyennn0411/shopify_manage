# Database and migration policy

- PostgreSQL schema nghiệp vụ là `erp_v2` và Flyway là công cụ duy nhất thay đổi schema này.
- Keycloak tự quản lý schema của Keycloak; migration nghiệp vụ không sửa bảng Keycloak.
- Migration đã chạy ở shared environment là immutable.
- Mỗi thay đổi tạo file `V<n>__description.sql` mới.
- Dùng expand/contract: thêm cấu trúc, backfill, chuyển code, rồi xóa cấu trúc cũ ở release khác.
- Column mới nên nullable hoặc có default an toàn trước khi backfill.
- Index lớn phải đánh giá lock và thời gian chạy trên dữ liệu gần production.
- Reference data cho company mới được tạo qua `CompanyReferenceDataProvisioner`, không chỉ bằng migration chạy một lần.
- Dữ liệu demo không nằm trong production migration.

## CI gate

Profile Maven `integration` khởi động PostgreSQL 18 thật bằng Testcontainers, áp dụng toàn bộ migration, chạy Flyway validate và kiểm tra company reference provisioning chạy lặp không tạo bản ghi trùng.

```text
mvn -f apps/backend/pom.xml -Pintegration verify
```

Trước migration rủi ro cao phải có backup/PITR hợp lệ, ghi lại RPO/RTO và diễn tập restore ở staging. “Có backup” chưa đủ nếu chưa chứng minh restore thành công.
