/**
 * Puente entre la app «Plan Ing. Software» y esta hoja de Google.
 *
 * La app envía una foto de tu progreso (hitos, bloques, entrenos, tests, tiempo en pantalla)
 * y esta hoja la muestra en pestañas. También guarda un respaldo para poder restaurarlo desde la app.
 *
 * Pasos en LEEME.md. Lo único que debes cambiar es TOKEN.
 */

// Pon aquí una clave larga y única (por ejemplo 30 letras y números al azar).
// Es la misma que escribes en la app, en Ajustes → Google Sheets → Token.
const TOKEN = 'CAMBIA-ESTE-TOKEN-POR-UNO-LARGO';

const HOJA_RESPALDO = '_respaldo';
const TROZO = 40000; // una celda admite 50 000 caracteres; se parte el respaldo en trozos

function doPost(e) {
  try {
    const d = JSON.parse(e.postData.contents);
    if (TOKEN === 'CAMBIA-ESTE-TOKEN-POR-UNO-LARGO') {
      return salida({ ok: false, error: 'primero cambia TOKEN en el script' });
    }
    if (!d.token || d.token !== TOKEN) {
      return salida({ ok: false, error: 'token incorrecto' });
    }
    if (d.accion === 'restaurar') {
      return salida({ ok: true, respaldo: leerRespaldo() });
    }
    if (d.accion === 'enviar') {
      const cerrojo = LockService.getScriptLock();
      cerrojo.waitLock(20000);
      try {
        guardar(d);
      } finally {
        cerrojo.releaseLock();
      }
      return salida({ ok: true });
    }
    return salida({ ok: false, error: 'acción desconocida' });
  } catch (err) {
    return salida({ ok: false, error: String(err) });
  }
}

// Abrir la URL en el navegador solo sirve para comprobar que el script está publicado.
function doGet() {
  return salida({ ok: true, mensaje: 'El script está activo. La app le habla con POST.' });
}

function salida(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function guardar(d) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  Object.keys(d.tablas || {}).forEach(function (nombre) {
    const filas = d.tablas[nombre];
    if (!filas || !filas.length) return;
    const hoja = ss.getSheetByName(nombre) || ss.insertSheet(nombre);
    hoja.clearContents();
    const columnas = filas[0].length;
    hoja.getRange(1, 1, filas.length, columnas).setValues(filas);
    hoja.getRange(1, 1, 1, columnas).setFontWeight('bold');
    hoja.setFrozenRows(1);
  });
  escribirRespaldo(ss, d.respaldo || '', d.dispositivo || '');
}

function escribirRespaldo(ss, texto, dispositivo) {
  const hoja = ss.getSheetByName(HOJA_RESPALDO) || ss.insertSheet(HOJA_RESPALDO);
  hoja.clearContents();
  hoja.getRange(1, 1).setValue('Respaldo de la app (no editar) · ' + dispositivo + ' · ' + new Date().toISOString());
  const trozos = [];
  for (let i = 0; i < texto.length; i += TROZO) trozos.push([texto.substring(i, i + TROZO)]);
  if (trozos.length) {
    const rango = hoja.getRange(2, 1, trozos.length, 1);
    rango.setNumberFormat('@'); // texto plano: que Sheets no convierta un trozo en número
    rango.setValues(trozos);
  }
  hoja.hideSheet();
}

function leerRespaldo() {
  const hoja = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(HOJA_RESPALDO);
  if (!hoja || hoja.getLastRow() < 2) return '';
  return hoja.getRange(2, 1, hoja.getLastRow() - 1, 1).getValues()
    .map(function (f) { return String(f[0]); })
    .join('');
}
