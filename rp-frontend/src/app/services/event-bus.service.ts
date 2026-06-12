// src/app/services/event-bus.service.ts

import { Injectable } from '@angular/core';
import { Subject, Observable, filter, map } from 'rxjs';

interface EventPayload {
  name: string;
  data?: any;
}

@Injectable({ providedIn: 'root' })
export class EventBusService {

  private event$ = new Subject<EventPayload>();

  /** ส่ง event */
  emit(name: string, data?: any) {
    this.event$.next({ name, data });
  }

  /** รับ event */
  on(name: string): Observable<any> {
    return this.event$.pipe(
      filter(e => e.name === name),
      map(e => e.data)
    );
  }

}
