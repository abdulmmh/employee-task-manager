import { NO_ERRORS_SCHEMA } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TasksComponent } from './tasks.component';
import { AuthService } from '../../core/auth/auth.service';
import { Role } from '../../core/api/models';
import { ToastService } from '../../shared/toast/toast.service';
import { environment } from '../../../environments/environment';

describe('TasksComponent admin assignee-role filtering', () => {
  let fixture: ComponentFixture<TasksComponent>;
  let http: HttpTestingController;
  let auth: { session: { role: Role } };
  const url = `${environment.apiUrl}/tasks`;
  const emptyPage = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 };

  beforeEach(waitForAsync(() => {
    auth = { session: { role: 'ADMIN' } };
    TestBed.configureTestingModule({
      imports: [CommonModule, ReactiveFormsModule, HttpClientTestingModule],
      declarations: [TasksComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
        { provide: Router, useValue: jasmine.createSpyObj('Router', ['navigate']) },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['show']) }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
  }));

  function start(role: Role = 'ADMIN'): TasksComponent {
    auth.session.role = role;
    fixture = TestBed.createComponent(TasksComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const initial = http.expectOne(request => request.url === url);
    expect(initial.request.params.has('role')).toBeFalse();
    initial.flush(emptyPage);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  afterEach(() => {
    http?.verify();
    fixture?.destroy();
  });

  it('combines the selected role with existing filters and starts from page zero', () => {
    const component = start();
    component.page = 2;
    component.filters.patchValue({ status: 'DONE', priority: 'HIGH' });
    component.assigneeFilter = { id: 3, name: 'Employee One' };
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('[formControlName="role"]');
    select.value = 'EMPLOYEE';
    select.dispatchEvent(new Event('change'));

    const request = http.expectOne(request => request.url === url);
    const query = new URL(request.request.urlWithParams, 'http://localhost').searchParams;
    expect(query.get('role')).toBe('EMPLOYEE');
    expect(query.get('status')).toBe('DONE');
    expect(query.get('priority')).toBe('HIGH');
    expect(query.get('assignee')).toBe('3');
    expect(query.get('page')).toBe('0');
    request.flush(emptyPage);
  });

  it('clears role, assignee, status, priority and pagination together', () => {
    const component = start();
    component.filters.patchValue({ role: 'MANAGER', status: 'DONE', priority: 'HIGH' });
    component.assigneeFilter = { id: 3, name: 'Employee One' };
    component.page = 2;
    fixture.detectChanges();
    const clear: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-clear-filters');
    expect(clear.disabled).toBeFalse();
    clear.click();

    const request = http.expectOne(request => request.url === url);
    expect(request.request.params.keys().sort()).toEqual(['page', 'size']);
    const query = new URL(request.request.urlWithParams, 'http://localhost').searchParams;
    expect(query.get('page')).toBe('0');
    expect(component.filters.get('role')!.value).toBe('');
    expect(component.assigneeFilter).toBeNull();
    expect(component.hasFilters).toBeFalse();
    request.flush(emptyPage);
  });

  for (const role of ['MANAGER', 'EMPLOYEE'] as Role[]) {
    it(`hides the role filter and omits its parameter for ${role}`, () => {
      const component = start(role);
      expect(fixture.nativeElement.querySelector('[formControlName="role"]')).toBeNull();
      component.filters.patchValue({ role: 'ADMIN' });
      component.filter();
      const request = http.expectOne(request => request.url === url);
      expect(request.request.params.has('role')).toBeFalse();
      request.flush(emptyPage);
    });
  }
});
