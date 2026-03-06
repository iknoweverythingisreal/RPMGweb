import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SerialActionRequest, LinkUnitsRequest } from '../core/models/serial.model';

export interface SerialUnit {
  itemUnitId: number;
  serial: string;
  status: string;
  pickedAt?: string;
  outAt?: string;
  returnedAt?: string;
  note?: string;
}

@Injectable({ providedIn: 'root' })
export class SerialOpsService {

  private http = inject(HttpClient);
  private baseUrl = '/api/events';

  // ✔ NEW: get units assigned to this event-item
  getUnits(eventId: number, eventItemId: number) {
    return this.http.get<SerialUnit[]>(
      `${this.baseUrl}/${eventId}/equipment/${eventItemId}/units`
    );
  }

  linkUnits(eventId: number, eventItemId: number, req: LinkUnitsRequest) {
    return this.http.post(`${this.baseUrl}/${eventId}/equipment/${eventItemId}/units`, req);
  }

  checkout(eventId: number, req: SerialActionRequest) {
    return this.http.post(`${this.baseUrl}/${eventId}/checkout`, req);
  }

  doReturn(eventId: number, req: SerialActionRequest) {
    return this.http.post(`${this.baseUrl}/${eventId}/return`, req);
  }

  damage(eventId: number, req: SerialActionRequest) {
    return this.http.post(`${this.baseUrl}/${eventId}/damage`, req);
  }
}
