package com.microservicios.authservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Registro de eventos ya procesados: base de la deduplicación (un evento repetido no se aplica dos veces). */
@Entity
@Table(name = "eventos_procesados")
public class EventoProcesado {

    @Id
    @Column(name = "id", length = 100)
    private String id;
    // Fecha y hora en que se procesó el evento
    @Column(name = "procesado_en", nullable = false)
    private LocalDateTime procesadoEn;

    // Constructor protegido para JPA
    protected EventoProcesado() {
    }

    // Constructor público para crear un nuevo registro de evento procesado
    public EventoProcesado(String id) {
        this.id = id;
        this.procesadoEn = LocalDateTime.now();
    }

    public String getId() { return id; }
    public LocalDateTime getProcesadoEn() { return procesadoEn; }
}