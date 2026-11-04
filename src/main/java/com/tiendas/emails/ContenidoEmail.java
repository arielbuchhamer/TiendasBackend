package com.tiendas.emails;

/** Email ya armado, listo para encolar: se envía exactamente esto. */
record ContenidoEmail(String asunto, String html, String texto) {
}
