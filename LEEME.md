# Conectar la app con Google Sheets

La app envía tu progreso a una hoja de Google. Si la hoja ya tiene tu Excel, súbelo a Google Drive
y ábrelo con «Hojas de cálculo de Google» (Archivo → Guardar como hoja de cálculo de Google).
También funciona con una hoja nueva y vacía.

La app **escribe** estas pestañas (las crea ella sola; no pongas nada tuyo en ellas porque se reescriben):

| Pestaña | Qué trae |
|---|---|
| Resumen | Semana, horas hechas/esperadas, avance, estado, racha |
| Hitos | Cada hito con horas, si está hecho y la fecha |
| Bloques | Los bloques de los últimos 60 días y si los marcaste |
| Entreno | Las 12 semanas × 5 sesiones |
| Tests | Tus pruebas de rendimiento |
| Foco | Minutos en pantalla de los últimos 7 días |

Tus propias pestañas (gráficos, fórmulas, resúmenes) se respetan: puedes hacer gráficos que lean de las anteriores.

## Pasos (5 minutos, una sola vez)

1. Abre tu hoja de Google → **Extensiones → Apps Script**.
2. Borra lo que haya y pega todo el contenido de `Codigo.gs`.
3. Cambia `TOKEN` por una clave larga que inventes (ejemplo: `h7Kq2xP9mT4vB8nL1zR6cD3wY5sA0f`). Guarda con el disquete.
4. Pulsa **Implementar → Nueva implementación → ⚙️ Aplicación web**:
   - *Ejecutar como*: **Yo**
   - *Quién tiene acceso*: **Cualquier persona**
   - Pulsa **Implementar**, acepta los permisos (Google avisa de que la app no está verificada: «Avanzado → Ir a…», es tu propio script).
5. Copia la **URL de la aplicación web** (termina en `/exec`).
6. En la app: **Ajustes → Google Sheets**. Pega la URL y el mismo token. Pulsa **Enviar ahora**.

Debe decir «Enviado · …» y verás las pestañas nuevas en tu hoja.

## Cómo funciona

- **Automático:** unos 45 segundos después de marcar algo, y cada 6 horas con internet. Se puede apagar en Ajustes.
- **Un solo sentido:** la app es la fuente de verdad. Lo que edites a mano en las pestañas que escribe la app se pierde en el siguiente envío.
- **Restaurar de la hoja:** trae el último respaldo guardado en la pestaña oculta `_respaldo`. Sirve al cambiar de teléfono. Pide contraseña o retos porque reemplaza tu progreso actual. No incluye tu contraseña.

## Seguridad

- «Cualquier persona» significa que cualquiera que tenga la URL **y** el token puede escribir. Usa un token largo y no compartas la URL.
- La URL y el token se guardan solo en tu teléfono, nunca en el código.
- Si sospechas que se filtraron: cambia `TOKEN`, guarda y haz **Implementar → Administrar implementaciones → ✏️ → Nueva versión**. Actualiza el token en la app.

## Si cambias el script

Cada cambio de `Codigo.gs` necesita **Administrar implementaciones → ✏️ → Versión: nueva versión → Implementar**. La URL no cambia.

## Problemas comunes

| Mensaje en la app | Causa |
|---|---|
| «La respuesta no es la esperada…» | La implementación no está como «Cualquier persona», o la URL no termina en `/exec` |
| «token incorrecto» | El token de la app no es igual al del script (cuidado con espacios) |
| «primero cambia TOKEN en el script» | Dejaste el token de ejemplo |
| «Sin conexión o error de red» | No hay internet; se reintenta solo |
