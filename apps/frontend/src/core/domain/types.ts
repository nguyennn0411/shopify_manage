// Cross-feature contracts returned by the backend monolith.
export type Company = { id: string; name: string; code: string; baseCurrency: string };
export type Me = { id: string; email: string; name: string; permissions: string[]; companies: Company[]; sessionExpiresAt: string };
export type ModuleCounts = Record<string, number>;
export type HrSummary = {
  employees: number | null; openPositions: number | null; activeCandidates: number | null; onboarding: number | null;
  attendanceToday: number | null; pendingLeave: number | null; draftPayrollRuns: number | null; openReviews: number | null;
  trainingInProgress: number | null; offboarding: number | null;
};
export type Employee = {
  id: string; employeeCode: string; fullName: string; workEmail?: string; departmentId?: string;
  departmentName?: string; jobTitle: string; managerEmployeeId?: string; joinDate: string; employmentStatus: string;
};
export type EmployeePrivateProfile = {
  dateOfBirth?: string; identityType?: string; identityNumber?: string; personalEmail?: string;
  personalPhone?: string; address?: string; emergencyName?: string; emergencyPhone?: string;
};
export type Payslip = {
  id: string; payrollRunId: string; employeeId: string; periodStart: string; periodEnd: string;
  currency: string; gross: string; deductions: string; net: string; payslipObjectKey?: string; publishedAt?: string;
};
export type PmSummary = {
  openProjects: number | null; openTasks: number | null; dueThisWeek: number | null; activeSprints: number | null;
  pendingTimesheets: number | null; openIssues: number | null; criticalIssues: number | null;
};
