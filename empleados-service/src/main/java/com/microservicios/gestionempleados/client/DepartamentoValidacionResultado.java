package com.microservicios.gestionempleados.client;

/**
 * Resultado de consultar la existencia de un departamento.
 *
 * Se modela como tres estados en vez de un booleano porque "no se pudo
 * determinar" (servicio caído) y "confirmado que no existe" (404 real)
 * requieren manejo completamente distinto: el primero acepta el
 * empleado como pendiente de validación, el segundo lo rechaza con 400.
 */
public enum DepartamentoValidacionResultado {
    EXISTE,
    NO_EXISTE,
    INDETERMINADO
}