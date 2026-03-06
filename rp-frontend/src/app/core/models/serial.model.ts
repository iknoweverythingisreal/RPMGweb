/* =======================================
   SERIAL MODELS (ใช้ทุกหน้าที่มี Serial)
   ======================================= */

/** ใช้แทน 1 serial unit */
export interface SerialItemUnitDTO {
  itemUnitId: number;
  serial: string;
  status: string;   // IN_STOCK / PICKED / OUT / DAMAGED / LOST
  note?: string;
}

/** สำหรับเรียก API checkout / return / damage */
export interface SerialActionRequest {
  eventItemId: number;
  unitIds: number[];
  note?: string;
}

/** ใช้ตอน link/pick serial */
export interface LinkUnitsRequest {
  unitIds: number[];
  note?: string;
}
