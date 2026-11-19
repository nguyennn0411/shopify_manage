# Lộ trình triển khai Company Management System

## Phase 0 — Architecture baseline (09/11/2026)

- Chốt React/Vite + Spring Boot modular monolith, PostgreSQL, Redis và Keycloak.
- Ghi ADR, domain boundaries, API convention, tenant/authorization model, environment model và logical ERD.
- Thêm Spring profiles `dev`, `staging`, `production`; production không thể bật development seed.
- Thêm seed local idempotent và company reference provisioning cho company tạo sau migration.
- Thêm request ID validation/propagation vào log và response.
- Thêm ArchUnit cycle rule và PostgreSQL/Flyway integration gate trong CI.
- Quy định forward-only migration, staging rehearsal và backup/restore gate.

## Phase 1 — Organization & Access Control (09/11/2026)

- Hồ sơ pháp nhân, thương hiệu, liên hệ, địa chỉ, tiền tệ, múi giờ và thông tin ngân hàng.
- Chi nhánh, phòng ban đa cấp, chức danh, cấp bậc và quan hệ báo cáo.
- Thành viên công ty với trạng thái mời, kích hoạt và vô hiệu hóa.
- Vai trò hệ thống/tùy chỉnh, permission catalog và quyền nhạy cảm tách riêng.
- Phạm vi dữ liệu `SELF`, `TEAM`, `DEPARTMENT`, `BRANCH`, `PROJECT`, `COMPANY`.
- Quyền gán trong database được hợp nhất với quyền OIDC; thành viên bị khóa mất quyền truy cập ngay.
- Audit cho mọi thao tác cấu hình tổ chức, tài khoản và quyền.
- Giao diện quản trị responsive tại `/organization` và migration PostgreSQL V6.

## Phase 2 — Human Resources & Employee Lifecycle (10/11/2026)

- Hồ sơ nhân viên cơ bản liên kết trực tiếp với chi nhánh, phòng ban, vị trí công việc và quản lý trực tiếp.
- Dữ liệu định danh, liên hệ cá nhân, lương, thư mời và lý do nghỉ việc nhạy cảm được mã hóa AES-256-GCM.
- Tuyển dụng, ứng viên, phỏng vấn, thư mời; onboarding và offboarding theo checklist có tiến độ.
- Chấm công, ca làm, điều chỉnh công có phê duyệt; nghỉ phép, số dư và lịch nghỉ.
- Cấu trúc lương, kỳ lương, phiếu lương; quyền xem lương cá nhân tách khỏi quyền vận hành payroll.
- KPI/OKR, đánh giá định kỳ và 360°, phản hồi, kế hoạch phát triển; đào tạo, chứng chỉ và kỹ năng.
- Tách quyền sửa hồ sơ nhạy cảm `hr.employee.write.sensitive`; vai trò hệ thống `HR_MANAGER` không mặc định có quyền payroll.
- Vai trò `PAYROLL_MANAGER` chỉ có quyền cần thiết cho vận hành tiền lương; mọi thao tác ghi được audit.
- Giao diện HR responsive tại `/hr` cùng các trang nghiệp vụ và migration PostgreSQL V7.

## Phase 3 — Project & Work Management (10/11/2026)

- Quản lý dự án, thành viên, mốc tiến độ, rủi ro và quyết định dự án.
- Quản lý sprint, workflow, công việc, checklist, bình luận, tệp đính kèm và quan hệ phụ thuộc.
- Timesheet cá nhân và toàn công ty, quy trình duyệt giờ làm, tổng hợp nguồn lực và công suất theo nhân viên.
- Báo cáo dự án, chi phí thực tế, giờ đã duyệt và theo dõi sự cố theo dự án hoặc công việc.
- Kiểm tra chéo để sprint, công việc cha, quan hệ phụ thuộc, timesheet và sự cố không thể tham chiếu sang dự án khác.
- Áp dụng phạm vi dữ liệu `PROJECT`, `DEPARTMENT`, `BRANCH`, `COMPANY` cùng quan hệ chủ dự án, thành viên, người được giao và quản lý trực tiếp.
- Bổ sung vai trò `PROJECT_MANAGER`, quyền ghi công suất `pm.workload.write` và giới hạn timesheet cá nhân bằng `pm.timesheet.read.own`.
- Giao diện PM responsive tại `/pm`, migration PostgreSQL V8 và kiểm thử bảo mật cho quyền dự án/công suất.

## Phase 4 — CRM, Sales & Customer Success (10/11/2026)

- Mở rộng hồ sơ khách hàng tập trung với loại cá nhân/doanh nghiệp, phân nhóm, nguồn, người phụ trách, người liên hệ và lịch sử tương tác.
- Quản lý lead, điểm tiềm năng, nguồn chiến dịch, cơ hội kinh doanh và sáu giai đoạn pipeline mặc định từ tiếp nhận đến thắng/thua.
- Báo giá có mẫu dùng lại, dòng hàng, chiết khấu, thuế, phiên bản, thời hạn, trang in/lưu PDF và quyền phê duyệt tách riêng `crm.quote.approve`.
- Hợp đồng khách hàng, thời hạn, giá trị, tệp riêng tư, lịch thanh toán và cảnh báo sắp hết hạn.
- Đơn bán nội bộ, dòng hàng, tiến độ xử lý và yêu cầu trả hàng; liên kết ngược về báo giá và hợp đồng.
- Ticket chăm sóc khách hàng theo kênh, mức ưu tiên, SLA, người xử lý, bình luận, kết quả và điểm hài lòng.
- Chiến dịch marketing, ngân sách/thực chi, lead, chuyển đổi và tỷ lệ chuyển đổi.
- Vai trò mặc định `SALES_CRM`, quyền theo từng phân hệ và phạm vi người phụ trách/phòng ban/chi nhánh/công ty.
- Giao diện CRM responsive tại `/crm`, migration PostgreSQL V9–V10 và smoke test xuyên suốt khách hàng → cơ hội → báo giá → hợp đồng → đơn bán → ticket.

## Phase 5 — Financial Management (13/11/2026)

- Phiếu thu, phiếu chi theo khoản mục, phòng ban, dự án, khách hàng hoặc nhà cung cấp; quy trình lập → gửi duyệt → phê duyệt → ghi nhận.
- Ngân sách theo kỳ, phòng ban, dự án và khoản mục; kiểm tra hạn mức trước khi ghi nhận khoản chi và cập nhật số thực tế tự động.
- Hóa đơn đầu ra/đầu vào, phải thu/phải trả, ngày đến hạn, thanh toán từng phần, cảnh báo quá hạn và kiểm soát không trả vượt tổng hóa đơn.
- Tạm ứng, phê duyệt, giải ngân, chứng từ hoàn ứng và đối soát bắt buộc giữa tiền đã chi với tiền hoàn lại.
- Chi phí dự án theo loại nhân sự, dịch vụ, phần mềm, vật tư hoặc chi phí khác; liên kết dữ liệu dự án và ngân sách.
- Hệ thống tài khoản quản trị, bút toán kép bắt buộc cân đối Nợ/Có, sổ cái tổng hợp và khóa/mở kỳ kế toán bằng quyền riêng.
- Báo cáo quản trị theo tháng gồm doanh thu, chi phí, lợi nhuận, công nợ, ngân sách khả dụng và sổ cái.
- Vai trò `FINANCE_MANAGER`, quyền đọc/ghi/phê duyệt tách biệt và phạm vi `DEPARTMENT`, `PROJECT`, `COMPANY`; quyền khóa kỳ không được cấp mặc định.
- Giao diện tài chính responsive tại `/finance`, migration PostgreSQL V11 và smoke test xuyên suốt ngân sách → chi phí → hóa đơn → tạm ứng → bút toán → khóa kỳ.
- Phạm vi hiện tại là tài chính quản trị nội bộ; chưa thay thế kế toán thuế, hóa đơn điện tử hoặc báo cáo pháp định.

## Phase 6 — Operations, Inventory & Assets (13/11/2026)

- Hồ sơ nhà cung cấp mở rộng với liên hệ, mã số thuế, điều khoản thanh toán, lead time, ngân hàng, phân nhóm và đánh giá chất lượng/giao hàng/chi phí/dịch vụ.
- Yêu cầu mua theo phòng ban, dự án và ngân sách; luồng nháp → gửi duyệt → phê duyệt/từ chối, giữ ngân sách khi duyệt và tách quyền `procurement.approve`.
- Đơn mua theo nhà cung cấp, kho nhận và dòng hàng chuẩn hóa; luồng gửi duyệt, phê duyệt, nhận từng phần hoặc nhận đủ.
- Phiếu nhận hàng tự cập nhật số đã nhận, tồn kho, luân chuyển kho và chuyển ngân sách đã giữ thành chi phí thực tế.
- Danh mục vật tư dùng chung, kho, tồn khả dụng, mức tồn tối thiểu, nhập/xuất/chuyển/điều chỉnh và chặn tồn âm.
- Kiểm kê lưu số hệ thống và số thực tế; chỉ tạo điều chỉnh sau khi người có quyền `inventory.count.approve` phê duyệt, đồng thời từ chối nếu tồn đã thay đổi sau lúc đếm.
- Tài sản công ty theo mã, serial, nguyên giá, bảo hành và tình trạng; bàn giao/thu hồi theo nhân viên, phòng ban và lưu lịch sử.
- Bảo trì/sửa chữa liên kết tài sản và nhà cung cấp, theo dõi lịch, chi phí, trạng thái và kết quả; hoàn tất tự đưa tài sản về trạng thái sử dụng phù hợp.
- Giấy phép phần mềm theo số ghế, chủ sở hữu, chi phí và ngày gia hạn; chỉ lưu tham chiếu credential, không lưu secret trực tiếp.
- Nền tảng lệnh sản xuất ghi nhận thành phẩm hoàn thành vào kho; vận chuyển hỗ trợ chuyến nhập, xuất, chuyển nội bộ, tracking và ngoại lệ.
- Vai trò `OPERATIONS_MANAGER`, quyền đọc/ghi/phê duyệt tách biệt, phạm vi dữ liệu cho yêu cầu mua và tài sản; giao diện responsive tại `/operations` và migration PostgreSQL V12.
- Smoke test bao phủ yêu cầu mua → duyệt ngân sách → đơn mua → nhận hàng → kiểm kê → tài sản → bảo trì → sản xuất → vận chuyển.

## Phase 7 — Documents, Knowledge & Internal Workflows (13/11/2026)

- Quản lý thư mục và tài liệu theo phạm vi cá nhân, phòng ban, dự án hoặc toàn công ty; lưu nhiều phiên bản, checksum SHA-256, trạng thái quét malware và quyền chia sẻ trực tiếp.
- Kho tri thức theo không gian công ty/phòng ban/dự án, bài viết nháp và quy trình xuất bản.
- Trung tâm phê duyệt dùng chung với nhiều bước tuần tự, người duyệt cụ thể, bình luận và quyền quyết định `approval.decide` tách riêng.
- Trình tạo biểu mẫu lưu schema JSON, kiểm tra trường bắt buộc, tiếp nhận phản hồi và tùy chọn tự tạo yêu cầu phê duyệt.
- Xác nhận điện tử nội bộ theo thứ tự người nhận, thời hạn, quyết định ký/từ chối và mã bằng chứng SHA-256. Đây không phải chữ ký số pháp lý.
- Helpdesk nội bộ cho IT, quyền truy cập, thiết bị, HR, hành chính và cơ sở vật chất; hỗ trợ mức ưu tiên, phân công, SLA, bình luận công khai và ghi chú nội bộ.
- Kênh trao đổi theo công ty/phòng ban/dự án và tin nhắn có luồng trả lời.
- Hồ sơ pháp lý, hợp đồng, giấy phép, thời hạn, tài liệu liên kết và nghĩa vụ cần theo dõi/hoàn tất.
- Vai trò `WORKPLACE_MANAGER`, quyền đọc/ghi/quản trị tách biệt và kiểm tra phạm vi ở cấp từng tài liệu, phê duyệt, chữ ký, ticket và kênh.
- Giao diện responsive tại `/workplace`, migration PostgreSQL V13, kiểm thử phân quyền và smoke test xuyên suốt các luồng chính.

## Phase 8 — Shared Productivity, Search & Notifications (19/11/2026)

- Tìm kiếm toàn hệ thống theo tên, mã hoặc nội dung trên công việc, khách hàng, tài liệu, kho tri thức và yêu cầu phê duyệt.
- Kết quả tìm kiếm được kiểm tra lại permission và phạm vi từng đối tượng; không trả dữ liệu chỉ vì người dùng có quyền mở phân hệ.
- Hộp việc cần xử lý hợp nhất các bước phê duyệt, xác nhận tài liệu, ticket helpdesk và task đang được giao.
- Trung tâm thông báo trong ứng dụng với mức độ, đường dẫn hành động, đánh dấu đã đọc/chưa đọc, lưu trữ và chống tạo trùng bằng dedupe key.
- Người dùng có thể tự tạo nhắc việc; chỉ người có `notification.manage` mới được gửi thông báo cho người khác.
- Cấu hình nhận thông báo theo nhóm, kênh trong ứng dụng/email và khung giờ yên lặng để chuẩn bị cho worker gửi email ở giai đoạn tích hợp.
- Bộ lọc/cách xem lưu bằng JSON theo từng phân hệ, hỗ trợ mặc định cá nhân và chia sẻ khi có quyền riêng `saved-view.share`.
- Nhật ký audit có trang đọc riêng cho người có `audit.read`; người dùng phổ thông không thể xem lịch sử nhạy cảm.
- Ô tìm kiếm được đưa lên topbar và giao diện Trung tâm cá nhân responsive tại `/productivity`.
- Migration PostgreSQL V14, quyền Keycloak, kiểm thử bảo mật controller và smoke test dữ liệu productivity.

## Phase 9 — Automation, Scheduling & Integration Hub (19/11/2026)

- Trình tạo workflow gồm nhiều bước `NOTIFICATION`, `APPROVAL` hoặc `WEBHOOK`, lưu cấu hình JSON có kiểm tra và lịch sử phiên bản cơ bản.
- Hỗ trợ kích hoạt thủ công, lịch lặp bằng `intervalMinutes` và sự kiện có `eventType`; scheduler dùng khóa hàng để nhiều instance không chạy trùng workflow đến hạn.
- Mỗi lần chạy và từng bước đều có trạng thái, đầu vào/đầu ra, thời gian, người khởi chạy và lỗi để truy vết.
- Bước thông báo gửi vào trung tâm thông báo Phase 8; bước phê duyệt tạo và gửi yêu cầu vào trung tâm phê duyệt Phase 7.
- Trung tâm kết nối chỉ lưu `credential_reference` tới secret/vault, không lưu khóa API hoặc mật khẩu trực tiếp.
- Webhook gửi đi bắt buộc HTTPS, chặn địa chỉ local/private, có hàng đợi bền vững, số lần thử và dead-letter. Worker gửi ra Internet mặc định chưa bật để tránh egress dữ liệu khi chưa có xác thực đích và chữ ký HMAC.
- Phân quyền tách `automation.access/read/write/run`, `integration.read/write`, `webhook.read/write`; vai trò `AUTOMATION_MANAGER` không tự được cấp cho thành viên.
- Giao diện responsive tại `/automation`, migration PostgreSQL V15, quyền Keycloak, kiểm thử bảo mật controller và smoke test workflow nội bộ.
- AI/LLM không thuộc Phase 9; sẽ được thiết kế ở giai đoạn riêng sau khi chốt quyền dữ liệu, nhà cung cấp và chi phí.

## Phase 10 — Executive Reporting & Strategic Goals (19/11/2026)

- Dashboard điều hành thay đổi theo quyền và phạm vi `COMPANY`, `DEPARTMENT`, `BRANCH`, `PROJECT`, `TEAM` hoặc `SELF`; không trả dữ liệu cấp công ty chỉ vì người dùng có quyền mở trang.
- KPI trực tiếp từ nhân sự, dự án/công việc, CRM, tài chính, phê duyệt và tồn kho: nhân sự hoạt động, dự án đang chạy, task quá hạn, thu/chi/lợi nhuận, pipeline có trọng số, công nợ quá hạn, việc chờ duyệt và cảnh báo tồn.
- Tiền tệ chỉ được tổng hợp theo `base_currency` của công ty; dashboard tài chính mặc định tính từ đầu năm tới ngày hiện tại, tránh cộng sai nhiều đồng tiền.
- Biểu đồ xu hướng thu/chi và task hoàn tất trong sáu tháng; bảng sức khỏe dự án ưu tiên rủi ro và công việc quá hạn.
- Mục tiêu chiến lược theo công ty/phòng ban/dự án, có người sở hữu, thời hạn, trạng thái và nhiều Key Result có trọng số.
- Tiến độ mục tiêu được tính lại từ baseline, current và target của từng Key Result, hỗ trợ mục tiêu tăng hoặc giảm.
- Snapshot báo cáo lưu JSON bất biến theo khoảng ngày để đối chiếu; chỉ người có phạm vi toàn công ty và `reporting.manage` mới được chốt snapshot.
- Xuất CSV có quyền riêng `reporting.export`; quyền xem báo cáo không tự cho phép tải dữ liệu ra khỏi hệ thống.
- Vai trò `EXECUTIVE_MANAGER`, quyền `reporting.*` và `strategy.*`, giao diện responsive tại `/reporting`, migration PostgreSQL V16 và kiểm thử phân quyền controller.
- AI giải thích xu hướng chưa được bật; Phase 10 chỉ cung cấp dữ liệu quản trị có nguồn gốc và kiểm tra quyền rõ ràng.

## Lịch sử hệ thống cũ — 20/10/2026

Phần từ Phase 1 trở xuống mô tả Shopify Company ERP V1.0 trước khi chuyển sang Company Management System. Nội dung này chỉ được giữ để đối chiếu migration, không phải trạng thái hoàn thành hay lộ trình hiện tại.

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

- Shopify: client ID/secret, token encryption key, callback URL và webhook URL HTTPS công khai; app scopes tối thiểu gồm `write_products,read_orders,read_all_orders,read_inventory,read_locations,read_customers`, và protected customer data phải được Shopify cấp. Store đã cấp bộ scope cũ cần reconnect.
- Marketing: account/advertiser ID và OAuth/access credential thật cho từng nền tảng; Meta API version đặt trong environment.
- Production: PostgreSQL/Redis managed, TLS, secret manager, backup/restore, log/metrics/alerting và quy trình rotate credential.

Không thể xác nhận end-to-end với Shopify hoặc tài khoản quảng cáo thật khi chưa có credential và quyền truy cập tương ứng. Phần code, schema, API, worker và UI cho các kết nối này đã hoàn tất và không hardcode secret.

## Nhóm B — Hoàn thiện Quản lý nhân sự (HRM)

Bảy phân hệ HR được hoàn thiện trên nền backend modular monolith hiện có (Spring Boot, JDBC, PostgreSQL, Keycloak) và frontend React.

- Hồ sơ nhân viên: tạo, cập nhật hồ sơ cơ bản, cập nhật hồ sơ riêng tư, hồ sơ đính kèm với phân loại và trạng thái quét malware.
- Tuyển dụng: vị trí, ứng viên, chuyển giai đoạn pipeline, đặt lịch phỏng vấn, chấm điểm và khuyến nghị, thư mời nhận việc và ghi nhận phản hồi.
- Onboarding: hồ sơ checklist, thêm hạng mục, đánh dấu hoàn thành, tiến độ tính tự động theo tỉ lệ hoàn thành.
- Chấm công: ca làm, lịch sử theo khoảng ngày, đề xuất chỉnh sửa và quy trình phê duyệt riêng.
- Nghỉ phép: đăng ký, số dư còn lại, phê duyệt đơn và lịch nghỉ toàn công ty theo khoảng ngày.
- Lương & phúc lợi: cấu trúc lương mã hóa, tạo và duyệt kỳ lương, xuất phiếu lương kèm cấu phần, lịch sử điều chỉnh.
- Đánh giá nhân viên: kỳ đánh giá, KPI/OKR, bảng đánh giá định kỳ và 360°, phản hồi, kế hoạch phát triển.
- Đào tạo: khóa học, giao khóa, tiến độ và điểm bài kiểm tra, chứng chỉ, kỹ năng.
- Offboarding: đơn nghỉ việc, checklist bàn giao/thu hồi tài sản/tài khoản/nghĩa vụ, hoàn tất hồ sơ và lưu trữ.

Ranh giới quyền được giữ nguyên: lương, định danh và hồ sơ cá nhân dùng quyền riêng (`hr.payroll.read.all`, `hr.employee.read.sensitive`), không mặc định cho trưởng phòng. Phiếu lương chỉ được xem bởi người có quyền toàn công ty hoặc chính nhân viên đó (`hr.payroll.read.own` kèm khớp Keycloak subject).
