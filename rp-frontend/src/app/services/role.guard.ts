import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { UserService } from '../services/user.service';

export const roleGuard: CanMatchFn = (route) => {
  const router = inject(Router);
  const userService = inject(UserService);
  const user = userService.currentUser;

  if (!user) {
    router.navigate(['/login']);
    return false;
  }

  const allowedRoles = (route.data?.['roles'] as string[] | undefined)?.map(r => r.toUpperCase());
  const userRole = (user.role || '').toUpperCase();

  // ✅ ใช้ userRole ที่เป็น uppercase ในการตรวจ
  if (allowedRoles && !allowedRoles.includes(userRole)) {
    router.navigate(['/calendar']);
    return false;
  }

  return true;
};
