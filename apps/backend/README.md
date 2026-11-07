# Backend modular monolith

Ứng dụng Spring Boot này là một JAR và một đơn vị deploy độc lập. Các package cấp cao là module domain trong cùng tiến trình, cùng transaction manager và cùng MySQL schema.

Quy tắc phụ thuộc:

- `platform` chứa cơ chế kỹ thuật dùng chung, không chứa nghiệp vụ ERP.
- `identity`, `company`, `dashboard` và `audit` sở hữu controller/service của chính domain.
- Controller không truy vấn database trực tiếp.
- Mọi endpoint dữ liệu phải có request validation, Method Security/company scope và audit phù hợp.
- Không gọi HTTP giữa các module. Giao tiếp nội bộ bằng Java API hoặc domain event trong cùng application.
