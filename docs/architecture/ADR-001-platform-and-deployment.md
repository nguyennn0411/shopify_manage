# ADR-001: Platform and deployment

Status: Accepted — Phase 0

## Decision

- Frontend chính là React + TypeScript + Vite trong `apps/frontend`.
- Backend chính là Spring Boot + Java 21 trong `apps/backend`.
- Backend là modular monolith. Ranh giới module nằm trong mã nguồn và database transaction, không tách microservice khi chưa có nhu cầu scale/ownership độc lập đã được đo lường.
- PostgreSQL là system of record. Bảng nghiệp vụ nằm trong schema `erp_v2`; Keycloak quản lý bảng định danh riêng.
- Redis chỉ lưu session, rate-limit state và dữ liệu tạm; không phải system of record.
- Keycloak cung cấp OIDC, MFA và identity lifecycle. Backend luôn là nơi quyết định authorization cuối cùng.
- Local/self-hosted dùng Docker Compose và Nginx. Production hiện dùng Vercel Services với frontend, backend và Keycloak là ba container.

## Why

Đội đã có code vận hành trên stack này. Chuyển sang Next.js/NestJS không tạo thêm giá trị nghiệp vụ nhưng làm tăng rủi ro migration, phân tán transaction và kéo dài lộ trình.

## Consequences

- Module mới phải tuân theo ranh giới trong `DOMAIN_BOUNDARIES.md`.
- Không truy cập database của module khác bằng query tùy tiện; dùng application service hoặc read model được công bố.
- Không đưa queue hay microservice vào chỉ để “chuẩn hóa kiến trúc”. Chỉ tách khi có số liệu về tải, fault isolation hoặc ownership.
- Các ứng dụng legacy trong `apps/api` và `apps/web` không phải production entrypoint.
