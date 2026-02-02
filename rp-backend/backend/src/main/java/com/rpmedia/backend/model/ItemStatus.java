package com.rpmedia.backend.model;

public enum ItemStatus {
    REQUESTED, // Manager เพิ่มเข้ารายการ
    ALLOCATED, // ระบบ/Tech allocate ของให้
    READY, // Tech เตรียมเสร็จ
    IN_USE, // Event เริ่มใช้งาน
    RETURNED, // ของคืนแล้ว
    DAMAGED, // เสียหาย
    LOST, // หาย
    RENT_EXTERNAL, // ของเช่าจากภายนอก
    TRANSFER, // โอนมาจาก event อื่น
    CANCELLED,
    DRAFT,
    CONFIRMED,
    CHECKED,
    REJECTED,
    PENDING,
    HOLD,
    AVAILABLE,
    PENDING_RENT,
    RETURN_REQUESTED, // Return flow status
}