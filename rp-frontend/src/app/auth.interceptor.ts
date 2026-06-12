import { HttpInterceptorFn } from '@angular/common/http';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {

  const token = localStorage.getItem('token');

  const isAuthApi = req.url.includes('/api/auth/');
  const badToken = !token || token === 'null' || token === 'undefined';

  // ไม่มี token → ไม่ต้องใส่ header
  if (isAuthApi || badToken) {
    return next(req);
  }

  const cloned = req.clone({
    setHeaders: { Authorization: `Bearer ${token}` }
  });

  return next(cloned).pipe(
    catchError((err) => {

      // 401 – Unauthorized → token หมดอายุ
      if (err.status === 401) {
        localStorage.removeItem('token');
        window.location.href = '/login';
      }

      // 403 – Forbidden → ไม่มีสิทธิ์เข้าหน้านี้
      if (err.status === 403) {
        window.location.href = '/login';
      }

      return throwError(() => err);
    })
  );
};
