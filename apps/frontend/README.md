# Frontend monolith

Ứng dụng React này là một SPA và một đơn vị deploy độc lập. `main.tsx` là composition root; route nằm trong `app`; hạ tầng dùng chung nằm trong `core`; mã nghiệp vụ nằm trong `features`; application shell nằm trong `layout`.

Quy tắc phụ thuộc:

- `core` không import `features` hoặc `layout`.
- Một feature không truy cập file nội bộ của feature khác, ngoại trừ public provider/component được chủ động export.
- API call đi qua `core/http/apiClient.ts` để thống nhất cookie, CSRF, request ID và xử lý `401`.
- Menu theo quyền chỉ phục vụ UX; backend luôn là nơi quyết định quyền thực tế.
