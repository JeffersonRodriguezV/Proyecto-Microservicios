package com.microservicios.gestionempleados.metricas;

import com.microservicios.gestionempleados.model.enume.EstadoEmpleado;
import com.microservicios.gestionempleados.repository.ConteoPorDepartamento;
import com.microservicios.gestionempleados.repository.EmpleadoRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metricas BASADAS EN LA BASE DE DATOS de empleados-service (Prometheus: /actuator/prometheus).
 * Describen el CONTENIDO y la SALUD de la base, no el trafico HTTP:
 *
 *  - empleados_bd_disponible                    1 si la ultima lectura a MySQL funciono, 0 si fallo
 *  - empleados_bd_total{estado}                 empleados por estado (ACTIVO, EN_VACACIONES, RETIRADO)
 *  - empleados_bd_por_departamento{departamento} empleados vigentes (no retirados) por departamento
 *  - empleados_bd_sin_validar                   empleados con departamento aun sin confirmar
 *
 * Decisiones de diseno:
 *  - Se leen en segundo plano cada 15 s ({@link #refrescar()}) y NO en cada scrape: Prometheus puede
 *    consultar /actuator/prometheus muchas veces y no debe disparar consultas a la base.
 *  - Si la base cae, el refresco captura el error, pone empleados_bd_disponible=0 y conserva los ultimos
 *    totales (el endpoint de metricas nunca falla por culpa de la base). Asi una alerta puede detectar
 *    "la base esta caida" aunque el proceso Java siga vivo (algo que la metrica 'up' no ve).
 *  - Clase propia (SRP), separada de MetricasNegocio: aquella cuenta eventos (contadores); esta mide estado.
 */
@Component
public class MetricasBaseDatos {

    private static final Logger log = LoggerFactory.getLogger(MetricasBaseDatos.class);

    private final EmpleadoRepository repositorio;
    private final AtomicInteger disponible = new AtomicInteger(0);   // 0 hasta la primera lectura correcta
    private final AtomicLong sinValidar = new AtomicLong();
    private final Map<EstadoEmpleado, AtomicLong> porEstado = new EnumMap<>(EstadoEmpleado.class);
    private final MultiGauge porDepartamento;

    public MetricasBaseDatos(EmpleadoRepository repositorio, MeterRegistry registro) {
        this.repositorio = repositorio;

        Gauge.builder("empleados.bd.disponible", disponible, AtomicInteger::get)
                .description("1 si la base de datos de empleados responde, 0 si no")
                .register(registro);

        for (EstadoEmpleado estado : EstadoEmpleado.values()) {
            AtomicLong valor = new AtomicLong();
            porEstado.put(estado, valor);
            Gauge.builder("empleados.bd.total", valor, AtomicLong::get)
                    .description("Empleados registrados en la base de datos, por estado")
                    .tag("estado", estado.name())
                    .register(registro);
        }

        Gauge.builder("empleados.bd.sin_validar", sinValidar, AtomicLong::get)
                .description("Empleados cuyo departamento aun no fue confirmado por departamentos-service")
                .register(registro);

        this.porDepartamento = MultiGauge.builder("empleados.bd.por_departamento")
                .description("Empleados vigentes (no retirados) por departamento")
                .register(registro);
    }

    /**
     * Lee la base y actualiza todas las metricas. Es publico para poder probarlo sin esperar al planificador.
     * El retraso inicial da tiempo a que la base y el esquema esten listos al arrancar.
     */
    @Scheduled(initialDelayString = "${metricas.bd.retraso-inicial-ms:5000}",
            fixedDelayString = "${metricas.bd.intervalo-ms:15000}")
    public void refrescar() {
        try {
            for (EstadoEmpleado estado : EstadoEmpleado.values()) {
                porEstado.get(estado).set(repositorio.countByEstado(estado));
            }
            sinValidar.set(repositorio.countByDepartamentoValidadoFalse());
            actualizarPorDepartamento(repositorio.contarVigentesPorDepartamento());
            disponible.set(1);
        } catch (RuntimeException e) {
            disponible.set(0);
            log.warn("No se pudieron leer las metricas de la base de datos de empleados: {}", e.getMessage());
        }
    }

    /** Reemplaza todas las filas: un departamento que ya no tiene empleados vigentes desaparece de la serie. */
    private void actualizarPorDepartamento(List<ConteoPorDepartamento> conteos) {
        List<MultiGauge.Row<?>> filas = conteos.stream()
                .<MultiGauge.Row<?>>map(c -> MultiGauge.Row.of(Tags.of("departamento", c.departamentoId()), c.total()))
                .toList();
        porDepartamento.register(filas, true);
    }
}
