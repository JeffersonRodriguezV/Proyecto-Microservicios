package com.microservicios.authservice.Repository;

import com.microservicios.authservice.model.EventoProcesado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, String> {
}