package com.releasy.domain.app;

/**
 * Plan contratado para una aplicación. Determina el tamaño del servicio ECS Fargate
 * (la traducción a CPU, memoria y número de tareas se hace en el pipeline de despliegue).
 */
public enum AppPlan {
    /** 0,25 vCPU, 512 MiB, 1 tarea. */
    BASIC,
    /** 0,5 vCPU, 1 GiB, 2 tareas. */
    STANDARD
}
