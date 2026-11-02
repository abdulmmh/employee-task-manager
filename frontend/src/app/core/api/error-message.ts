import { HttpErrorResponse } from '@angular/common/http';

export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) return 'Unable to reach the server. Check your connection and try again.';
    if (error.status === 403) return 'You do not have permission for this action.';
    if (typeof error.error?.message === 'string') return error.error.message;
    if (error.status >= 500) return 'The service is temporarily unavailable. Please try again.';
  }
  return 'Something went wrong. Please try again.';
}
