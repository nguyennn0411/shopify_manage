import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  DndContext, PointerSensor, TouchSensor, closestCorners, useSensor, useSensors, type DragEndEvent,
} from '@dnd-kit/core';
import { SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { CalendarDays, ChevronLeft, ChevronRight, GanttChart, KanbanSquare, List, Plus, Table2 } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';
import {
  buildMonthGrid, isOverdue, MONTH_LABELS, WEEKDAY_LABELS, addDays,
  barPosition, weekOffsets, type DateCell,
} from './pmDates';
import {
  emptyTaskFilters, usePmEmployees, usePmProjects, usePmSprints, usePmTasks, usePmWorkflowStatuses,
  type PmRow, type TaskFilters,
} from './usePmQueries';

type ViewMode = 'kanban' | 'list' | 'table' | 'calendar' | 'gantt';
type Created = { id: string; status: string };

const taskSchema = z.object({
  title: z.string().trim().min(1, 'Tiêu đề là bắt buộc').max(255),
  projectId: z.string().optional(),
  sprintId: z.string().optional(),
  assigneeEmployeeId: z.string().optional(),
  supervisorEmployeeId: z.string().optional(),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']),
  estimatedHours: z.number().min(0).optional(),
  startsOn: z.string().optional(),
  dueDate: z.string().optional(),
  description: z.string().trim().max(4000).optional(),
});
type TaskForm = z.infer<typeof taskSchema>;

const PRIORITY_LABELS: Record<string, string> = { LOW: 'Thấp', MEDIUM: 'Trung bình', HIGH: 'Cao', CRITICAL: 'Khẩn cấp' };

function text(row: PmRow, key: string): string {
  const value = row[key];
  return value === null || value === undefined ? '' : String(value);
}

function num(row: PmRow, key: string): number {
  const value = row[key];
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function TasksPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth();
  const queryClient = useQueryClient();
  const [view, setView] = useState<ViewMode>('kanban');
  const [filters, setFilters] = useState<TaskFilters>(emptyTaskFilters);
  const [selectedId, setSelectedId] = useState('');
  const [monthOffset, setMonthOffset] = useState(0);

  const tasks = usePmTasks(companyId, filters);
  const projects = usePmProjects(companyId);
  const statuses = usePmWorkflowStatuses(companyId, 'TASK');
  const sprints = usePmSprints(companyId, filters.projectId || undefined);
  const employees = usePmEmployees(companyId);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 180, tolerance: 8 } }),
  );

  const move = useMutation({ mutationFn: async (input: { taskId: string; statusId: string }) =>
      (await api.post<Created>(`/pm/tasks/${input.taskId}/move`, { statusId: input.statusId }, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pm-tasks', companyId] }) });

  const create = useMutation({ mutationFn: async (values: TaskForm) =>
      (await api.post<Created>('/pm/tasks', {
        title: values.title, description: values.description || undefined,
        projectId: values.projectId || undefined, sprintId: values.sprintId || undefined,
        assigneeEmployeeId: values.assigneeEmployeeId || undefined,
        supervisorEmployeeId: values.supervisorEmployeeId || undefined,
        priority: values.priority, estimatedHours: values.estimatedHours || undefined,
        startsOn: values.startsOn || undefined, dueDate: values.dueDate || undefined,
      }, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pm-tasks', companyId] }) });

  const form = useForm<TaskForm>({ resolver: zodResolver(taskSchema),
    defaultValues: { title: '', projectId: '', sprintId: '', assigneeEmployeeId: '', supervisorEmployeeId: '',
      priority: 'MEDIUM', estimatedHours: undefined, startsOn: '', dueDate: '', description: '' } });

  const taskRows = useMemo(() => tasks.data ?? [], [tasks.data]);
  const statusRows = useMemo(() => statuses.data ?? [], [statuses.data]);
  const selected = useMemo(() => taskRows.find(row => text(row, 'id') === selectedId) ?? null, [taskRows, selectedId]);

  const onDragEnd = (event: DragEndEvent) => {
    const target = event.over?.id;
    if (!target) return;
    const taskId = String(event.active.id);
    const statusId = String(target);
    const row = taskRows.find(item => text(item, 'id') === taskId);
    if (!row || text(row, 'status_id') === statusId) return;
    move.mutate({ taskId, statusId });
  };

  const [today] = useState(() => new Date());
  const calendarBase = useMemo(() => new Date(today.getFullYear(), today.getMonth() + monthOffset, 1), [today, monthOffset]);
  const grid = useMemo(() => buildMonthGrid(calendarBase.getFullYear(), calendarBase.getMonth()), [calendarBase]);
  const ganttRange = useMemo(() => {
    const start = addDays(today, -14);
    const end = addDays(today, 60);
    return { start, end, weeks: weekOffsets(start, 11) };
  }, [today]);
  const ganttTodayOffset = useMemo(() => {
    const total = Math.max(1, Math.round((ganttRange.end.getTime() - ganttRange.start.getTime()) / 86_400_000));
    return (Math.round((today.getTime() - ganttRange.start.getTime()) / 86_400_000) / total) * 100;
  }, [ganttRange, today]);

  const views: { key: ViewMode; label: string; icon: typeof KanbanSquare }[] = [
    { key: 'kanban', label: 'Kanban', icon: KanbanSquare },
    { key: 'list', label: 'Danh sách', icon: List },
    { key: 'table', label: 'Bảng', icon: Table2 },
    { key: 'calendar', label: 'Lịch', icon: CalendarDays },
    { key: 'gantt', label: 'Gantt', icon: GanttChart },
  ];

  if (!companyId) return <section className="empty-state"><h1>Chọn công ty</h1><p>Chọn workspace để xem công việc.</p></section>;

  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'PM'} / tasks</span><h1>Công việc</h1><p>Mã, tiêu đề, dự án, phòng ban, người phụ trách, hạn chót, ưu tiên và trạng thái.</p></div>

    <section className="panel"><div className="button-row">
      <label>Dự án<select value={filters.projectId} onChange={event => setFilters(value => ({ ...value, projectId: event.target.value, sprintId: '' }))}>
        <option value="">Tất cả dự án</option>{projects.data?.map(project => <option key={text(project, 'id')} value={text(project, 'id')}>{text(project, 'code')} · {text(project, 'name')}</option>)}</select></label>
      <label>Sprint<select value={filters.sprintId} onChange={event => setFilters(value => ({ ...value, sprintId: event.target.value }))}>
        <option value="">Tất cả sprint</option>{sprints.data?.map(sprint => <option key={text(sprint, 'id')} value={text(sprint, 'id')}>{text(sprint, 'name')}</option>)}</select></label>
      <label>Người phụ trách<select value={filters.assigneeId} onChange={event => setFilters(value => ({ ...value, assigneeId: event.target.value }))}>
        <option value="">Tất cả</option>{employees.data?.map(employee => <option key={text(employee, 'id')} value={text(employee, 'id')}>{text(employee, 'fullName')}</option>)}</select></label>
      <label>Ưu tiên<select value={filters.priority} onChange={event => setFilters(value => ({ ...value, priority: event.target.value }))}>
        <option value="">Tất cả</option>{Object.entries(PRIORITY_LABELS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
      <label>Từ ngày<input type="date" value={filters.from} onChange={event => setFilters(value => ({ ...value, from: event.target.value }))} /></label>
      <label>Đến ngày<input type="date" value={filters.to} onChange={event => setFilters(value => ({ ...value, to: event.target.value }))} /></label>
      <button className="secondary" onClick={() => setFilters(emptyTaskFilters)}>Xoá lọc</button>
    </div></section>

    <div className="view-switch">{views.map(item => { const Icon = item.icon; return <button key={item.key} className={`secondary ${view === item.key ? 'active' : ''}`} onClick={() => setView(item.key)}><Icon size={16} /> {item.label}</button>; })}</div>

    {view === 'kanban' && <DndContext sensors={sensors} collisionDetection={closestCorners} onDragEnd={onDragEnd}>
      <section className="kanban-board">
        {statusRows.map(status => {
          const statusId = text(status, 'id');
          const columnTasks = taskRows.filter(row => text(row, 'status_id') === statusId);
          return <div className="kanban-column" key={statusId}>
            <div className="kanban-col-head"><span>{text(status, 'name')}</span><span className="kanban-count">{columnTasks.length}</span></div>
            <SortableContext items={columnTasks.map(row => text(row, 'id'))} strategy={verticalListSortingStrategy}>
              {columnTasks.map(row => <KanbanCard key={text(row, 'id')} row={row}
                onOpen={() => setSelectedId(text(row, 'id'))} />)}
            </SortableContext>
            {columnTasks.length === 0 && <p className="table-message">Chưa có công việc.</p>}
          </div>;
        })}
        {statusRows.length === 0 && <p className="table-message">Chưa cấu hình trạng thái công việc.</p>}
        {move.error && <p className="form-error">{move.error.message}</p>}
      </section>
    </DndContext>}

    {view === 'list' && <section className="panel">
      {taskRows.length ? <div className="task-list">{taskRows.map(row => <button className="task-list-item" key={text(row, 'id')} onClick={() => setSelectedId(text(row, 'id'))}>
        <span className={`priority-pill p-${text(row, 'priority').toLowerCase()}`}>{PRIORITY_LABELS[text(row, 'priority')] ?? text(row, 'priority')}</span>
        <strong>{text(row, 'title')}</strong>
        <small>{text(row, 'code')} · {text(row, 'project_name') || 'Chưa có dự án'} · {text(row, 'assignee_name') || 'Chưa gán'}</small>
        <span className="status-pill">{text(row, 'status_name')}</span>
        <span className={isOverdue(text(row, 'due_date') || null, text(row, 'is_done') === 'true') ? 'overdue-text' : ''}>{text(row, 'due_date') || '—'}</span>
      </button>)}</div> : <div className="empty-table"><p>Chưa có công việc.</p></div>}
    </section>}

    {view === 'table' && <section className="panel table-panel"><h2>Danh sách công việc</h2>
      {taskRows.length ? <div className="table-scroll"><table><thead><tr>
        <th>Mã</th><th>Tiêu đề</th><th>Dự án</th><th>Phòng ban</th><th>Phụ trách</th><th>Giám sát</th>
        <th>Bắt đầu</th><th>Hạn</th><th>Ưu tiên</th><th>Trạng thái</th></tr></thead><tbody>
        {taskRows.map(row => <tr key={text(row, 'id')} onClick={() => setSelectedId(text(row, 'id'))} style={{ cursor: 'pointer' }}>
          <td>{text(row, 'code')}</td><td><strong>{text(row, 'title')}</strong></td>
          <td>{text(row, 'project_code') || '—'}</td><td>{text(row, 'department_name') || '—'}</td>
          <td>{text(row, 'assignee_name') || '—'}</td><td>{text(row, 'supervisor_name') || '—'}</td>
          <td>{text(row, 'starts_on') || '—'}</td>
          <td className={isOverdue(text(row, 'due_date') || null, text(row, 'is_done') === 'true') ? 'overdue-text' : ''}>{text(row, 'due_date') || '—'}</td>
          <td><span className={`priority-pill p-${text(row, 'priority').toLowerCase()}`}>{PRIORITY_LABELS[text(row, 'priority')] ?? text(row, 'priority')}</span></td>
          <td><span className="status-pill">{text(row, 'status_name')}</span></td></tr>)}
      </tbody></table></div> : <div className="empty-table"><p>Chưa có công việc.</p></div>}
    </section>}

    {view === 'calendar' && <section className="panel"><div className="quick-action-title">
      <div><strong>{MONTH_LABELS[calendarBase.getMonth()]} {calendarBase.getFullYear()}</strong><p>Công việc theo hạn hoàn thành.</p></div></div>
      <div className="button-row"><button className="secondary" onClick={() => setMonthOffset(value => value - 1)}><ChevronLeft size={16} /></button>
        <button className="secondary" onClick={() => setMonthOffset(0)}>Tháng này</button>
        <button className="secondary" onClick={() => setMonthOffset(value => value + 1)}><ChevronRight size={16} /></button></div>
      <div className="cal-month">{WEEKDAY_LABELS.map(label => <div className="cal-weekday" key={label}>{label}</div>)}
        {grid.map(cell => <CalendarCell key={cell.iso} cell={cell} tasks={taskRows} onOpen={setSelectedId} />)}
      </div>
    </section>}

    {view === 'gantt' && <section className="panel"><div className="quick-action-title">
      <div><strong>Dòng thời gian</strong><p>Công việc theo ngày bắt đầu và hạn hoàn thành.</p></div></div>
      <div className="gantt-panel">
        <div className="gantt-scale"><div className="gantt-label">Công việc</div><div className="gantt-track">
          {ganttRange.weeks.map(label => <span className="gantt-week" key={label}>{label.slice(5)}</span>)}</div></div>
        {taskRows.map(row => {
          const position = barPosition(text(row, 'starts_on') || null, text(row, 'due_date') || null, ganttRange.start, ganttRange.end);
          const overdue = isOverdue(text(row, 'due_date') || null, text(row, 'is_done') === 'true');
          return <div className="gantt-row" key={text(row, 'id')} onClick={() => setSelectedId(text(row, 'id'))} style={{ cursor: 'pointer' }}>
            <div className="gantt-label"><span className={`priority-pill p-${text(row, 'priority').toLowerCase()}`}>{text(row, 'code')}</span>{text(row, 'title')}</div>
            <div className="gantt-track">
              {position && <span className={`gantt-bar ${overdue ? 'delayed' : ''}`} style={{ left: `${position.left}%`, width: `${position.width}%` }}
                title={`${position.days} ngày`} />}
            </div>
          </div>;
        })}
        <div className="gantt-row"><div className="gantt-label" /><div className="gantt-track">
          <span className="gantt-today" style={{ left: `${ganttTodayOffset}%` }} /></div></div>
        {taskRows.length === 0 && <p className="table-message">Chưa có công việc để hiển thị.</p>}
      </div>
    </section>}

    {can('pm.task.write') && <section className="panel"><div className="quick-action-title"><Plus />
      <div><strong>Tạo công việc</strong><p>Công việc mới bắt đầu ở trạng thái đầu tiên của quy trình.</p></div></div>
      <form className="form-grid compact-form" onSubmit={form.handleSubmit(values => create.mutate(values))}>
        <label className="wide-field">Tiêu đề<input {...form.register('title')} /></label>
        <label>Dự án<select {...form.register('projectId')}><option value="">Chưa có dự án</option>{projects.data?.map(project => <option key={text(project, 'id')} value={text(project, 'id')}>{text(project, 'code')} · {text(project, 'name')}</option>)}</select></label>
        <label>Sprint<select {...form.register('sprintId')}><option value="">Chưa có sprint</option>{sprints.data?.map(sprint => <option key={text(sprint, 'id')} value={text(sprint, 'id')}>{text(sprint, 'name')}</option>)}</select></label>
        <label>Người phụ trách<select {...form.register('assigneeEmployeeId')}><option value="">Chưa gán</option>{employees.data?.map(employee => <option key={text(employee, 'id')} value={text(employee, 'id')}>{text(employee, 'fullName')}</option>)}</select></label>
        <label>Người giám sát<select {...form.register('supervisorEmployeeId')}><option value="">Chưa gán</option>{employees.data?.map(employee => <option key={text(employee, 'id')} value={text(employee, 'id')}>{text(employee, 'fullName')}</option>)}</select></label>
        <label>Ưu tiên<select {...form.register('priority')}>{Object.entries(PRIORITY_LABELS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
        <label>Ước lượng (giờ)<input type="number" min="0" step="0.5" {...form.register('estimatedHours', { valueAsNumber: true })} /></label>
        <label>Bắt đầu<input type="date" {...form.register('startsOn')} /></label>
        <label>Hạn hoàn thành<input type="date" {...form.register('dueDate')} /></label>
        <label className="wide-field">Mô tả<textarea rows={2} {...form.register('description')} /></label>
        <div className="form-actions"><button className="primary" disabled={create.isPending}>Tạo công việc</button>
          {create.isSuccess && <span className="success-text">Đã tạo công việc.</span>}{create.error && <span className="form-error">{create.error.message}</span>}</div>
      </form>
    </section>}

    {selected && <TaskDetailPanel companyId={companyId} taskId={text(selected, 'id')} row={selected}
      onClose={() => setSelectedId('')} />}
  </>;
}

function KanbanCard({ row, onOpen }: { row: PmRow; onOpen: () => void }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: text(row, 'id') });
  const style = { transform: CSS.Transform.toString(transform), transition };
  const overdue = isOverdue(text(row, 'due_date') || null, text(row, 'is_done') === 'true');
  return <div ref={setNodeRef} style={style} className={`kanban-card ${overdue ? 'past-due' : ''} ${isDragging ? 'dragging' : ''}`}
    onClick={onOpen} {...attributes} {...listeners}>
    <div className="kanban-card-head"><strong>{text(row, 'title')}</strong>
      <span className={`priority-pill p-${text(row, 'priority').toLowerCase()}`}>{text(row, 'code')}</span></div>
    <div className="meta"><span>{text(row, 'assignee_name') || 'Chưa gán'}</span>
      <span>{text(row, 'due_date') || 'Không hạn'}</span>
      <span>{num(row, 'progress_percent')}%</span></div>
  </div>;
}

function CalendarCell({ cell, tasks, onOpen }: { cell: DateCell; tasks: PmRow[]; onOpen: (id: string) => void }) {
  const dayTasks = tasks.filter(row => text(row, 'due_date') === cell.iso);
  return <div className={`cal-cell ${cell.inMonth ? '' : 'out'}`}>
    <span className="cal-day-num">{cell.day}</span>
    {dayTasks.map(row => <button key={text(row, 'id')} className={`cal-event ${isOverdue(text(row, 'due_date') || null, text(row, 'is_done') === 'true') ? 'overdue' : ''}`}
      onClick={() => onOpen(text(row, 'id'))}>{text(row, 'title')}</button>)}
  </div>;
}

function TaskDetailPanel({ companyId, taskId, row, onClose }: { companyId: string; taskId: string; row: PmRow; onClose: () => void }) {
  const queryClient = useQueryClient();
  const { can } = useAuth();
  const [comment, setComment] = useState('');
  const [checklistTitle, setChecklistTitle] = useState('');

  const checklist = useQuery({ queryKey: ['pm-task-checklist', companyId, taskId], enabled: !!taskId,
    queryFn: async () => (await api.get<PmRow[]>(`/pm/tasks/${taskId}/checklist`, { params: { companyId } })).data });
  const comments = useQuery({ queryKey: ['pm-task-comments', companyId, taskId], enabled: !!taskId,
    queryFn: async () => (await api.get<PmRow[]>(`/pm/tasks/${taskId}/comments`, { params: { companyId } })).data });
  const attachments = useQuery({ queryKey: ['pm-task-attachments', companyId, taskId], enabled: !!taskId,
    queryFn: async () => (await api.get<PmRow[]>(`/pm/tasks/${taskId}/attachments`, { params: { companyId } })).data });
  const subtasks = useQuery({ queryKey: ['pm-task-subtasks', companyId, taskId], enabled: !!taskId,
    queryFn: async () => (await api.get<PmRow[]>(`/pm/tasks/${taskId}/subtasks`, { params: { companyId } })).data });
  const dependencies = useQuery({ queryKey: ['pm-task-dependencies', companyId, taskId], enabled: !!taskId,
    queryFn: async () => (await api.get<PmRow[]>(`/pm/tasks/${taskId}/dependencies`, { params: { companyId } })).data });
  const activity = useQuery({ queryKey: ['pm-task-activity', companyId, taskId], enabled: !!taskId,
    queryFn: async () => (await api.get<PmRow[]>(`/pm/tasks/${taskId}/activity`, { params: { companyId } })).data });

  const toggle = useMutation({ mutationFn: async (input: { itemId: string; done: boolean }) =>
      (await api.post<Created>(`/pm/tasks/${taskId}/checklist/${input.itemId}/toggle`, { done: input.done }, { params: { companyId } })).data,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['pm-task-checklist', companyId, taskId] }); queryClient.invalidateQueries({ queryKey: ['pm-tasks', companyId] }); } });
  const addItem = useMutation({ mutationFn: async (title: string) =>
      (await api.post<Created>(`/pm/tasks/${taskId}/checklist`, { title }, { params: { companyId } })).data,
    onSuccess: () => { setChecklistTitle(''); queryClient.invalidateQueries({ queryKey: ['pm-task-checklist', companyId, taskId] }); } });
  const addComment = useMutation({ mutationFn: async (body: string) =>
      (await api.post<Created>(`/pm/tasks/${taskId}/comments`, { body }, { params: { companyId } })).data,
    onSuccess: () => { setComment(''); queryClient.invalidateQueries({ queryKey: ['pm-task-comments', companyId, taskId] }); } });

  const watchers = useMemo(() => {
    const raw = row.watcher_subjects;
    if (!raw) return [] as string[];
    if (Array.isArray(raw)) return raw.map(String);
    try { const parsed = JSON.parse(String(raw)); return Array.isArray(parsed) ? parsed.map(String) : []; } catch { return []; }
  }, [row.watcher_subjects]);

  return <section className="panel task-detail">
    <div className="quick-action-title"><div><strong>{text(row, 'code')} · {text(row, 'title')}</strong>
      <p>{text(row, 'description') || 'Chưa có mô tả.'}</p></div>
      <button className="secondary" onClick={onClose}>Đóng</button></div>
    <div className="private-grid">
      <div><dt>Dự án</dt><dd>{text(row, 'project_name') || 'Chưa có'}</dd></div>
      <div><dt>Phòng ban</dt><dd>{text(row, 'department_name') || 'Chưa có'}</dd></div>
      <div><dt>Người phụ trách</dt><dd>{text(row, 'assignee_name') || 'Chưa gán'}</dd></div>
      <div><dt>Người giám sát</dt><dd>{text(row, 'supervisor_name') || 'Chưa gán'}</dd></div>
      <div><dt>Người liên quan</dt><dd>{watchers.length ? watchers.join(', ') : 'Chưa có'}</dd></div>
      <div><dt>Thời gian</dt><dd>{text(row, 'starts_on') || '—'} → {text(row, 'due_date') || '—'}</dd></div>
      <div><dt>Ưu tiên</dt><dd>{PRIORITY_LABELS[text(row, 'priority')] ?? text(row, 'priority')}</dd></div>
      <div><dt>Trạng thái</dt><dd>{text(row, 'status_name')} · {num(row, 'progress_percent')}%</dd></div>
      <div><dt>Sprint</dt><dd>{text(row, 'sprint_name') || 'Chưa có'}</dd></div>
      <div><dt>Ước lượng</dt><dd>{text(row, 'estimated_hours') || '—'} giờ</dd></div>
    </div>

    <div className="detail-columns">
      <div className="detail-block"><h3>Checklist</h3>
        {checklist.data?.map(item => <label className="checklist-item" key={text(item, 'id')}>
          <input type="checkbox" checked={text(item, 'done') === 'true'} disabled={!can('pm.task.write')}
            onChange={event => toggle.mutate({ itemId: text(item, 'id'), done: event.target.checked })} />
          <span className={text(item, 'done') === 'true' ? 'done' : ''}>{text(item, 'title')}</span></label>)}
        {can('pm.task.write') && <div className="button-row"><input value={checklistTitle} onChange={event => setChecklistTitle(event.target.value)}
          placeholder="Thêm hạng mục" /><button className="secondary" disabled={!checklistTitle.trim() || addItem.isPending}
            onClick={() => addItem.mutate(checklistTitle.trim())}>Thêm</button></div>}
      </div>
      <div className="detail-block"><h3>Bình luận</h3>
        {comments.data?.map(item => <div className="comment" key={text(item, 'id')}><strong>{text(item, 'author_subject')}</strong>
          <p>{text(item, 'body')}</p></div>)}
        {can('pm.task.write') && <div className="button-row"><input value={comment} onChange={event => setComment(event.target.value)}
          placeholder="Viết bình luận" /><button className="secondary" disabled={!comment.trim() || addComment.isPending}
            onClick={() => addComment.mutate(comment.trim())}>Gửi</button></div>}
      </div>
      <div className="detail-block"><h3>Công việc con</h3>
        {subtasks.data?.length ? subtasks.data.map(item => <p key={text(item, 'id')} className="table-message">{text(item, 'code')} · {text(item, 'title')}</p>)
          : <p className="table-message">Chưa có công việc con.</p>}
      </div>
      <div className="detail-block"><h3>Phụ thuộc</h3>
        {dependencies.data?.length ? dependencies.data.map(item => <p key={text(item, 'id')} className="table-message">{text(item, 'code')} · {text(item, 'title')}</p>)
          : <p className="table-message">Chưa có phụ thuộc.</p>}
      </div>
      <div className="detail-block"><h3>Tệp đính kèm</h3>
        {attachments.data?.length ? attachments.data.map(item => <p key={text(item, 'id')} className="table-message">{text(item, 'display_name')}</p>)
          : <p className="table-message">Chưa có tệp.</p>}
      </div>
      <div className="detail-block"><h3>Lịch sử hoạt động</h3>
        {activity.data?.length ? activity.data.map(item => <p key={text(item, 'id')} className="table-message">
          {text(item, 'created_at')} · {text(item, 'actor_subject')} · {text(item, 'action')}</p>)
          : <p className="table-message">Chưa có hoạt động nào.</p>}
      </div>
    </div>
  </section>;
}
