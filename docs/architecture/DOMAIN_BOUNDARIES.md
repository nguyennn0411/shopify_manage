# Domain boundaries

## Modules hiện tại

| Module | Ownership | Được phép phụ thuộc |
|---|---|---|
| `platform` | HTTP, security, observability, shared technical code | Không phụ thuộc domain nghiệp vụ |
| `identity` | OIDC identity, user projection, session view | `platform`, `company` read contract |
| `company` | Company membership và company-scope authorization | `platform` |
| `audit` | Append-only security/business audit | `platform` |
| `dashboard` | Read model tổng hợp | Public read contract của các domain |
| `hr` | Vòng đời nhân viên và dữ liệu HR | `company`, `audit`, `platform` |
| `pm` | Project, task, sprint, timesheet, workload, issue | `company`, `audit`, `platform` |
| `crm` | Customer, lead, opportunity, quote, contract, sales order, support ticket, campaign | `company`, `audit`, `platform` |
| `finance` | Cash transaction, budget, invoice/debt, advance, project cost, journal, period và management report | `company`, `audit`, read contract từ `hr`, `pm`, `crm` |
| `operations` | Supplier, procurement, warehouse, stock, asset, maintenance, license, manufacturing và shipment | `company`, `audit`, read contract từ `hr`, `pm`, `crm`, `finance` |
| `workplace` | Document, knowledge base, approval, form, internal signature, helpdesk, communication và legal record | `company`, `audit`, read contract từ `organization`, `hr`, `pm` |
| `productivity` | Permission-aware search, action inbox, notification, saved view và audit read model | `company`, `audit`, public access contract từ `pm`, `crm`, `workplace` |
| `automation` | Workflow, scheduler, run history, connection catalog và webhook delivery queue | `company`, `audit`, public application service của `workplace`; ghi notification qua contract nền tảng hiện có |
| `reporting` | Executive KPI, scoped trends, strategic objective/key result và immutable report snapshot | `company`, `audit`, read contract từ `hr`, `pm`, `crm`, `finance`, `operations`, `workplace` |
| `organization` | Company profile, branch, department, position, custom role và data scope | `company`, `identity`, `audit`, `platform` |

## Modules dự kiến

Khi quy mô vận hành tăng, connector và webhook dispatcher có thể tách khỏi `automation`; KPI read model có thể tách khỏi `reporting`; notification có thể tách khỏi `productivity`; `workplace` có thể tách dần document, approval và communication; `operations` có thể tách procurement, inventory, asset và fulfillment theo các transaction boundary đã có.

## Rules

1. Controller chỉ parse/validate request và gọi application service của cùng module.
2. Transaction nghiệp vụ bắt đầu tại application service, không bắt đầu trong controller.
3. Mọi query dữ liệu công ty phải có `company_id` và kiểm tra membership + permission trước khi truy vấn.
4. HR không gọi trực tiếp repository của PM và ngược lại.
5. Giao tiếp chéo module dùng application service công khai. Sự kiện bất đồng bộ sẽ dùng outbox khi notification/job platform được triển khai.
6. Không đặt entity nghiệp vụ trong `platform` hoặc một package `common` chung chung.
7. Audit chỉ append; code nghiệp vụ không update/delete audit record.
8. ArchUnit kiểm tra dependency cycle giữa các package cấp cao trong CI.

## Package shape cho module mới

```text
module/
├── api/              controller + request/response contract
├── application/      use case + transaction boundary
├── domain/           rule, value object, state transition
└── infrastructure/   JDBC, integration adapter
```

Code hiện tại chưa được di chuyển cơ học sang cấu trúc này để tránh một refactor lớn không tạo giá trị. Module mới phải áp dụng ngay; module cũ được chuyển dần khi có thay đổi nghiệp vụ.
