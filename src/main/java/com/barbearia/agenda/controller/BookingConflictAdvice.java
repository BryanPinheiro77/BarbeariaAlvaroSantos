package com.barbearia.agenda.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;
import java.sql.SQLException;

@RestControllerAdvice
public class BookingConflictAdvice {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> conflict(DataIntegrityViolationException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23P01".equals(sql.getSQLState())) {
                return ResponseEntity.status(409).body("Horário já reservado. Escolha outro horário.");
            }
        }
        throw error;
    }
}
