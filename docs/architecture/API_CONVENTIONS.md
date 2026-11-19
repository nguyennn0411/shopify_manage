# API conventions

## Resource and versioning

- Public business API dùng prefix `/api/v1`.
- URL dùng danh từ số nhiều và kebab-case.
- ID là UUID, không lộ sequence nội bộ.
- Breaking change tạo version mới; không đổi âm thầm response đang được frontend sử dụng.

## Company scope

- API theo công ty hiện dùng query parameter bắt buộc `companyId`.
- Không đồng thời nhận company scope từ body và query parameter.
- Controller phải có method authorization gọi `companyAccess` trước khi service truy vấn dữ liệu.
- Backend không tin company đang chọn ở frontend.

## Request tracing

- Client có thể gửi `X-Request-Id` gồm 8–100 ký tự an toàn.
- Backend thay request ID không hợp lệ, trả `X-Request-Id` trong mọi response và đưa cùng ID vào log MDC.
- Khi báo lỗi vận hành phải kèm request ID, không gửi stack trace cho client.

## Errors

Error response dùng `application/problem+json` với `title`, `status`, `detail`, field lỗi khi có và `instance` cho lỗi không dự kiến. Mã chuẩn: `400` validation, `401` chưa đăng nhập, `403` thiếu quyền/scope, `404` không thấy trong scope, `409` xung đột, `422` state transition không hợp lệ, `429` rate limit, `500` lỗi không dự kiến.

## List APIs

Module mới phải hỗ trợ server-side pagination ngay từ đầu:

```text
GET /api/v1/resources?companyId=<uuid>&page=0&size=50&sort=createdAt,desc&status=ACTIVE&q=keyword
```

- `page` bắt đầu từ 0.
- `size` mặc định 50, tối đa 200.
- Sort field phải nằm trong allowlist, không nối trực tiếp input vào SQL.
- Response chuẩn gồm `items`, `page`, `size`, `totalItems`, `totalPages`.

## Data formats and writes

- Timestamp dùng ISO-8601 UTC; local date dùng `YYYY-MM-DD`.
- Tiền là decimal + ISO-4217 currency; không dùng floating point.
- Field chưa có giá trị trả `null`; không dùng chuỗi rỗng thay cho null trong API.
- `POST` tạo mới, `PATCH` cập nhật một phần, `PUT` thay thế toàn bộ khi thực sự cần.
- Không hard delete dữ liệu tài chính, nhân sự hoặc audit qua API thường.
- Action nghiệp vụ có endpoint rõ như `/requests/{id}/decision`, không cho client sửa trực tiếp `status`.
- Tác vụ tài chính/thanh toán về sau phải hỗ trợ `Idempotency-Key`.
