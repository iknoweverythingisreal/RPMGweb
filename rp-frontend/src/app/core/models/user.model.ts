export interface CurrentUser {
  id: number;
  email: string;
  name: string;
  role: string;        // BACKEND ส่งเป็น string เช่น "ADMIN"
  status: string;      // ACTIVE / PENDING / REJECTED
  isActive: boolean;   // true / false

  calendarColor?: string;
  teamId?: number;
  departmentId?: number;
}
