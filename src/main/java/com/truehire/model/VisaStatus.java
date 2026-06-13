package com.truehire.model;

public enum VisaStatus {
    UPLOADED,            // Шаг 1: документы загружены
    PLATFORM_REVIEW,     // Шаг 2: проверка платформой
    EMBASSY_SUBMITTED,   // Шаг 3: подача в посольство Германии
    APPROVED             // Шаг 4: виза одобрена
}
