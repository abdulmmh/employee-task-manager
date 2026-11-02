import { FormBuilder } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { UsersComponent } from './users.component';
import { WorkspaceApiService } from '../../core/api/workspace-api.service';
import { ToastService } from '../../shared/toast/toast.service';
import { User } from '../../core/api/models';

describe('UsersComponent soft-delete presentation', () => {
  let component: UsersComponent;
  let api: jasmine.SpyObj<WorkspaceApiService>;
  let toast: jasmine.SpyObj<ToastService>;
  const user: User = { id: 3, fullName: 'Employee One', email: 'emp1@demo.com', role: 'EMPLOYEE', managerId: 2, active: true, createdAt: '' };
  beforeEach(() => {
    api = jasmine.createSpyObj<WorkspaceApiService>('WorkspaceApiService', ['users', 'deleteUser', 'saveUser']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['show']);
    api.users.and.returnValue(of({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }));
    component = new UsersComponent(new FormBuilder(), api, toast);
  });
  afterEach(() => component.ngOnDestroy());
  it('always requests active users so soft-deleted records stay out of the list', () => {
    component.load();
    expect(api.users).toHaveBeenCalledWith({ page: 0, size: 10, search: '', active: true });
    expect(component.filters.get('active')).toBeNull();
    expect(component.form.get('active')).toBeNull();
  });
  it('deletes through the existing DELETE API and reloads the active list', () => {
    component.confirming = user; api.deleteUser.and.returnValue(of(undefined));
    component.deleteUser();
    expect(api.deleteUser).toHaveBeenCalledWith(3);
    expect(component.confirming).toBeUndefined();
    expect(api.users).toHaveBeenCalledWith({ page: 0, size: 10, search: '', active: true });
    expect(toast.show).toHaveBeenCalledWith('success', 'User deleted successfully.');
  });
  it('preserves the list and reports a backend safeguard conflict', () => {
    component.users = [user]; component.confirming = user;
    api.deleteUser.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'The last active administrator cannot be deleted.' } })));
    component.deleteUser();
    expect(component.users).toEqual([user]);
    expect(api.users).not.toHaveBeenCalled();
    expect(toast.show).toHaveBeenCalledWith('error', 'The last active administrator cannot be deleted.');
  });
});
