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
  id: string; employeeCode: string; fullName: string; workEmail?: string; branchId?: string; branchName?: string;
  departmentId?: string; departmentName?: string; positionId?: string; positionTitle?: string;
  jobTitle: string; managerEmployeeId?: string; joinDate: string; employmentStatus: string;
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
export type CrmSummary = {
  activeCustomers: number | null; openLeads: number | null; openOpportunities: number | null;
  weightedPipeline: number | string | null; pendingQuotes: number | null; expiringContracts: number | null;
  openOrders: number | null; openTickets: number | null; breachedSla: number | null;
};
export type FinanceSummary = {
  income: number | string | null; expenses: number | string | null; profit: number | string | null;
  receivables: number | string | null; payables: number | string | null; availableBudget: number | string | null;
  pendingTransactions: number | null; overdueInvoices: number | null; pendingAdvances: number | null;
};
export type OperationsSummary = {
  activeSuppliers: number; pendingPurchaseRequests: number; openPurchaseOrders: number; lowStockItems: number;
  assignedAssets: number; maintenanceDue: number; licensesDue: number; shipmentsInTransit: number;
};
export type WorkplaceSummary = {
  documents: number; publishedArticles: number; pendingApprovals: number; pendingSignatures: number;
  openTickets: number; activeChannels: number; expiringLegalRecords: number; dueObligations: number;
};
export type ProductivitySummary = {
  unreadNotifications: number; pendingApprovals: number; pendingSignatures: number;
  assignedTasks: number; openTickets: number; savedViews: number;
};
export type AutomationSummary = {
  activeWorkflows: number; runsToday: number; failedRuns: number;
  activeConnections: number; pendingDeliveries: number;
};
export type ExecutiveSummary = {
  currency: string; activeEmployees: number; activeProjects: number; overdueTasks: number;
  revenue: number | string; expenses: number | string; profit: number | string;
  openPipeline: number | string; overdueReceivables: number | string;
  pendingApprovals: number; lowStockItems: number; objectivesAtRisk: number;
};
export type ReportingTrend = { period: string; revenue: number | string; expenses: number | string; completedTasks: number };
export type SearchResult = { type: string; id: string; title: string; subtitle: string; path: string; status: string };
export type CompanyProfile = Company & { legalName?: string; brandName?: string; taxCode?: string;
  registrationNumber?: string; email?: string; phone?: string; website?: string; address?: string;
  logoObjectKey?: string; bankName?: string; bankAccountName?: string; bankAccountNumber?: string;
  timezone: string; status: string };
export type Branch = { id: string; code: string; name: string; address?: string; timezone: string;
  phone?: string; email?: string; managerEmployeeId?: string; managerName?: string; status: string };
export type Department = { id: string; code: string; name: string; functionType: string; branchId?: string;
  branchName?: string; parentDepartmentId?: string; parentDepartmentName?: string; managerEmployeeId?: string;
  managerName?: string; description?: string; status: string };
export type JobPosition = { id: string; code: string; title: string; departmentId?: string;
  departmentName?: string; levelName?: string; reportsToPositionId?: string; reportsToTitle?: string;
  description?: string; status: string };
export type Permission = { code: string; module: string; description: string; sensitive: boolean };
export type CompanyRole = { id: string; code: string; name: string; description?: string; systemRole: boolean;
  active: boolean; permissions: string[] };
export type ScopeGrant = { scopeType: 'SELF' | 'TEAM' | 'DEPARTMENT' | 'BRANCH' | 'PROJECT' | 'COMPANY'; scopeId: string };
export type CompanyMember = { keycloakSubject: string; email: string; displayName: string; status: string;
  roleIds: string[]; roleNames: string[]; scopes: ScopeGrant[] };
