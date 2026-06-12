// src/app/services/user.service.ts
import { Injectable } from '@angular/core';
import { jwtDecode } from 'jwt-decode';
import { BehaviorSubject, map } from 'rxjs';
import { CurrentUser } from '../core/models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {

  private currentUser$ = new BehaviorSubject<CurrentUser | null>(null);

  constructor() {
    this.loadUserFromToken();
  }

  /** โหลด user จาก token เมื่อ refresh หน้า */
  loadUserFromToken(): CurrentUser | null {
    const token = localStorage.getItem('token');
    if (!token) {
      this.currentUser$.next(null);
      return null;
    }

    try {
      const decoded: any = jwtDecode(token);
      const user: CurrentUser = {
        id: decoded.userId,
        email: decoded.sub,
        name: decoded.name || '',
        role: decoded.role,
        status: decoded.status,
        isActive: decoded.isActive,
        calendarColor: decoded.calendarColor,
        teamId: decoded.teamId,
        departmentId: decoded.departmentId
      };

      this.currentUser$.next(user);
      return user;

    } catch (e) {
      console.error("Invalid token:", e);
      this.currentUser$.next(null);
      return null;
    }
  }

  /** set user หลัง login */
  setCurrentUser(user: CurrentUser) {
    this.currentUser$.next(user);
  }

  /** ดึงค่า user ล่าสุด (ใช้ใน Guard) */
  get currentUser() {
    return this.currentUser$.value;
  }

  /** observable ใช้ใน UI */
  get user$() {
    return this.currentUser$.asObservable();
  }

  /** logout */
  clearUser() {
    this.currentUser$.next(null);
    localStorage.clear();
  }

  // ================================
  // ROLE HELPERS (Reactive)
  // ================================
  get isAdmin$() {
    return this.currentUser$.pipe(map(u => u?.role === 'ADMIN'));
  }

  get isManager$() {
    return this.currentUser$.pipe(map(u => u?.role === 'MANAGER' || u?.role === 'ADMIN' || u?.role === 'TECH_LEAD'));
  }

  get isTechLead$() {
    return this.currentUser$.pipe(map(u => u?.role === 'TECH_LEAD'));
  }

  get isTechnical$() {
    return this.currentUser$.pipe(map(u => u?.role === 'TECHNICAL' || u?.role === 'TECH_LEAD'));
  }

  get canManageUsers$() {
    return this.currentUser$.pipe(map(u => u?.role === 'ADMIN' || u?.role === 'TECH_LEAD'));
  }

  // ================================
  // ROLE HELPERS (Sync - for Guards/Interceptors)
  // ================================
  get isAdmin() {
    return this.currentUser?.role === 'ADMIN';
  }

  get isManager() {
    const r = this.currentUser?.role;
    return r === 'MANAGER' || r === 'ADMIN' || r === 'TECH_LEAD';
  }

  get isTechLead() {
    return this.currentUser?.role === 'TECH_LEAD';
  }

  get isTechnical() {
    const r = this.currentUser?.role;
    return r === 'TECHNICAL' || r === 'TECH_LEAD';
  }

  get canManageUsers() {
    const r = this.currentUser?.role;
    return r === 'ADMIN' || r === 'TECH_LEAD';
  }
}
