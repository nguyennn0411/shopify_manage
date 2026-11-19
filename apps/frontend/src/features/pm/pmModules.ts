import { Bug, CalendarClock, ClipboardList, GitBranch, ListChecks, Receipt, Target } from 'lucide-react';

export const pmModules = [
  { key: 'projects', summaryKey: 'openProjects', label: 'Quản trị dự án', description: 'Danh sách dự án, thành viên, mục tiêu, ngân sách và mốc quan trọng.', permission: 'pm.project.read', icon: Target },
  { key: 'tasks', summaryKey: 'openTasks', label: 'Công việc & Task', description: 'Kanban, danh sách, lịch, Gantt; người phụ trách, hạn chót và ưu tiên.', permission: 'pm.task.read', icon: ListChecks },
  { key: 'sprints', summaryKey: 'activeSprints', label: 'Sprint & Quy trình', description: 'Sprint, backlog, trạng thái tùy chỉnh và phụ thuộc công việc.', permission: 'pm.sprint.read', icon: GitBranch },
  { key: 'timesheets', summaryKey: 'pendingTimesheets', label: 'Timesheet', description: 'Ghi thời gian theo task, so sánh thực tế với dự kiến và phê duyệt.', permission: 'pm.timesheet.read', alternatePermission: 'pm.timesheet.read.own', icon: CalendarClock },
  { key: 'workload', summaryKey: null, label: 'Khối lượng công việc', description: 'Tải lượng từng người, lịch phân công và xung đột thời gian.', permission: 'pm.workload.read', icon: ClipboardList },
  { key: 'reports', summaryKey: null, label: 'Báo cáo dự án', description: 'Tiến độ, thời gian, chi phí, công việc tồn đọng và lợi nhuận.', permission: 'pm.report.read', icon: Receipt },
  { key: 'issues', summaryKey: 'openIssues', label: 'Quản lý sự cố', description: 'Lỗi, vấn đề, blocker, yêu cầu thay đổi và lịch sử khắc phục.', permission: 'pm.issue.read', icon: Bug },
] as const;

export type PmModuleKey = typeof pmModules[number]['key'];
