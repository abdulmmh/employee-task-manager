import { AbstractControl, FormGroup, ValidationErrors } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

export function passwordBytes(control: AbstractControl): ValidationErrors | null {
  return control.value && new TextEncoder().encode(String(control.value)).length > 72 ? { passwordBytes: true } : null;
}
export function applyServerErrors(form: FormGroup, error: unknown): void {
  if (!(error instanceof HttpErrorResponse) || !error.error?.fieldErrors) return;
  const fields = error.error.fieldErrors as Record<string, string>;
  Object.keys(fields).forEach(name => {
    const control = form.get(name);
    if (control) { control.setErrors({ ...control.errors, server: fields[name] }); control.markAsTouched(); }
  });
}
export function fieldError(form: FormGroup, name: string): string {
  const control = form.get(name);
  if (!control?.touched || !control.errors) return '';
  const errors = control.errors;
  if (errors['server']) return String(errors['server']);
  if (errors['required']) return 'This field is required.';
  if (errors['email']) return 'Enter a valid email address.';
  if (errors['maxlength']) return `Use at most ${errors['maxlength'].requiredLength} characters.`;
  if (errors['passwordBytes']) return 'Password must not exceed 72 UTF-8 bytes.';
  return 'Check this value.';
}
