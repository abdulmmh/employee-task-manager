import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-icon',
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="paths[name] || paths['tasks']"></path></svg>`,
  styles: [':host{display:inline-flex;width:1.3rem;height:1.3rem;flex-shrink:0}svg{width:100%;height:100%}']
})
export class IconComponent {
  @Input() name = 'tasks';
  readonly paths: Record<string, string> = {
    dashboard: 'M3 10 12 3l9 7v10H3V10Zm6 10v-7h6v7',
    users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-3.87M15 3.13a4 4 0 0 1 0 7.75M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
    tasks: 'M9 3H5v18h14V3h-4M9 2h6v4H9V2Zm-1 9 2 2 5-5m-7 9h7',
    reports: 'M4 21V11h4v10m3 0V4h4v17m3 0v-7h4v7M2 21h21',
    logout: 'M9 3H3v18h6m6-14 5 5-5 5m-7-5h12',
    menu: 'M3 6h18M3 12h18M3 18h18',
    check: 'M5 12l4 4L19 6m3 6a10 10 0 1 1-5-9',
    sun: 'M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5M17.5 17.5 19 19M5 19l1.5-1.5M17.5 6.5 19 5m-2 7a5 5 0 1 1-10 0 5 5 0 0 1 10 0Z',
    download: 'M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5',
    arrow: 'M5 12h14m-5-5 5 5-5 5',
    lock: 'M6 10h12v11H6V10Zm2 0V6a4 4 0 0 1 8 0v4m-4 5v2'
  };
}
