package com.releasy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada de la API de ReleEasy Cloud.
 *
 * <p>Capas:
 * <ul>
 *   <li>{@code domain}: entidades, reglas de estado y contratos de repositorio, sin dependencias;</li>
 *   <li>{@code application}: casos de uso y puertos hacia AWS (interfaces);</li>
 *   <li>{@code infrastructure}: implementaciones mock/real de puertos y repositorios, y configuración;</li>
 *   <li>{@code web}: controladores REST, DTOs y seguridad.</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ReleasyApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReleasyApplication.class, args);
    }
}
