import { SerialItemUnitDTO } from '../models/serial.model';

export interface UnifiedAvailabilityDTO {
  itemId: number;
  itemName: string;
  uom: string;

  // ถ้าเป็น serial mode = true → ใช้ serials[]
  serialMode: boolean;

  // รายการ serial ทั้งหมดที่ available / allocated / shortage
  serials?: SerialItemUnitDTO[];

  // สำหรับ item แบบจำนวน (เช่น SQM)
  qty?: {
    total: number;
    allocated: number;
    available: number;
    shortage: number;
  };

  // summary รวม (ใช้เหมือนกันทั้ง serial/qty)
  total: number;
  allocated: number;
  available: number;
  shortage: number;
}
