# Company ERP — Separate Frontend and Backend Monoliths

ERP gồm hai ứng dụng monolith độc lập, không phải microservices và không đóng gói chung một artefact:

- `apps/frontend`: React 19 monolith, tổ chức theo `app`, `core`, `features` và `layout`; build thành một SPA/container riêng.
- `apps/backend`: Spring Boot modular monolith, tổ chức theo domain `identity`, `company`, `dashboard`, `audit` và nền tảng dùng chung `platform`; build thành một JAR/container riêng.
- `infra/nginx`: single-origin reverse proxy và security headers cho môi trường Docker local/self-hosted.
- `infra/keycloak`: realm, MFA TOTP và permission roles. Không chứa user hoặc credential production.
- PostgreSQL lưu dữ liệu nghiệp vụ trong schema `erp_v2`; Redis lưu session và rate-limit counter.
- HR domain gồm hồ sơ, tuyển dụng, onboarding, chấm công, nghỉ phép, payroll, đánh giá, đào tạo và offboarding. Xem [mô hình quyền HR](docs/HR_SECURITY.md).

Frontend chỉ quyết định cách hiển thị. Mọi quyền truy cập dữ liệu, company scope, CSRF và audit đều được backend kiểm tra lại.

Backend NestJS/Prisma/PostgreSQL và frontend Next.js trước đây vẫn còn trong `apps/api`, `apps/web` và `packages/database` để đối chiếu trong thời gian chuyển dữ liệu, nhưng không còn là entrypoint trong root scripts hoặc Docker Compose.

## Chạy local

1. Sao chép `.env.example` thành `.env` và thay toàn bộ giá trị `replace-with-*` bằng secret độc lập. `HR_DATA_ENCRYPTION_KEY` phải là khóa 32 byte được mã hóa Base64.
2. Chạy `docker compose up --build`.
3. Mở `http://localhost:8088`. Keycloak Admin đi qua `http://localhost:8088/auth/admin/`.
4. Tạo user trong realm `company-erp`, cấp `super-admin` hoặc các permission role cần thiết. User mới bắt buộc cấu hình TOTP.
5. Tạo company/membership bằng migration hoặc công cụ quản trị trước khi truy cập dữ liệu công ty. Hệ thống không tự sinh dữ liệu production giả.

## Kiểm tra

```text
npm install
npm run lint
npm run typecheck
npm run test
npm run build
```

Máy không cài Maven vẫn build/test backend được vì Maven chạy trong Docker multi-stage build.

## Production

Vercel Services chạy ba container `frontend`, `backend` và `keycloak` trên cùng origin; `vercel.json` định tuyến giao diện, `/api`, OAuth callback và `/auth`. PostgreSQL/Neon và Redis là dịch vụ managed, không được công khai trực tiếp. Secret mã hóa HR, mật khẩu cơ sở dữ liệu, Redis và tài khoản bootstrap Keycloak chỉ được lưu trong Vercel Environment Variables. Docker Compose với Nginx vẫn là môi trường local/self-hosted. Xem [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
