# Limpieza profunda semanal KALI

Transcripción de las X de la fotografía proporcionada. Se conservan los números originales y se omiten las filas vacías. Semana de lunes a domingo, conforme al período del reporte existente. Cada tarea semanal puede completarse una vez durante su semana; no se repite cada día.

| Área | Con foto | Para marcar | Total |
|---|---:|---:|---:|
| Cocina | 7 | 6 | 13 |
| Salón | 4 | 5 | 9 |
| Copería | 3 | 0 | 3 |
| Barra | 5 | 1 | 6 |
| **Total** | **19** | **12** | **31** |

| N.º | Área | Tarea | Confirmación |
|---:|---|---|---|
| 1 | COCINA | Campana y filtros | Foto |
| 2 | COCINA | Planchas, detrás de plancha y correas de gas | Foto |
| 3 | COCINA | Freidoras, por todas partes y correas de gas | Foto |
| 4 | COCINA | Refrigeradores, arriba, superficies y por dentro | Marcar |
| 5 | COCINA | Limpiar trampa del suelo y botar basura en bolsas | Foto |
| 6 | COCINA | Horno Bakerlux, por dentro y muro de atrás | Foto |
| 7 | COCINA | Estanterías y repisas | Marcar |
| 8 | COCINA | Mesones inox y lavamanos | Marcar |
| 9 | COCINA | Desagües / Sifones / Trampas Grasa | Marcar |
| 10 | COCINA | Muros, de arriba hacia abajo | Marcar |
| 11 | COCINA | Basurero entrada | Foto |
| 12 | COCINA | Limpiar el suelo al finalizar la limpieza profunda | Marcar |
| 13 | COCINA | Limpiar y ordenar bodega | Foto |
| 14 | SALON | Limpiar mesas y sillas, revisar que no tenga nada pegado por abajo y limpiar | Marcar |
| 15 | SALON | Mover sillones, limpiar debajo y tapiz | Marcar |
| 16 | SALON | Limpiar y ordenar mueble de estación de servicio terraza | Foto |
| 17 | SALON | Limpiar y repasar bandejas, ramequines, cubiertos, QR y todas las herramientas de Salón | Marcar |
| 18 | SALON | Limpiar vidrio terraza, ventana y puerta de entrada | Marcar |
| 19 | SALON | Limpiar y ordenar mueble de estación de servicio entrada | Foto |
| 20 | SALON | Limpiar luminaria de perritos | Foto |
| 21 | SALON | Limpiar y ordenar bodega | Foto |
| 22 | SALON | Limpiar suelo al finalizar la limpieza profunda | Marcar |
| 25 | COPERIA | Desagües / Sifones / Trampas Grasa, botar grasa en bolsas de basura | Foto |
| 26 | COPERIA | Después del lavado de la lavavajilla, pasar un trapo limpio con agua por filtro y todas partes | Foto |
| 27 | COPERIA | Limpiar trampa del suelo y botar basura en bolsas | Foto |
| 33 | BARRA | Mover y limpiar debajo de los barriles, mangueras y dejar ordenado sector máquina de bebidas | Foto |
| 34 | BARRA | Ordenar bajo lavamanos y dejar limpio | Foto |
| 35 | BARRA | Ordenar y limpiar mesón inox por debajo y por atrás | Foto |
| 36 | BARRA | Ordenar y limpiar mueble bajo caja | Foto |
| 37 | BARRA | Limpiar basurero, sacar la basura y limpiar basurero | Foto |
| 38 | BARRA | Limpiar muros y suelo al finalizar la limpieza profunda | Marcar |

## Orden para aplicar

1. Ejecutar `migrations/22_checkin_frecuencia_semanal.sql` en Oracle como script completo. Solo prepara columnas.
2. Hacer manualmente commit/push de los archivos de esta entrega, excluyendo application-local.properties, y esperar a que Render publique la versión semanal.
3. Ejecutar `migrations/23_limpieza_profunda_semanal_kali.sql` como script completo, después de confirmar el despliegue. No repetir 20 ni 21.
4. Comprobar los conteos finales (31). Entrar a Check-in: las tareas semanales se mantienen visibles y conservan su estado durante toda la semana.
5. Completar con el jefe correspondiente. Los pendientes se mantienen hasta el domingo; la semana siguiente genera nuevas instancias. Las evidencias anteriores se conservan.

No se aplicaron estos scripts a Oracle desde el agente ni se hicieron commits, push o despliegues. La firma del supervisor de la planilla queda representada por la cuenta, nombre y hora del jefe que confirma cada tarea; no se añadió una firma dibujada.
