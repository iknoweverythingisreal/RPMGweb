import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';

export interface EventItemRequestDTO {
  itemId: string;
  requestedQuantity: number;
  unitPrice: number;
  rateType: 'daily' | 'hourly' | 'fixed';
}

@Injectable({
  providedIn: 'root'
})
export class EventItemsService {
  constructor(private http: HttpClient) { }



  addBulkItemsToEvent(eventId: number, items: any[]) {
    return this.http.post(`${environment.apiUrl}/api/event-items/bulk/${eventId}`, items);
  }

  syncVirtualStorage() {
    return this.http.post(environment.apiUrl + '/api/inventory/auto-sync', {});
  }

  getAllEventItems() {
    return this.http.get<any[]>(environment.apiUrl + '/api/event-items');
  }

  getPendingRentals() {
    return this.http.get<any[]>(environment.apiUrl + '/api/event-items/pending');
  }

  getEventItems(eventId: number): Observable<any[]> {
    return this.http.get<any[]>(`${environment.apiUrl}/api/event-items/event/${eventId}`);
  }

  getAvailability(startDate: string, endDate: string, eventId?: number) {
    const params: any = { startDate, endDate };
    if (eventId) params.excludeEventId = eventId;
    return this.http.get<any[]>(environment.apiUrl + '/api/inventory/availability', { params });
  }

  getBulkAvailability(itemIds: number[], startDate: string, endDate: string, eventId?: number) {
    const body = {
      itemIds,
      startDate,
      endDate,
      eventId
    };
    return this.http.post<any[]>(`${environment.apiUrl}/api/items/availability/bulk`, body);
  }

  approveOverbook(id: number, approverId: number, note: string = '') {
    return this.http.post<any>(`${environment.apiUrl}/api/event-items/${id}/approve-overbook`, {}, {
      params: { approverId, note }
    });
  }

  rejectOverbook(id: number, approverId: number, note: string = '') {
    return this.http.post<any>(`${environment.apiUrl}/api/event-items/${id}/reject-overbook`, {}, {
      params: { approverId, note }
    });
  }

  requestRentExternal(eventId: number, requesterId: number, itemId: number, qty: number, reason: string = '') {
    return this.http.post(`${environment.apiUrl}/api/event-items/${eventId}/request-rent`, {}, {
      params: {
        requesterId,
        itemId,
        qty,
        reason
      },
      responseType: 'text'
    });
  }

  assignToRoom(id: number, roomName: string, quantity: number) {
    return this.http.post(`${environment.apiUrl}/api/event-items/${id}/assign-room`, {}, {
      params: { roomName, quantity },
      responseType: 'text'
    });
  }

  deleteRoom(eventId: number, roomName: string) {
    return this.http.delete(`${environment.apiUrl}/api/event-items/event/${eventId}/room`, {
      params: { roomName },
      responseType: 'text'
    });
  }

  updateQuantity(eventItemId: number, quantity: number) {
    return this.http.put(`${environment.apiUrl}/api/event-items/${eventItemId}/quantity`, { quantity }, { responseType: 'text' });
  }

  deleteEventItem(id: number) {
    return this.http.delete(`${environment.apiUrl}/api/event-items/${id}`, { responseType: 'text' });
  }

  confirmEventItems(eventId: number, confirmedBy: number) {
    return this.http.put(`${environment.apiUrl}/api/event-items/confirm/${eventId}`, {}, {
      params: { confirmedBy },
      responseType: 'text'
    });
  }
}
