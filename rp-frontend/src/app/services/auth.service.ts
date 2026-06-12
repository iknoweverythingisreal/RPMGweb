// src/app/services/auth.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, map, of } from 'rxjs';
import { UserService } from './user.service';
import { CurrentUser } from '../core/models/user.model';   // << ใช้ตัวนี้เท่านั้น!
import { environment } from 'src/environments/environment';

export interface LoginReq {
  email: string;
  password: string;
}

export interface LoginRes {
  token: string;
  userId: number;
  email: string;
  name: string;
  role: string;
  status: string;
  calendarColor?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly TOKEN_KEY = 'token';

  constructor(
    private http: HttpClient,
    private userService: UserService
  ) { }

  // =====================================================
  // LOGIN
  // =====================================================
  login(body: LoginReq): Observable<LoginRes> {
    return this.http.post<LoginRes>(environment.apiUrl + '/api/auth/login', body).pipe(
      tap(res => this.storeSession(res))
    );
  }

  // =====================================================
  // REGISTER
  // =====================================================
  register(body: any): Observable<any> {
    return this.http.post(environment.apiUrl + '/api/auth/register', body);
  }

  // =====================================================
  // STORE SESSION (Login Success)
  // =====================================================
  storeSession(res: LoginRes) {

    // 1) Save Token
    localStorage.setItem(this.TOKEN_KEY, res.token);

    // 2) Decode JWT
    const payload: any = JSON.parse(atob(res.token.split('.')[1]));

    // 3) Build User Object
    const user: CurrentUser = {
      id: res.userId,
      email: res.email,
      name: res.name,
      role: res.role,
      status: payload.status,
      isActive: payload.isActive,
      calendarColor: res.calendarColor
    };

    // 4) Save to LocalStorage
    localStorage.setItem("currentUser", JSON.stringify(user));
    localStorage.setItem("userId", String(user.id));
    localStorage.setItem("userName", user.name || user.email);
    localStorage.setItem("userEmail", user.email);
    if (user.calendarColor) localStorage.setItem("calendarColor", user.calendarColor);

    // 5) Push to UserService → Guard sees correct user immediately
    this.userService.setCurrentUser(user);
  }

  // =====================================================
  // TOKEN
  // =====================================================
  get token(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  logout() {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem("currentUser");
    this.userService.clearUser();
  }

  // =====================================================
  // LOAD USER FROM BACKEND (/me)
  // =====================================================
  me(): Observable<CurrentUser | null> {
    if (!this.token) return of(null);

    return this.http.get<any>(environment.apiUrl + '/api/auth/me').pipe(
      map(res => {
        const user: CurrentUser = {
          id: res.id,
          email: res.email,
          name: res.name,
          role: res.role,
          status: res.status,
          isActive: res.isActive,
          calendarColor: res.calendarColor
        };

        this.userService.setCurrentUser(user);
        return user;
      })
    );
  }

  // =====================================================
  // JWT Decode
  // =====================================================
  private decodeJwt(): any | null {
    if (!this.token) return null;
    try {
      return JSON.parse(atob(this.token.split('.')[1]));
    } catch {
      return null;
    }
  }

  getUserRole(): string | null {
    return this.decodeJwt()?.role ?? null;
  }
}
