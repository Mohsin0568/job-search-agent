package com.systa.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CandidateProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCandidateProfileNotFound(final CandidateProfileNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "your job search profile has not been set, please update your preferences to search for the job"));
    }

    @ExceptionHandler(CompanyPreferencesNotSetException.class)
    public ResponseEntity<ErrorResponse> handleCompanyPreferencesNotSet(final CompanyPreferencesNotSetException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "you have not set any company preferences, please update your preferences to search for the job"));
    }
}
