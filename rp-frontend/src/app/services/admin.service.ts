import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AdminService {

  private base = environment.apiUrl + '/api/admin/users';

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
