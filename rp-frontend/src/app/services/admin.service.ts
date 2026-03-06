import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AdminService {

  private base = '/api/admin/users';

  constructor(private http: HttpClient) {}

  getPendingUsers(): Observable<any> {
    return this.http.get(`${this.base}/pending`);
  }

  approveUser(userId: number, role: string): Observable<any> {
    return this.http.put(`${this.base}/${userId}/approve`, { role });
  }

  rejectUser(userId: number): Observable<any> {
    return this.http.put(`${this.base}/${userId}/reject`, {});
  }
}
