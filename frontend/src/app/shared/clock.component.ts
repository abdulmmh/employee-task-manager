import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription, timer } from 'rxjs';

@Component({ selector: 'app-clock', template: `<time class="workspace-clock" [attr.datetime]="now.toISOString()"><span>{{ now | date:'EEE, MMM d, y' }}</span><strong>{{ now | date:'h:mm a' }}</strong></time>` })
export class ClockComponent implements OnInit, OnDestroy {
  now = new Date(); private tick?: Subscription;
  ngOnInit(): void { this.tick = timer(60000 - Date.now() % 60000, 60000).subscribe(() => this.now = new Date()); }
  ngOnDestroy(): void { this.tick?.unsubscribe(); }
}
