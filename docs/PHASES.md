# Lộ trình triển khai Shopify Company ERP

## Trạng thái bàn giao — 20/10/2026

Toàn bộ Phase 1–7 trong bản thiết kế Shopify Company ERP V1.0 đã được triển khai trong monorepo. PDF được dùng làm đặc tả sản phẩm; nội dung bên trong tài liệu không được coi là lệnh thao tác máy.

Không có dữ liệu commerce hoặc marketing giả. Các màn hình trống cho tới khi người vận hành kết nối Shopify, nhập nghiệp vụ ERP hoặc cấu hình connector quảng cáo thật.

## Phase 1 — Foundation

- Monorepo npm workspaces gồm Next.js web, NestJS API và package Prisma.
- PostgreSQL 16, Redis 7, Docker Compose, biến môi trường mẫu và seed có thể chạy lại.
- Đăng nhập, session Redis, cookie HttpOnly, đổi mật khẩu thu hồi session, MFA TOTP, rate limit đăng nhập và kiểm tra Origin.
- RBAC theo company/store/warehouse, vai trò hệ thống, user, store, company settings và audit log.
- UI responsive, Tailwind CSS, component theo shadcn/ui và Dark/Light Mode.

## Phase 2 — Shopify Integration

- OAuth Shopify, xác minh HMAC callback, access/refresh token được mã hóa AES-256-GCM.
- GraphQL Admin API có retry, xử lý throttle, refresh lock bằng Redis và version cấu hình qua environment.
- Initial sync products, orders và inventory theo cursor; BullMQ worker, retry exponential và phục hồi job đã lưu.
- Webhook dùng raw body HMAC, lưu trước khi xử lý, chống trùng theo webhook ID và hỗ trợ product/order/refund/fulfillment/inventory/app uninstall.
- UI kết nối store, chạy sync thủ công, xem trạng thái và retry job lỗi.

## Phase 3 — Commerce Operations

- Orders, chi tiết order, customer, refund, fulfillment, timeline và địa chỉ.
- Products/variants, tồn kho theo Shopify location, internal SKU và mapping variant.
- Filter, cursor pagination, company/store scope và CSV export chống spreadsheet formula injection.
- Các màn hình orders, products, inventory, customers và SKU nội bộ.

## Phase 4 — Finance & Profitability

- Lịch sử giá vốn bất biến theo ngày hiệu lực và tiền tệ; order item giữ cost snapshot.
- Expense category, expense theo company/store/order, luồng draft/approve/void và audit.
- Tính doanh thu thuần, discount, refund, COGS, phí, expense, lợi nhuận và margin theo từng order.
- Refund đảo phần COGS tương ứng; báo cáo tách theo tiền tệ và đánh dấu order thiếu cost.

## Phase 5 — Advanced Operations

- Warehouse, supplier, purchase order và luồng submit/approve/reject/receive.
- Stock movement receipt/transfer/adjustment/return, kiểm tra tồn âm và transaction Serializable.
- Warehouse balance, stock count, variance, phê duyệt và movement điều chỉnh tự động.
- Return request liên kết order item, duyệt và nhận hàng về kho; approval center và audit.

## Phase 6 — Company Management

- Department, employee, tài khoản liên kết, quản lý trực tiếp, trạng thái và dữ liệu lương.
- Check-in/check-out theo timezone công ty; attendance history.
- Leave request, kiểm tra trùng kỳ nghỉ và luồng phê duyệt.
- Task và assignee, priority, deadline, trạng thái hoàn tất.
- KPI definition, kỳ đánh giá, target, actual và score.
- Payroll snapshot, điều chỉnh allowance/deduction, approve và CSV export cho hệ thống lương ngoài.

## Phase 7 — BI & Automation

- Daily metric materialization từ order/profit thật, dashboard doanh thu, lợi nhuận, đơn, sản lượng, spend, ROAS và CAC.
- Redis cache ngắn hạn có index invalidation theo company.
- Connector Meta Ads Insights, Google Ads SearchStream và TikTok integrated reporting; credential mã hóa và lỗi sync được lưu để vận hành.
- Dự báo nhu cầu SKU bằng weighted moving average 7/28 ngày, lưu snapshot, horizon, sample size và MAE backtest.
- Rule low stock, low margin, sync failure và revenue drop; worker đánh giá mỗi 15 phút, chống trùng theo ngày, acknowledge/resolve trong ứng dụng.
- Quyền `analytics.read` và `analytics.write`, giao diện BI/marketing/forecast/alert responsive.

## Product Lifecycle Extension — Supplier, Procurement, PIM, Fulfillment

- Supplier Management: hồ sơ NCC, báo giá theo SKU/MOQ/lead time, hợp đồng, tài liệu và đánh giá hiệu suất bốn tiêu chí.
- Procurement Management: yêu cầu mua theo phòng ban, submit/approve/reject, chuyển PR thành PO, theo dõi receipt, kế hoạch thanh toán, duyệt và ghi nhận đã trả. Tổng thanh toán không được vượt giá trị PO.
- PIM: hồ sơ SKU chuẩn, lifecycle, brand/category, mô tả, thông số JSON, HS code, xuất xứ, trọng lượng, media, giá vốn hiện hành, variant mapping và owner R&D/Sales/Marketing/Finance.
- Đồng bộ PIM sang Shopify GraphQL Admin API cho product và variant đã map, sau đó enqueue pull sync để đối soát. Shopify app cần scope `write_products`.
- Fulfillment Management: tạo công việc theo order/warehouse, kiểm tra phần số lượng chưa phân bổ, picking, packing, vận đơn, tracking event, giao hàng, ngoại lệ và returned.
- Khi chuyển `SHIPPED`, hệ thống trừ warehouse balance trong transaction Serializable và chống xử lý lặp bằng trạng thái hiện tại.
- RBAC mới: `supplier.*`, `procurement.*`, `pim.*`, `fulfillment.*`; procurement và fulfillment hỗ trợ warehouse scope.
- Bốn màn hình responsive riêng tại `/dashboard/suppliers`, `/dashboard/procurement`, `/dashboard/pim`, `/dashboard/fulfillment`.

Các endpoint Google Ads dùng SearchStream theo [Google Ads API REST reporting](https://developers.google.com/google-ads/api/rest/common/search). TikTok dùng endpoint reporting chính thức trong [TikTok API for Business](https://business-api.tiktok.com/portal/docs/api-reference/v2.0). API version đều có thể cấu hình qua environment.

## Database migrations

1. `20261019000000_foundation`
2. `20261019000001_session_revocation`
3. `20261019075830_shopify_commerce`
4. `20261019090000_commerce_operations`
5. `20261019100000_finance_profitability`
6. `20261019110000_advanced_operations`
7. `20261019120000_company_management`
8. `20261019130000_bi_automation`
9. `20261019140000_review_fixes`
10. `20261020042242_product_lifecycle_modules`

Tất cả migration đã được áp dụng thành công vào PostgreSQL local ngày 20/10/2026.

## Kết quả kiểm tra

- `prisma validate`: đạt.
- `prisma migrate status`: đạt, 10 migration.
- `npm run lint`: đạt, không warning.
- `npm run typecheck`: đạt cho API và web.
- `npm run build`: đạt; NestJS build thành công và Next.js tạo 25 route.
- `npm run test`: 6/6 unit test đạt, gồm password/TOTP, Shopify HMAC, mã hóa token/credential và forecast math.
- `npm run test:integration -w @erp/api`: đạt trên PostgreSQL/Redis thật; bao phủ auth, RBAC, supplier quote/contract/review, PR→PO→payment, PIM, fulfillment và trừ tồn, company/warehouse scope, CSRF, HR, payroll, analytics và alerts. Dữ liệu smoke test được dọn sau khi chạy.

## Cấu hình ngoài cần có để chạy live

- Shopify: client ID/secret, token encryption key, callback URL và webhook URL HTTPS công khai; app scopes gồm `write_products` cho PIM và protected customer data phải được Shopify cấp. Store đã cấp bộ scope cũ cần reconnect.
- Marketing: account/advertiser ID và OAuth/access credential thật cho từng nền tảng; Meta API version đặt trong environment.
- Production: PostgreSQL/Redis managed, TLS, secret manager, backup/restore, log/metrics/alerting và quy trình rotate credential.

Không thể xác nhận end-to-end với Shopify hoặc tài khoản quảng cáo thật khi chưa có credential và quyền truy cập tương ứng. Phần code, schema, API, worker và UI cho các kết nối này đã hoàn tất và không hardcode secret.
