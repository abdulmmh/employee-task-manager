import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { NEVER, of, Subject } from 'rxjs';
import { PersonPickerComponent } from './person-picker.component';
import { IconComponent } from './icon.component';
import { WorkspaceApiService } from '../core/api/workspace-api.service';
import { PageResponse, PersonOption } from '../core/api/models';

describe('PersonPickerComponent', () => {
  let fixture: ComponentFixture<PersonPickerComponent>;
  let component: PersonPickerComponent;
  let api: jasmine.SpyObj<WorkspaceApiService>;
  const page: PageResponse<PersonOption> = { content: [{ id: 3, name: 'Employee One' }], page: 0, size: 20, totalElements: 1, totalPages: 1 };
  beforeEach(async () => {
    api = jasmine.createSpyObj<WorkspaceApiService>('WorkspaceApiService', ['employees', 'users', 'user']);
    api.employees.and.returnValue(of(page));
    await TestBed.configureTestingModule({ imports: [ReactiveFormsModule], declarations: [PersonPickerComponent, IconComponent], providers: [{ provide: WorkspaceApiService, useValue: api }] }).compileComponents();
    fixture = TestBed.createComponent(PersonPickerComponent); component = fixture.componentInstance; fixture.detectChanges();
  });
  afterEach(() => fixture.destroy());

  it('shows a direct option list and closes after choosing', () => {
    spyOn(component.selected, 'emit'); component.toggle(); fixture.detectChanges();
    const option = fixture.nativeElement.querySelector('.picker-option') as HTMLButtonElement;
    expect(option.textContent).toContain('Employee One'); option.click(); fixture.detectChanges();
    expect(component.selected.emit).toHaveBeenCalledWith({ id: 3, name: 'Employee One' });
    expect(component.opened).toBeFalse(); expect(fixture.nativeElement.querySelector('select')).toBeNull();
  });

  it('closes on outside click', () => {
    component.toggle(); fixture.detectChanges(); document.body.click();
    expect(component.opened).toBeFalse();
  });

  it('closes on Escape without dismissing a parent dialog', () => {
    component.toggle(); fixture.detectChanges();
    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
    fixture.nativeElement.dispatchEvent(event);
    expect(component.opened).toBeFalse(); expect(event.defaultPrevented).toBeTrue();
  });

  it('sends a debounced search term to the backend', fakeAsync(() => {
    component.toggle(); component.search.setValue('Employee'); tick(251);
    expect(api.employees).toHaveBeenCalledWith({ page: 0, size: 20, search: 'Employee' });
    component.close();
  }));

  it('shows an empty result rather than keeping loading visible', () => {
    api.employees.and.returnValue(of({ ...page, content: [], totalElements: 0, totalPages: 0 }));
    component.toggle(); fixture.detectChanges();
    expect(component.loading).toBeFalse(); expect(fixture.nativeElement.textContent).toContain('No matching employee found');
  });

  it('ends an unresponsive request with a retry message', fakeAsync(() => {
    api.employees.and.returnValue(NEVER); component.toggle(); tick(10001);
    expect(component.loading).toBeFalse(); expect(component.error).toContain('taking too long'); component.close();
  }));

  it('cancels the previous search before requesting newer options', fakeAsync(() => {
    const pending = new Subject<PageResponse<PersonOption>>(); api.employees.and.returnValue(pending);
    component.toggle(); expect(pending.observed).toBeTrue();
    api.employees.and.returnValue(of(page)); component.search.setValue('One'); tick(251);
    expect(pending.observed).toBeFalse(); expect(component.options[0].name).toBe('Employee One'); component.close();
  }));
});
