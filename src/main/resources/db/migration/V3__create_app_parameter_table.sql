-- V1__create_app_parameters_table.sql

--- 1. Create the table
CREATE TABLE app_parameters (
    id BIGSERIAL PRIMARY KEY,
    param_key VARCHAR(255) NOT NULL UNIQUE,
    param_value TEXT,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

---  2. Creating the index for faster lookups
CREATE INDEX idx_param_key ON app_parameters (param_key);


--- 3. Seed initial data
INSERT INTO app_parameters (param_key, param_value, description)
VALUES
    ('OTP.EXPIRED.TIME', '2', 'OTP Expired time in minutes')
ON CONFLICT (param_key) DO NOTHING;