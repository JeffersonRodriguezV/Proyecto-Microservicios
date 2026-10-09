package com.microservicios.authservice.service;

import com.microservicios.authservice.Repository.EventoProcesadoRepository;
import com.microservicios.authservice.Repository.UsuarioRepository;
import com.microservicios.authservice.messaging.EventoPublisher;
import com.microservicios.authservice.model.EstadoCuenta;
import com.microservicios.authservice.model.EventoProcesado;
import com.microservicios.authservice.model.Rol;
import com.microservicios.authservice.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ciclo de vida de la cuenta dirigido por eventos.
 * Deduplicación: el id del evento se guarda en la misma transacción que sus efectos,
 * así un evento repetido se descarta y nunca queda "a medias".
 * Los eventos salientes se publican después del commit, para no anunciar cambios que no se guardaron.
 */
@Service
public class CicloVidaService {

    private static final Logger log = LoggerFactory.getLogger(CicloVidaService.class);

    private final EventoProcesadoRepository procesados;
    private final UsuarioRepository usuarios;
    private final JwtService jwt;
    private final EventoPublisher publisher;

    public CicloVidaService(EventoProcesadoRepository procesados, UsuarioRepository usuarios,
                            JwtService jwt, EventoPublisher publisher) {
        this.procesados = procesados;
        this.usuarios = usuarios;
        this.jwt = jwt;
        this.publisher = publisher;
    }

    @Transactional
    public void procesar(String eventoId, String type, Map<String, Object> data) {
        if (procesados.existsById(eventoId)) {
            log.info("Evento duplicado descartado: {} ({})", type, eventoId);
            return;
        }

        switch (type) {
            case "empleado.creado" -> alCrearEmpleado(data);
            case "empleado.retirado" -> alRetirarEmpleado(data);
            case "vacaciones.iniciadas" -> alIniciarVacaciones(data);
            case "vacaciones.finalizadas" -> alFinalizarVacaciones(data);
            default -> {
                log.debug("Evento ignorado: {}", type);
                return;
            }
        }

        procesados.save(new EventoProcesado(eventoId));
    }

    /**
     * empleado.creado (Catálogo 3.1): crea la cuenta inactiva, sin contraseña, con rol USER,
     * genera el token de activación y publica usuario.creado (Catálogo 3.4). Nunca viaja una contraseña.
     */
    private void alCrearEmpleado(Map<String, Object> data) {
        String empleadoId = texto(data, "empleadoId");
        String email = texto(data, "email");
        if (empleadoId == null || email == null) {
            log.warn("empleado.creado descartado: faltan empleadoId o email");
            return;
        }

        if (usuarios.findByEmpleadoId(empleadoId).isPresent() || usuarios.existsByEmail(email)) {
            log.warn("empleado.creado ignorado: ya existe una cuenta para {} / {}", empleadoId, email);
            return;
        }

        Usuario cuenta = new Usuario();
        cuenta.setEmpleadoId(empleadoId);
        cuenta.setEmail(email);
        cuenta.setPasswordHash(null);
        cuenta.setRol(Rol.USER);
        cuenta.setEstado(EstadoCuenta.PENDIENTE_ACTIVACION);
        cuenta.setFechaCreacion(LocalDateTime.now());
        usuarios.save(cuenta);

        JwtService.TokenEmitido token = jwt.generarTokenReset(empleadoId, email);

        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("empleadoId", empleadoId);
        salida.put("email", email);
        salida.put("tokenActivacion", token.token());
        salida.put("expiraEn", token.expiraEn().truncatedTo(ChronoUnit.SECONDS).toString());
        publicarTrasCommit("usuario.creado", salida);

        log.info("Cuenta creada en PENDIENTE_ACTIVACION para el empleado {}", empleadoId);
    }

    /**
     * empleado.retirado: baja PERMANENTE. Se aplica desde cualquier estado (activa, suspendida o pendiente),
     * y es definitiva: ningún evento posterior la reactiva.
     */
    private void alRetirarEmpleado(Map<String, Object> data) {
        Usuario cuenta = buscarCuenta(data, "empleado.retirado");
        if (cuenta == null) {
            return;
        }
        if (cuenta.getEstado() == EstadoCuenta.DESACTIVADA_PERMANENTE) {
            log.info("empleado.retirado ignorado: la cuenta {} ya estaba desactivada", cuenta.getEmpleadoId());
            return;
        }

        cuenta.setEstado(EstadoCuenta.DESACTIVADA_PERMANENTE);
        usuarios.save(cuenta);
        publicarTrasCommit("cuenta.desactivada", datosDesactivacion(cuenta, "RETIRO", true));
        log.info("Cuenta {} desactivada de forma PERMANENTE (retiro)", cuenta.getEmpleadoId());
    }

    /** vacaciones.iniciadas: suspensión TEMPORAL, solo si la cuenta está ACTIVA. */
    private void alIniciarVacaciones(Map<String, Object> data) {
        Usuario cuenta = buscarCuenta(data, "vacaciones.iniciadas");
        if (cuenta == null) {
            return;
        }
        if (cuenta.getEstado() != EstadoCuenta.ACTIVA) {
            log.info("vacaciones.iniciadas ignorado: la cuenta {} está en {}", cuenta.getEmpleadoId(), cuenta.getEstado());
            return;
        }

        cuenta.setEstado(EstadoCuenta.SUSPENDIDA_TEMPORAL);
        usuarios.save(cuenta);
        publicarTrasCommit("cuenta.desactivada", datosDesactivacion(cuenta, "VACACIONES", false));
        log.info("Cuenta {} SUSPENDIDA temporalmente (vacaciones)", cuenta.getEmpleadoId());
    }

    /**
     * vacaciones.finalizadas: reactiva SOLO si la cuenta sigue SUSPENDIDA_TEMPORAL.
     * Caso borde obligatorio: si el empleado fue retirado durante las vacaciones, la cuenta está
     * DESACTIVADA_PERMANENTE y NO se reactiva.
     */
    private void alFinalizarVacaciones(Map<String, Object> data) {
        Usuario cuenta = buscarCuenta(data, "vacaciones.finalizadas");
        if (cuenta == null) {
            return;
        }
        if (cuenta.getEstado() == EstadoCuenta.DESACTIVADA_PERMANENTE) {
            log.warn("vacaciones.finalizadas: la cuenta {} fue dada de baja durante las vacaciones; NO se reactiva",
                    cuenta.getEmpleadoId());
            return;
        }
        if (cuenta.getEstado() != EstadoCuenta.SUSPENDIDA_TEMPORAL) {
            log.info("vacaciones.finalizadas ignorado: la cuenta {} está en {}", cuenta.getEmpleadoId(), cuenta.getEstado());
            return;
        }

        cuenta.setEstado(EstadoCuenta.ACTIVA);
        usuarios.save(cuenta);

        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("empleadoId", cuenta.getEmpleadoId());
        salida.put("email", cuenta.getEmail());
        salida.put("motivo", "FIN_VACACIONES");
        publicarTrasCommit("cuenta.activada", salida);
        log.info("Cuenta {} REACTIVADA (fin de vacaciones)", cuenta.getEmpleadoId());
    }

    /** Busca la cuenta del empleado del evento; si no existe (p. ej. empleados anteriores a este servicio), se registra y se ignora. */
    private Usuario buscarCuenta(Map<String, Object> data, String evento) {
        String empleadoId = texto(data, "empleadoId");
        if (empleadoId == null) {
            log.warn("{} descartado: falta empleadoId", evento);
            return null;
        }
        return usuarios.findByEmpleadoId(empleadoId).orElseGet(() -> {
            log.warn("{} ignorado: no existe una cuenta para el empleado {}", evento, empleadoId);
            return null;
        });
    }

    private Map<String, Object> datosDesactivacion(Usuario cuenta, String motivo, boolean permanente) {
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("empleadoId", cuenta.getEmpleadoId());
        salida.put("email", cuenta.getEmail());
        salida.put("motivo", motivo);
        salida.put("permanente", permanente);
        return salida;
    }

    /** Lee un campo de texto del payload; devuelve null si falta o está vacío. */
    private String texto(Map<String, Object> data, String campo) {
        Object valor = data.get(campo);
        return (valor instanceof String s && !s.isBlank()) ? s : null;
    }

    /** Publica el evento cuando la transacción actual se confirma; si no hay transacción, de inmediato. */
    private void publicarTrasCommit(String type, Map<String, Object> data) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.publicar(type, data);
                }
            });
        } else {
            publisher.publicar(type, data);
        }
    }
}