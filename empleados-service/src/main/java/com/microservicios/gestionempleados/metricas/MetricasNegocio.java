package com.microservicios.gestionempleados.metricas;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Métricas de NEGOCIO (no técnicas) del servicio de empleados, expuestas a Prometheus en
 * /actuator/prometheus y graficadas en el dashboard de Grafana:
 *
 *  - empleados_creados_total               empleados dados de alta con éxito
 *  - empleados_retirados_total             empleados dados de baja con éxito
 *  - empleados_operaciones_rechazadas_total{operacion}   operaciones rechazadas por una regla de negocio
 *
 * Se aísla en su propia clase (SRP) para que el servicio de dominio solo diga "qué pasó" y no
 * conozca Micrometer; los contadores se crean una vez y se reutilizan (Counter es thread-safe).
 */
@Component
public class MetricasNegocio {

    private final MeterRegistry registro;
    private final Counter creados;
    private final Counter retirados;

    public MetricasNegocio(MeterRegistry registro) {
        this.registro = registro;
        this.creados = Counter.builder("empleados.creados")
                .description("Empleados creados con éxito").register(registro);
        this.retirados = Counter.builder("empleados.retirados")
                .description("Empleados retirados con éxito").register(registro);
    }

    public void empleadoCreado() {
        creados.increment();
    }

    public void empleadoRetirado() {
        retirados.increment();
    }

    /** Una operación (crear, retirar...) fue rechazada por una regla de negocio (duplicado, inexistente...). */
    public void operacionRechazada(String operacion) {
        Counter.builder("empleados.operaciones.rechazadas")
                .description("Operaciones rechazadas por reglas de negocio")
                .tag("operacion", operacion)
                .register(registro)
                .increment();
    }
}
