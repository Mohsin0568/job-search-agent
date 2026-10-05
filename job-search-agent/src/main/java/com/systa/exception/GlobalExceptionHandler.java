package com.systa.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    // 401 makes the frontend end its local session and send the user back to sign in.
    @ExceptionHandler(UserSessionRevokedException.class)
    public ResponseEntity<ErrorResponse> handleUserSessionRevoked(final UserSessionRevokedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("your session is no longer valid, please sign in again"));
    }

    @ExceptionHandler(IdentityProviderUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleIdentityProviderUnavailable(final IdentityProviderUnavailableException ex) {
        LOGGER.error("Cognito user lookup failed", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("we couldn't verify your account details right now, please try again"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationFailure(final MethodArgumentNotValidException ex) {
        final String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .findFirst()
                .orElse("Request is invalid");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }
}
