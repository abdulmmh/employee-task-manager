export type Role = 'ADMIN' | 'MANAGER' | 'EMPLOYEE';
export type TaskStatus = 'PENDING' | 'IN_PROGRESS' | 'DONE';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH';

export interface LoginResponse { token: string; role: Role; name: string; }
export interface Session extends LoginResponse { expiresAt: number; userId: number; }
export interface ApiError { status: number; message: string; fieldErrors: Record<string, string>; }
export interface PageResponse<T> {
  content: T[]; page: number; size: number; totalElements: number; totalPages: number;
}
export interface Task {
  id: number; title: string; description: string | null; status: TaskStatus; priority: TaskPriority;
  deadline: string | null; assignedTo: number; assigneeName: string; assignedBy: number;
  assignerName: string; createdAt: string; updatedAt: string;
}
export interface Dashboard {
  role: Role; totalUsers?: number; activeUsers?: number; teamSize?: number;
  totalTasks: number; completedTasks: number;
  tasksByStatus: { pending: number; inProgress: number; done: number };
}
export interface User {
  id: number; fullName: string; email: string; role: Role; managerId: number | null;
  active: boolean; createdAt: string;
}
export interface UserInput {
  fullName: string; email: string; password?: string; role: Role; managerId: number | null; active: boolean;
}
export interface TaskInput {
  title: string; description: string | null; priority: TaskPriority; deadline: string | null; assignedTo: number;
}
export interface PersonOption { id: number; name: string; }
export type Query = Record<string, string | number | boolean>;
