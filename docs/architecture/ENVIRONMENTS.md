# Environment model

| Environment | Spring profile | Data | Purpose |
|---|---|---|---|
| Local | `dev` | Docker PostgreSQL/Redis, seed tùy chọn | Phát triển cá nhân |
| CI | profile mặc định | PostgreSQL Testcontainers | Unit, architecture, migration test |
| Staging | `staging` | Managed database riêng | UAT, smoke, migration rehearsal |
| Production | `production` | Managed production services | Người dùng thật |

## Rules

- Không dùng chung database, Redis hoặc Keycloak realm giữa staging và production.
- Secret chỉ nằm trong secret manager/environment variables, không commit.
- `DEV_SEED_ENABLED` chỉ có hiệu lực với profile `dev`.
- Production và staging bắt buộc secure session cookie và Flyway clean bị vô hiệu hóa.
- Migration phải chạy thành công trên database rỗng trong CI trước khi deploy.
- Production deploy cần smoke test cho frontend, auth, CSRF, Keycloak discovery và API health.

## Local seed

1. Tạo user trong Keycloak realm `company-erp`.
2. Lấy `sub` của user và đặt `DEV_SEED_KEYCLOAK_SUBJECT`.
3. Đặt `DEV_SEED_ENABLED=true` trong `.env`.
4. Chạy `docker compose up --build`.

Seed chạy idempotent, tạo company demo, membership, ba phòng ban và dữ liệu tham chiếu HR/PM. Seed không chạy ở staging/production.

## Promotion

```text
feature branch → pull request/CI → staging deploy → migration rehearsal
→ UAT/smoke/security checks → production deploy → post-deploy smoke
```

Rollback ứng dụng dùng deployment trước. Rollback dữ liệu dùng forward-fix hoặc restore theo runbook đã diễn tập, không chạy Flyway clean/undo tùy tiện.
