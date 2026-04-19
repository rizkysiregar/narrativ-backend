-- V4__add_role_to_users.sql

-- step 1: add column as nullable first
ALTER TABLE users ADD COLUMN role VARCHAR(20);

-- step 2: fill existing rows with default value
UPDATE users SET role = 'USER' WHERE role IS NULL;

-- step 3: now safe to add NOT NULL constraint + check
ALTER TABLE users ALTER COLUMN role SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'));