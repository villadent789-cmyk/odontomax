-- La migración V21 sembró doctores derivadores y comisiones de ejemplo (pacientes inventados). En un sistema real no deben
-- existir: se retiran solo si siguen intactos, es decir, si nadie los usó después. Lo que se cargó con datos reales no se toca.

DELETE FROM comisiones_radiologicas
 WHERE id IN ('c0000001-0000-4000-8000-000000000001',
              'c0000002-0000-4000-8000-000000000002',
              'c0000003-0000-4000-8000-000000000003')
   AND registrado_por = 'Recepcionista Chilca'
   AND cita_id IS NULL;

DELETE FROM doctores_derivadores d
 WHERE d.id IN ('d0000001-0000-4000-8000-000000000001',
                'd0000002-0000-4000-8000-000000000002',
                'd0000003-0000-4000-8000-000000000003')
   AND NOT EXISTS (SELECT 1 FROM comisiones_radiologicas c WHERE c.doctor_derivador_id = d.id)
   AND NOT EXISTS (SELECT 1 FROM citas ci WHERE ci.doctor_derivador_id = d.id);
