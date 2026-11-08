-- param_value was UNIQUE, which means two parameters could never hold the same value (two flags that are both
-- "true"). Only param_key identifies a parameter. The constraint name differs per database (Hibernate generated
-- it on existing ones), so it is looked up by its column.
DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT con.conname
        FROM pg_constraint con
                 JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
        WHERE con.conrelid = 'app_parameters'::regclass
          AND con.contype = 'u'
          AND att.attname = 'param_value'
          AND array_length(con.conkey, 1) = 1
    LOOP
        EXECUTE format('ALTER TABLE app_parameters DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END
$$;
