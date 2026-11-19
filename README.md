# Company ERP — Separate Frontend and Backend Monoliths

ERP gồm hai ứng dụng monolith độc lập, không phải microservices và không đóng gói chung một artefact:

- `apps/frontend`: React 19 monolith, tổ chức theo `app`, `core`, `features` và `layout`; build thành một SPA/container riêng.
- `apps/backend`: Spring Boot modular monolith, tổ chức theo domain `identity`, `company`, `dashboard`, `audit` và nền tảng dùng chung `platform`; build thành một JAR/container riêng.
- `infra/nginx`: single-origin reverse proxy và security headers cho môi trường Docker local/self-hosted.
- `infra/keycloak`: realm, MFA TOTP và permission roles. Không chứa user hoặc credential production.
- PostgreSQL lưu dữ liệu nghiệp vụ trong schema `erp_v2`; Redis lưu session và rate-limit counter.
- HR domain gồm hồ sơ, tuyển dụng, onboarding, chấm công, nghỉ phép, payroll, đánh giá, đào tạo và offboarding. Xem [mô hình quyền HR](docs/HR_SECURITY.md).
- Organization domain quản lý hồ sơ công ty, chi nhánh, phòng ban, chức danh, tài khoản thành viên, vai trò tùy chỉnh và phạm vi dữ liệu tại `/organization`.
- HR domain liên kết hồ sơ nhân viên với cơ cấu tổ chức, tách riêng quyền dữ liệu nhạy cảm và quyền payroll; các vai trò mặc định `HR_MANAGER` và `PAYROLL_MANAGER` không được gộp quyền quá mức.
- Project & Work Management quản lý dự án, sprint, công việc, timesheet, nguồn lực, báo cáo và sự cố; dữ liệu được giới hạn theo phạm vi dự án/tổ chức và vai trò `PROJECT_MANAGER`. Xem [mô hình quyền PM](docs/PM_SECURITY.md).
- CRM quản lý khách hàng, pipeline, báo giá, hợp đồng, đơn bán, ticket SLA và chiến dịch marketing; vai trò `SALES_CRM` chỉ truy cập dữ liệu trong phạm vi được giao. Xem [mô hình quyền CRM](docs/CRM_SECURITY.md).
- Finance quản lý thu chi, ngân sách, hóa đơn/công nợ, tạm ứng, chi phí dự án, bút toán và báo cáo quản trị; vai trò `FINANCE_MANAGER` tách quyền lập, duyệt và khóa kỳ. Xem [mô hình quyền Finance](docs/FINANCE_SECURITY.md).
- Operations quản lý nhà cung cấp, mua sắm, nhận hàng, tồn kho, kiểm kê, tài sản, bảo trì, license, sản xuất và vận chuyển; vai trò `OPERATIONS_MANAGER` không mặc định có quyền phê duyệt mua sắm hoặc kiểm kê. Xem [mô hình quyền Operations](docs/OPERATIONS_SECURITY.md).
- Workplace quản lý tài liệu, kho tri thức, phê duyệt, biểu mẫu, xác nhận điện tử nội bộ, helpdesk, kênh trao đổi và hồ sơ pháp lý tại `/workplace`; vai trò `WORKPLACE_MANAGER` không mặc định được quyết định phê duyệt. Xem [mô hình quyền Workplace](docs/WORKPLACE_SECURITY.md).
- Productivity cung cấp tìm kiếm toàn hệ thống theo quyền, hộp việc cần xử lý, thông báo cá nhân, bộ lọc đã lưu và lịch sử kiểm toán tại `/productivity`. Xem [mô hình quyền Productivity](docs/PRODUCTIVITY_SECURITY.md).
- Automation quản lý workflow thủ công/theo lịch/theo sự kiện, bước thông báo/phê duyệt/webhook, kết nối dịch vụ và lịch sử chạy tại `/automation`. Webhook được xếp hàng bền vững nhưng mặc định không gửi dữ liệu ra ngoài cho tới khi cấu hình worker có ký xác thực. Xem [mô hình an toàn Automation](docs/AUTOMATION_SECURITY.md).
- Reporting cung cấp dashboard điều hành theo phạm vi quyền, KPI liên phân hệ, xu hướng sáu tháng, sức khỏe dự án, OKR/Key Result, snapshot bất biến và xuất CSV tại `/reporting`. Xem [mô hình quyền Reporting](docs/REPORTING_SECURITY.md).

Frontend chỉ quyết định cách hiển thị. Mọi quyền truy cập dữ liệu, company scope, CSRF và audit đều được backend kiểm tra lại.

Baseline kiến trúc, ranh giới module, API convention, company scope, môi trường và chính sách migration được cố định tại [docs/architecture](docs/architecture/README.md). Module mới phải tuân theo baseline này.

Backend NestJS/Prisma/PostgreSQL và frontend Next.js trước đây vẫn còn trong `apps/api`, `apps/web` và `packages/database` để đối chiếu trong thời gian chuyển dữ liệu, nhưng không còn là entrypoint trong root scripts hoặc Docker Compose.

## Chạy local

1. Sao chép `.env.example` thành `.env` và thay toàn bộ giá trị `replace-with-*` bằng secret độc lập. `HR_DATA_ENCRYPTION_KEY` phải là khóa 32 byte được mã hóa Base64.
2. Chạy `docker compose up --build`.
3. Mở `http://localhost:8088`. Keycloak Admin đi qua `http://localhost:8088/auth/admin/`.
4. Tạo user trong realm `company-erp`, cấp `super-admin` hoặc các permission role cần thiết. User mới bắt buộc cấu hình TOTP.
5. Tạo company/membership bằng migration hoặc công cụ quản trị trước khi truy cập dữ liệu công ty. Hệ thống không tự sinh dữ liệu production giả.

Để dùng seed local idempotent, đặt `DEV_SEED_ENABLED=true` và `DEV_SEED_KEYCLOAK_SUBJECT` bằng subject của user Keycloak. Seed chỉ chạy với Spring profile `dev`.

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
