import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { UserService } from './services/user.service';

export const authGuard: CanMatchFn = () => {

  const router = inject(Router);
  const userService = inject(UserService);

  let user = userService.currentUser;

  // 👉 ถ้ายังไม่มี user ลองโหลดจาก token
  if (!user) {
    user = userService.loadUserFromToken();
  }

  // ❌ ไม่มี user เลย → เตะไป login
  if (!user) {
    router.navigate(['/login']);
    return false;
  }

  // 👉 ADMIN เข้าทุกหน้าไม่สน status
  if (user.role === 'ADMIN') {
    return true;
  }

  // ❌ บล็อกทุก role ที่ไม่ ACTIVE
  if (user.status !== 'ACTIVE') {
    router.navigate(['/login']);
    return false;
  }

  return true;
};
