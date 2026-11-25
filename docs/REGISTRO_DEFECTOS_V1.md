# Registro inicial de defectos

| ID | Estado | Severidad | Descripción | Evidencia | Responsable |
|---|---|---|---|---|---|
| DEF-001 | Abierto | Media | Ruteo aún estima recorridos sin geocodificación ni tráfico real | README / `RuteoServiceImpl` | Equipo de backend |
| DEF-002 | Abierto | Media | Sesiones conversacionales de inventario son locales a una instancia | README | Equipo de backend |
| DEF-003 | Abierto | Alta | Estado externo de Jira no puede verificarse sin credenciales de proyecto | Pendiente de sincronización Jira | QA designado |
| DEF-004 | Abierto | Baja | El gateway de IA requiere URL y API key configuradas fuera del repositorio | `AiGatewayClient` | DevOps/arquitectura |

Los incidentes nuevos deben registrarse también en Jira con el mismo ID cuando exista acceso al proyecto.
