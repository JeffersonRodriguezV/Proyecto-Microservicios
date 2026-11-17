package com.microservicios.gestionempleados.repository;

/**
 * Resultado de la consulta agregada "empleados vigentes por departamento".
 * Es un record (inmutable) que Hibernate construye con la expresion JPQL
 * {@code select new ...ConteoPorDepartamento(...)}, asi la capa de metricas no
 * maneja arreglos {@code Object[]} sin tipo.
 *
 * @param departamentoId identificador del departamento (ej. "TI")
 * @param total          cantidad de empleados no retirados en ese departamento
 */
public record ConteoPorDepartamento(String departamentoId, long total) {
}
