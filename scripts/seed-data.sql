-- =============================================================================
-- seed-data.sql: datos de ejemplo para departamentos-service (PostgreSQL).
-- Uso (con el sistema arriba):
--   docker compose exec -T db-departamentos psql -U <usuario> -d <base> < scripts/seed-data.sql
-- Es idempotente: puede ejecutarse varias veces.
--
-- Los EMPLEADOS no se siembran por SQL a propósito: deben crearse por la API
-- (POST /empleados) para que se publique el evento empleado.creado y así se generen
-- su perfil, su cuenta de acceso y su notificación de bienvenida. Insertarlos
-- directamente dejaría el sistema inconsistente. El usuario administrador y el
-- usuario de prueba los crea auth-service al arrancar (ADMIN_* / SEED_USER_* del .env).
-- =============================================================================
INSERT INTO departamentos (id, nombre, descripcion) VALUES
  ('SIS', 'Sistemas',        'Desarrollo y soporte de plataformas de software'),
  ('RH',  'Recursos Humanos','Gestión del talento y bienestar de los empleados'),
  ('FIN', 'Finanzas',        'Contabilidad, tesorería y presupuesto'),
  ('OPS', 'Operaciones',     'Logística y operación diaria')
ON CONFLICT (id) DO NOTHING;
