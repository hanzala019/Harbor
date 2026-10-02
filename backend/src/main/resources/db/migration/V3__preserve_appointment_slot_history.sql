ALTER TABLE appointments ADD COLUMN original_slot_index INTEGER;
UPDATE appointments SET original_slot_index=slot_index WHERE slot_index IS NOT NULL;
