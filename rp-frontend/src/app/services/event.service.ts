import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';

/* ---------- Types สำหรับ entity หลัก ---------- */
export interface CalendarEvent {
  id: number;
  title: string;
  description?: string;
  location?: string;
  startDate: string;      // YYYY-MM-DD
  endDate: string;
  startTime: string;      // HH:mm:ss
  endTime: string;           // HH:mm:ss

  color?: string;
  department?: { id: number; name: string } | null;
  createdBy?: {              // relation (User)
    id: number;
    name: string;
    email: string;
    role: string;
    calendarColor?: string;
  } | null;

  status?: string;
  customFields?: {
    category?: string;
    [key: string]: any;
  };
}

/* ---------- Types สำหรับ DTO/UI ---------- */
export interface UserLight {
  id: number;
  displayName: string;
  calendarColor: string;
  role?: string;
}

export interface CalendarEventDTO {
  id: number;
  title: string;
  description?: string;
  startDate: string;      // YYYY-MM-DD
  endDate: string;
  startTime: string;      // HH:mm:ss
  endTime: string;
  ownerId: number | null;
  ownerName?: string;
  ownerColor?: string;
  type?: string;
  location?: string;
  teamupSubcalendarIds?: number[];
}

export interface CalendarOwner {
  id: number;
  teamupSubcalendarId: number;
  name: string;
  colorHex?: string;
  isActive: boolean;
}

export interface CreateEventRequest {
  title: string;
  description?: string;
  location?: string;
  startDate: string;      // YYYY-MM-DD
  endDate: string;        // YYYY-MM-DD
  startTime: string;      // HH:mm:ss (ถ้าเป็น HH:mm เดี๋ยว helper เติม :00)
  endTime: string;        // HH:mm:ss
  ownerId: number;        // ✅ กัน created_by = NULL
  managerIds?: number[];  // Multiple managers for the event
  type?: string;
  customFields?: Record<string, unknown>;
}

// --- History types ---
export interface EventHistoryEntry {
  id: number;
  eventId: number;
  userId?: number;
  action?: string;
  note?: string;
  createdAt: string;     // <-- ใช้ createdAt (อย่าใช้ changedAt)
  // สำหรับ log แบบเดิม (key-value) เผื่อยังส่งมา:
  changeType?: string;
  data?: Record<string, any>;
}

@Injectable({ providedIn: 'root' })
export class EventService {
  private http = inject(HttpClient);

  // 🔧 ยิงไป backend 8080 ตรง ๆ (ไม่ต้องมี proxy)
  private apiUrl = ''; // Base URL is handled by proxy or empty for same-origin
  private API_EVENTS = environment.apiUrl + '/api/events';
  private API_USERS = environment.apiUrl + '/api/users';

  /* ====== CRUD entity ตรง ๆ ====== */
  getEvents(): Observable<CalendarEvent[]> {
    return this.http.get<CalendarEvent[]>(this.API_EVENTS);
  }

  getEventById(id: number): Observable<CalendarEvent> {
    return this.http.get<CalendarEvent>(`${this.API_EVENTS}/${id}`);
  }

  getTeamupEvents(): Observable<CalendarEvent[]> {
    return this.http.get<CalendarEvent[]>(environment.apiUrl + '/api/events/teamup');
  }

  createEventEntity(event: CalendarEvent): Observable<CalendarEvent> {
    return this.http.post<CalendarEvent>(this.API_EVENTS, event);
  }

  updateEventEntity(id: number, event: CalendarEvent): Observable<CalendarEvent> {
    return this.http.put<CalendarEvent>(`${this.API_EVENTS}/${id}`, event);
  }

  deleteEvent(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_EVENTS}/${id}`);
  }

  deleteEventItem(id: number): Observable<void> {
    return this.http.delete<void>(`/api/event-items/${id}`);
  }

  syncFromTeamup(): Observable<any> {
    return this.http.get(environment.apiUrl + '/api/teamup/sync-changes');
  }

  syncFutureEvents(): Observable<any> {
    return this.http.get(environment.apiUrl + '/api/teamup/sync-future', { responseType: 'text' });
  }

  /* ====== เมธอดสำหรับหน้า Calendar (DTO) ====== */

  // (optional) รายชื่อผู้ใช้แบบเบา ๆ สำหรับ sidebar
  getUsersLight(): Observable<UserLight[]> {
    return this.http.get<UserLight[]>(`${this.API_USERS}/light`);
  }

  getOwners(): Observable<CalendarOwner[]> {
    return this.http.get<CalendarOwner[]>(environment.apiUrl + '/api/teamup/owners');
  }

  // (optional) ดึงอีเวนต์ตามช่วงวัน + กรอง user
  getEventsRange(from: string, to: string, userIds?: number[]): Observable<CalendarEventDTO[]> {
    const params: any = { from, to };
    if (userIds?.length) params.userIds = userIds.join(',');
    return this.http.get<CalendarEventDTO[]>(`${this.API_EVENTS}/range`, { params });
  }

  // POST /api/events
  createEvent(req: CreateEventRequest) {
    const body: any = {
      title: req.title,
      description: req.description ?? '',
      location: req.location,
      startDate: req.startDate,
      endDate: req.endDate,
      startTime: DateTimeHelper.ensureSeconds(req.startTime),
      endTime: DateTimeHelper.ensureSeconds(req.endTime),

      // ✅ ต้องเป็น object
      createdBy: { id: req.ownerId },

      // Add manager IDs if provided
      managers: req.managerIds?.map(id => ({ id })) ?? [],

      customFields: req.customFields ?? (req.type ? { type: req.type } : undefined),
    };
    return this.http.post<CalendarEvent>(this.API_EVENTS, body);
  }

  // PUT /api/events/{id}
  updateEvent(id: number, req: CreateEventRequest) {
    const body: any = {
      title: req.title,
      description: req.description ?? '',
      location: req.location,
      startDate: req.startDate,
      endDate: req.endDate,
      startTime: DateTimeHelper.ensureSeconds(req.startTime),
      endTime: DateTimeHelper.ensureSeconds(req.endTime),

      // ✅ ต้องเป็น object
      createdBy: { id: req.ownerId },

      // Add manager IDs if provided
      managers: req.managerIds?.map(id => ({ id })) ?? [],

      customFields: req.customFields ?? (req.type ? { type: req.type } : undefined),
    };
    return this.http.put<CalendarEvent>(`${this.API_EVENTS}/${id}`, body);
  }

  // ===== Technical / Manager actions =====

  confirmEventItems(eventId: number, confirmedBy: number) {
    return this.http.put(`/api/event-items/confirm/${eventId}`, null, {
      params: { confirmedBy: confirmedBy.toString() },
    });
  }

  markPrepared(eventId: number, preparedBy: number) {
    return this.http.put(`/api/event-items/${eventId}/prepare`, null, {
      params: { preparedBy: preparedBy.toString() },
    });
  }

  markChecked(eventId: number, checkedBy: number) {
    return this.http.put(`/api/event-items/${eventId}/check`, null, {
      params: { checkedBy: checkedBy.toString() },
    });
  }

  getEventItems(eventId: number) {
    return this.http.get<any[]>(`/api/event-items/event/${eventId}`);
  }

  // 🔹 Event History
  getEventHistory(eventId: number) {
    return this.http.get<EventHistoryEntry[]>(`/api/event-history/event/${eventId}`);
  }

  requestRentExternal(eventId: number, requesterId: number, itemId: number, qty: number, reason?: string) {
    let params = new HttpParams()
      .set('requesterId', requesterId.toString())
      .set('itemId', itemId.toString())
      .set('qty', qty.toString());
    if (reason) params = params.set('reason', reason);
    return this.http.post(`/api/event-items/${eventId}/request-rent`, null, { params });
  }

  approveRentExternal(eventItemId: number, approverId: number, approved: boolean, note?: string) {
    let params = new HttpParams()
      .set('approverId', approverId.toString())
      .set('approved', approved.toString());
    if (note) params = params.set('note', note);

    return this.http.put(`/api/event-items/approve-rent/${eventItemId}`, {}, { params });
  }

  // ✅ Manager Reserve Item
  reserveItem(eventId: number, itemId: number, qty: number, userId: number) {
    return this.http.post(`/api/event-items/${eventId}/reserve`, null, {
      params: {
        itemId: itemId.toString(),
        qty: qty.toString(),
        userId: userId.toString()
      },
    });
  }

  // 🔹 Return Flow
  requestReturn(eventId: number, requesterId: number) {
    return this.http.post(`/api/event-items/return-request/${eventId}`, null, {
      params: { requesterId: requesterId.toString() },
      responseType: 'text'
    });
  }

  approveReturn(eventId: number, approverId: number) {
    return this.http.put(`/api/event-items/return-approve/${eventId}`, null, {
      params: { approverId: approverId.toString() },
      responseType: 'text'
    });
  }

  // 🔹 Swap Items
  swapItems(request: {
    sourceEventItemId: number;
    targetItemId: number;
    targetEventId?: number;
    targetEventItemId?: number;
    moveToStore?: boolean;
    reason?: string;
    userId: number;
  }): Observable<string> {
    return this.http.post(environment.apiUrl + '/api/event-items/swap', request, { responseType: 'text' });
  }
}

/* ---------- Helper functions วันที่/เวลา ---------- */
export class DateTimeHelper {
  /** คืน YYYY-MM-DD (local) */
  static toLocalDate(date: Date): string {
    const y = date.getFullYear();
    const m = (date.getMonth() + 1).toString().padStart(2, '0');
    const d = date.getDate().toString().padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  /** คืน HH:mm:ss */
  static toLocalTimeSeconds(date: Date): string {
    const hh = date.getHours().toString().padStart(2, '0');
    const mm = date.getMinutes().toString().padStart(2, '0');
    const ss = date.getSeconds().toString().padStart(2, '0');
    return `${hh}:${mm}:${ss}`;
  }

  /** ถ้าผ่านมาเป็น HH:mm ให้เติม :00 เป็น HH:mm:ss */
  static ensureSeconds(t: string): string {
    if (!t) return t;
    return t.length === 5 ? `${t}:00` : t;
  }

  /** รวม date + time เป็น JS Date */
  static combineDateTime(dateStr: string, timeStr: string): Date {
    const t = this.ensureSeconds(timeStr);
    return new Date(`${dateStr}T${t}`);
  }

  /** แปลง Event จาก API เป็น object ที่พร้อมใช้บน UI */
  static parseEventForCalendar(event: CalendarEvent) {
    return {
      ...event,
      startDateTime: this.combineDateTime(event.startDate, event.startTime),
      endDateTime: this.combineDateTime(event.endDate, event.endTime),
    };
  }

}
