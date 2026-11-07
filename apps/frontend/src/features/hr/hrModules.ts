import { BadgeCheck, BookOpenCheck, CalendarClock, ClipboardList, ContactRound, LogOut, ReceiptText, Star, UserPlus } from 'lucide-react';

export const hrModules = [
  { key: 'employees', summaryKey: 'employees', label: 'Hồ sơ nhân viên', description: 'Mã nhân viên, phòng ban, chức danh và hồ sơ cá nhân.', permission: 'hr.employee.read.basic', icon: ContactRound },
  { key: 'recruitment', summaryKey: 'openPositions', label: 'Tuyển dụng', description: 'Vị trí, ứng viên, phỏng vấn và thư mời nhận việc.', permission: 'hr.recruitment.read', icon: UserPlus },
  { key: 'onboarding', summaryKey: 'onboarding', label: 'Onboarding', description: 'Checklist, tài khoản, thiết bị, đào tạo và mentor.', permission: 'hr.onboarding.read', icon: ClipboardList },
  { key: 'attendance', summaryKey: 'attendanceToday', label: 'Chấm công', description: 'Ca làm, check-in/out, đi muộn, tăng ca và điều chỉnh.', permission: 'hr.attendance.read', icon: CalendarClock },
  { key: 'leave', summaryKey: 'pendingLeave', label: 'Nghỉ phép', description: 'Số dư phép, đơn nghỉ, phê duyệt và lịch nghỉ.', permission: 'hr.leave.read', icon: BadgeCheck },
  { key: 'payroll', summaryKey: 'draftPayrollRuns', label: 'Lương & phúc lợi', description: 'Cấu trúc lương, bảng lương, phiếu lương và điều chỉnh.', permission: 'hr.payroll.read.all', alternatePermission: 'hr.payroll.read.own', icon: ReceiptText },
  { key: 'performance', summaryKey: 'openReviews', label: 'Đánh giá nhân viên', description: 'KPI, OKR, 360°, phản hồi và kế hoạch phát triển.', permission: 'hr.performance.read', icon: Star },
  { key: 'training', summaryKey: 'trainingInProgress', label: 'Đào tạo nhân viên', description: 'Khóa học, bài kiểm tra, chứng chỉ và kỹ năng.', permission: 'hr.training.read', icon: BookOpenCheck },
  { key: 'offboarding', summaryKey: 'offboarding', label: 'Offboarding', description: 'Nghỉ việc, bàn giao, thu hồi tài sản và tài khoản.', permission: 'hr.offboarding.read', icon: LogOut },
] as const;

export type HrModuleKey = typeof hrModules[number]['key'];
