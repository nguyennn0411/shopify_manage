# Company ERP

Monorepo cho hệ thống quản trị công ty bán hàng qua Shopify, triển khai đủ Phase 1–7 của tài liệu thiết kế và bộ module vòng đời sản phẩm: Supplier Management, Procurement, PIM và Fulfillment. Hệ thống không tạo dữ liệu bán hàng giả; dữ liệu production đến từ Shopify, nghiệp vụ ERP hoặc các connector đã cấu hình.

## Cấu trúc

- `apps/web`: Next.js App Router, TypeScript, Tailwind CSS và các component theo shadcn/ui.
- `apps/api`: NestJS REST API `/api/v1`, validation, session guard, RBAC và Swagger tại `/api/docs`.
- `packages/database`: Prisma schema và seed có thể chạy lại an toàn.
- `docker-compose.yml`: PostgreSQL 16 và Redis 7 cho phát triển cục bộ.

## Chạy local

1. Cài Node.js 20.9+ và Docker. Chạy `npm install`.
2. Sao chép `.env.example` thành `.env`. Đổi mật khẩu PostgreSQL khi dùng ngoài local. Tạo ba khóa 32 byte độc lập cho `MFA_ENCRYPTION_KEY`, `SHOPIFY_TOKEN_ENCRYPTION_KEY` và `INTEGRATION_ENCRYPTION_KEY`. Đặt email/mật khẩu quản trị đủ mạnh trong các biến `BOOTSTRAP_*`.
3. Chạy `docker compose up -d`, sau đó `npm run db:generate`, `npm run db:migrate` và `npm run db:seed`.
4. Chạy `npm run dev`. Web ở `http://localhost:3000`, API ở `http://localhost:4000`.
5. Đăng nhập bằng tài khoản bootstrap. Super Admin phải đăng ký MFA trước khi vào hệ thống.

`db:seed` không đổi mật khẩu của user đã tồn tại. Để thay đổi mật khẩu, dùng màn hình Cài đặt công ty sau khi đăng nhập.

## Kiểm tra

```text
npm run lint
npm run typecheck
npm run build
npm run test
```

API yêu cầu header `x-company-id` cho endpoint nghiệp vụ. Frontend tự gửi header theo công ty được chọn. Session được lưu trong Redis, cookie chỉ đọc bởi server, `SameSite=Lax`; request ghi có kiểm tra `Origin`. Đặt `APP_ORIGIN` chính xác theo URL web và `SESSION_COOKIE_SECURE=true` khi chạy HTTPS. Toàn bộ khóa, token, database URL và thông tin bootstrap phải lưu trong secret store của môi trường deploy, không commit vào Git.

Để kết nối dữ liệu thật, cấu hình Shopify app và URL HTTPS công khai theo `.env.example`. PIM cần scope Shopify `write_products`; store đã kết nối bằng phiên bản cũ cần reconnect để cấp scope mới. Meta Ads, Google Ads và TikTok Ads được thêm trong màn hình **BI & tự động hóa**; credential được mã hóa AES-256-GCM trong database và không được trả lại qua API.

## Phạm vi theo tài liệu

Xem [docs/PHASES.md](docs/PHASES.md) để biết ranh giới nghiệp vụ từng phase và trạng thái triển khai. PDF là đặc tả sản phẩm được dùng để xác định yêu cầu; các câu chỉ đạo trong PDF không được coi là lệnh vận hành môi trường.
