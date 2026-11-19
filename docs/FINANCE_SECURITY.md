# Mô hình quyền Tài chính & Kế toán quản trị

Frontend chỉ ẩn hoặc hiện chức năng theo permission. Backend luôn kiểm tra lại thành viên công ty, quyền hành động, phạm vi dữ liệu và trạng thái nghiệp vụ trước khi đọc hoặc ghi.

## Quyền nghiệp vụ

- Thu chi: `finance.transaction.read`, `finance.transaction.write`, `finance.transaction.approve`.
- Ngân sách: `finance.budget.read`, `finance.budget.write`.
- Hóa đơn và công nợ: `finance.invoice.read`, `finance.invoice.write`.
- Tạm ứng: `finance.advance.read`, `finance.advance.write`, `finance.advance.approve`.
- Chi phí dự án: `finance.project-cost.read`, `finance.project-cost.write`.
- Kế toán quản trị: `finance.accounting.read`, `finance.accounting.write`, `finance.period.close`.
- Báo cáo: `finance.report.read`.

Vai trò `FINANCE_MANAGER` có quyền lập, vận hành và phê duyệt nghiệp vụ tài chính nhưng không mặc định có `finance.period.close`. Khóa hoặc mở lại kỳ phải được cấp riêng cho người có thẩm quyền.

## Phạm vi dữ liệu

- `COMPANY`: toàn bộ dữ liệu tài chính trong công ty.
- `DEPARTMENT`: ngân sách, phiếu thu chi, hóa đơn và tạm ứng thuộc phòng ban được cấp.
- `PROJECT`: ngân sách, phiếu thu chi, hóa đơn, tạm ứng và chi phí thuộc dự án được cấp.
- Dữ liệu không gắn phòng ban hoặc dự án chỉ được xem bởi người có phạm vi toàn công ty.

## Toàn vẹn và kiểm toán

- Bản ghi liên kết phải thuộc cùng công ty; backend kiểm tra phòng ban, dự án, nhân viên, khách hàng, nhà cung cấp, đơn bán và hợp đồng trước khi ghi.
- Khoản chi ghi nhận không được vượt ngân sách đang hoạt động phù hợp với kỳ, tiền tệ và phạm vi.
- Thanh toán tích lũy không được vượt tổng hóa đơn.
- Tạm ứng chỉ hoàn tất khi tổng chứng từ và tiền hoàn lại đúng bằng số đã giải ngân.
- Mỗi bút toán phải có tổng Nợ bằng tổng Có và không được ghi vào kỳ đã khóa.
- Thao tác tạo, duyệt, thanh toán, ghi sổ và khóa kỳ đều ghi audit append-only.

## Giới hạn sản phẩm

Module này phục vụ tài chính quản trị nội bộ. Việc thay thế phần mềm kế toán pháp định cần thêm chuẩn tài khoản, thuế, hóa đơn điện tử, chữ ký số và quy định báo cáo tương ứng.
