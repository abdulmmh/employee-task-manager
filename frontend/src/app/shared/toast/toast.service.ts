import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface Toast { id: number; kind: 'success' | 'error' | 'info'; title: string; message: string; duration: number; }

@Injectable({ providedIn: 'root' })
export class ToastService {
  private sequence = 0;
  private state = new BehaviorSubject<Toast[]>([]);
  readonly items$ = this.state.asObservable();
  private timers = new Map<number, ReturnType<typeof setTimeout>>();

  show(kind: Toast['kind'], message: string, title = kind === 'success' ? 'Success' : kind === 'error' ? 'Error' : 'Notice'): void {
    const toast = { id: ++this.sequence, kind, title, message, duration: kind === 'error' ? 8000 : 5000 };
    if (this.state.value.length >= 3) this.dismiss(this.state.value[0].id);
    this.state.next([...this.state.value, toast]);
    this.timers.set(toast.id, setTimeout(() => this.dismiss(toast.id), toast.duration));
  }
  dismiss(id: number): void {
    clearTimeout(this.timers.get(id));
    this.timers.delete(id);
    this.state.next(this.state.value.filter(item => item.id !== id));
  }
}
