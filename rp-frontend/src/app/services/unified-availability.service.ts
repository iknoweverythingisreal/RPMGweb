import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { UnifiedAvailabilityDTO } from '../core/models/unified-availability.model';

@Injectable({ providedIn: 'root' })
export class UnifiedAvailabilityService {
  private http = inject(HttpClient);
  private baseUrl = '/api/items';

  getFullAvailability(itemId: number, eventId: number, start: string, end: string) {
    return this.http.get<UnifiedAvailabilityDTO>(
      `${this.baseUrl}/${itemId}/availability/full`,
      { params: { eventId, startDate: start, endDate: end } }
    );
  }
}
