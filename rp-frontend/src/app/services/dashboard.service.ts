import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DashboardSummary {
  totalEvents: number;
  draftItems: number;
  confirmedItems: number;
  readyItems: number;
  checkedItems: number;
  rentPending: number;
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private http = inject(HttpClient);
  private API = '/api/dashboard/summary';

  getSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(this.API);
  }
}
