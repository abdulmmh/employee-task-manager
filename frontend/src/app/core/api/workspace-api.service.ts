import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Dashboard, PageResponse, PersonOption, Query, Task, TaskInput, TaskStatus, User, UserInput } from './models';

@Injectable({ providedIn: 'root' })
export class WorkspaceApiService {
  constructor(private http: HttpClient) {}
  dashboard() { return this.http.get<Dashboard>(`${environment.apiUrl}/dashboard/stats`); }
  recentTasks(page = 0, size = 4) { return this.http.get<PageResponse<Task>>(`${environment.apiUrl}/tasks`, { params: { page, size } }); }
  report() { return this.http.get(`${environment.apiUrl}/reports/tasks/pdf`, { responseType: 'blob' }); }
  users(params: Query) { return this.http.get<PageResponse<User>>(`${environment.apiUrl}/users`, { params }); }
  user(id: number) { return this.http.get<User>(`${environment.apiUrl}/users/${id}`); }
  saveUser(input: UserInput, id?: number) {
    if (id) return this.http.put<User>(`${environment.apiUrl}/users/${id}`, input);
    const { active, ...create } = input;
    return this.http.post<User>(`${environment.apiUrl}/users`, create);
  }
  deactivate(id: number) { return this.http.delete<void>(`${environment.apiUrl}/users/${id}`); }
  employees(params: Query) { return this.http.get<PageResponse<PersonOption>>(`${environment.apiUrl}/team/employees`, { params }); }
  tasks(params: Query) { return this.http.get<PageResponse<Task>>(`${environment.apiUrl}/tasks`, { params }); }
  saveTask(input: TaskInput, id?: number) {
    return id ? this.http.put<Task>(`${environment.apiUrl}/tasks/${id}`, input)
      : this.http.post<Task>(`${environment.apiUrl}/tasks`, input);
  }
  changeStatus(id: number, status: TaskStatus) { return this.http.put<Task>(`${environment.apiUrl}/tasks/${id}/status`, { status }); }
  deleteTask(id: number) { return this.http.delete<void>(`${environment.apiUrl}/tasks/${id}`); }
}
