--HU-06: validar integridad de datos existentes en duracion_minutos
--corrige los servicios con duración nula, cero, negativa o superior a 480
--asignando un valor por defecto de 30 minutos

UPDATE servicios
SET    duracion_minutos = 30,
       updated_at       = now()
WHERE  duracion_minutos IS NULL
   OR  duracion_minutos <= 0
   OR  duracion_minutos > 480;