import { lazy, Suspense, type ReactNode } from 'react';
import { createBrowserRouter } from 'react-router-dom';
import { AppLayout } from '../layout/AppLayout';
import { ProtectedRoute } from '../features/auth/ProtectedRoute';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { LoginPage } from '../features/auth/LoginPage';
import { ModulePage } from '../features/modules/ModulePage';

const HrHubPage = lazy(() => import('../features/hr/HrHubPage').then(module => ({ default: module.HrHubPage })));
const EmployeesPage = lazy(() => import('../features/hr/EmployeesPage').then(module => ({ default: module.EmployeesPage })));
const RecruitmentPage = lazy(() => import('../features/hr/RecruitmentPage').then(module => ({ default: module.RecruitmentPage })));
const OnboardingPage = lazy(() => import('../features/hr/OnboardingPage').then(module => ({ default: module.OnboardingPage })));
const AttendancePage = lazy(() => import('../features/hr/AttendancePage').then(module => ({ default: module.AttendancePage })));
const LeavePage = lazy(() => import('../features/hr/LeavePage').then(module => ({ default: module.LeavePage })));
const PerformancePage = lazy(() => import('../features/hr/PerformancePage').then(module => ({ default: module.PerformancePage })));
const TrainingPage = lazy(() => import('../features/hr/TrainingPage').then(module => ({ default: module.TrainingPage })));
const OffboardingPage = lazy(() => import('../features/hr/OffboardingPage').then(module => ({ default: module.OffboardingPage })));
const PayrollPage = lazy(() => import('../features/hr/PayrollPage').then(module => ({ default: module.PayrollPage })));
const PmHubPage = lazy(() => import('../features/pm/PmHubPage').then(module => ({ default: module.PmHubPage })));
const TasksPage = lazy(() => import('../features/pm/TasksPage').then(module => ({ default: module.TasksPage })));
const ProjectsPage = lazy(() => import('../features/pm/ProjectsPage').then(module => ({ default: module.ProjectsPage })));
const SprintsPage = lazy(() => import('../features/pm/SprintsPage').then(module => ({ default: module.SprintsPage })));
const TimesheetsPage = lazy(() => import('../features/pm/TimesheetsPage').then(module => ({ default: module.TimesheetsPage })));
const WorkloadPage = lazy(() => import('../features/pm/WorkloadPage').then(module => ({ default: module.WorkloadPage })));
const ReportsPage = lazy(() => import('../features/pm/ReportsPage').then(module => ({ default: module.ReportsPage })));
const IssuesPage = lazy(() => import('../features/pm/IssuesPage').then(module => ({ default: module.IssuesPage })));
const lazyPage = (page: ReactNode) => <Suspense fallback={<main className="center-screen">Đang tải phân hệ…</main>}>{page}</Suspense>;

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { element: <ProtectedRoute />, children: [{ element: <AppLayout />, children: [
    { index: true, element: <DashboardPage /> },
    { path: 'hr', element: lazyPage(<HrHubPage />) },
    { path: 'hr/employees', element: lazyPage(<EmployeesPage />) },
    { path: 'hr/recruitment', element: lazyPage(<RecruitmentPage />) },
    { path: 'hr/onboarding', element: lazyPage(<OnboardingPage />) },
    { path: 'hr/attendance', element: lazyPage(<AttendancePage />) },
    { path: 'hr/leave', element: lazyPage(<LeavePage />) },
    { path: 'hr/payroll', element: lazyPage(<PayrollPage />) },
    { path: 'hr/performance', element: lazyPage(<PerformancePage />) },
    { path: 'hr/training', element: lazyPage(<TrainingPage />) },
    { path: 'hr/offboarding', element: lazyPage(<OffboardingPage />) },
    { path: 'pm', element: lazyPage(<PmHubPage />) },
    { path: 'pm/tasks', element: lazyPage(<TasksPage />) },
    { path: 'pm/projects', element: lazyPage(<ProjectsPage />) },
    { path: 'pm/sprints', element: lazyPage(<SprintsPage />) },
    { path: 'pm/timesheets', element: lazyPage(<TimesheetsPage />) },
    { path: 'pm/workload', element: lazyPage(<WorkloadPage />) },
    { path: 'pm/reports', element: lazyPage(<ReportsPage />) },
    { path: 'pm/issues', element: lazyPage(<IssuesPage />) },
    ...['stores', 'orders', 'inventory', 'suppliers', 'procurement', 'pim', 'fulfillment', 'analytics', 'security', 'settings'].map(module => ({ path: module, element: <ModulePage module={module} /> })),
  ] }] },
]);
