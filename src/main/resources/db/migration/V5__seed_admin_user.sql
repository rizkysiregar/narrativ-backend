-- V5__seed_admin_user.sql
INSERT INTO users (id, username, email, password, first_name, last_name, role, created_at)
VALUES (
           gen_random_uuid(),
           'superadmin',
           'feedzmore.helpdesk@gmail.com',
           'siregar123',
           'Super',
           'Admin',
           'ADMIN',
           NOW()
       );