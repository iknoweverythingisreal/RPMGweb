// items.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';

export interface UsageInfo {
  eventName: string;
  eventId: number;
  quantity: number;
  startDate: string;
  endDate: string;
  status: string;
}

export interface ItemAvailability {
  total: number;
  allocated: number;
  available: number;
  shortage: number;
  usageDetails?: UsageInfo[];
}

export interface Item {
  id: number;
  name: string;
  category: string;
  availableQuantity: number;
  totalQuantity: number;
  brand?: string;
  model?: string;
  imageUrl?: string;
  location: string;
  price?: number;
  status?: string;
  remark?: string;
  serialControl?: boolean;
  serial?: string;
  description?: string;
  uom?: string;
  itemName?: string;
  title?: string;
  spec?: any; // JSONB metadata including repair_qty
  originalItems?: Item[];
}

@Injectable({
  providedIn: 'root'
})
export class ItemsService {
  private apiUrl = environment.apiUrl + '/api/items';

  constructor(private http: HttpClient) { }

  getItems(): Observable<Item[]> {
    return this.http.get<Item[]>(this.apiUrl);
  }

  getItemById(id: number): Observable<Item> {
    return this.http.get<Item>(`${this.apiUrl}/${id}`);
  }

  searchItems(query: string): Observable<Item[]> {
    return this.http.get<Item[]>(`${this.apiUrl}/search`, { params: { q: query } });
  }

  getItemsByCategory(category: string): Observable<Item[]> {
    return this.http.get<Item[]>(`${this.apiUrl}/category/${category}`);
  }

  getAvailability(itemId: number, eventId: number, startDate: string, endDate: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/${itemId}/availability`, {
      params: { eventId, startDate, endDate }
    });
  }

  getBulkAvailability(itemIds: number[], eventId: number, startDate: string, endDate: string): Observable<any[]> {
    return this.http.post<any[]>(`${this.apiUrl}/availability/bulk`, {
      itemIds,
      eventId,
      startDate,
      endDate
    });
  }

  createItem(item: Partial<Item>): Observable<Item> {
    return this.http.post<Item>(this.apiUrl, item);
  }

  deleteItem(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getServiceItems(): Observable<Item[]> {
    return this.searchItems('External Rental');
  }

  // === REPAIR MANAGEMENT ===

  markItemForRepair(itemId: number, quantity: number): Observable<any> {
    return this.http.post(`${environment.apiUrl}/api/repair/items/${itemId}/mark`, { quantity });
  }

  releaseItemFromRepair(itemId: number, quantity: number): Observable<any> {
    return this.http.post(`${environment.apiUrl}/api/repair/items/${itemId}/release`, { quantity });
  }

  /** Permanently scrap broken units: removes them from repair AND total stock. */
  writeOffItem(itemId: number, quantity: number): Observable<any> {
    return this.http.post(`${environment.apiUrl}/api/repair/items/${itemId}/writeoff`, { quantity });
  }

  updateUnitStatus(unitId: number, status: string): Observable<any> {
    return this.http.put(`${environment.apiUrl}/api/repair/units/${unitId}/status`, {}, {
      params: { status }
    });
  }
}