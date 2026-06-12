-- แปลง items.status เป็น int
ALTER TABLE items 
    ALTER COLUMN status TYPE int USING 
    CASE 
        WHEN status = 'ACTIVE' THEN 0
        WHEN status = 'INACTIVE' THEN 9
        ELSE 0
    END;

-- แปลง item_units.status เป็น int
ALTER TABLE item_units 
    ALTER COLUMN status TYPE int USING 
    CASE 
        WHEN status = 'AVAILABLE' THEN 0
        WHEN status = 'IN_USE' THEN 1
        WHEN status = 'BROKEN' THEN 2
        WHEN status = 'UNDER_REPAIR' THEN 3
        WHEN status = 'LOST' THEN 4
        ELSE 0
    END;
