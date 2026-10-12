package com.systa.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(InvalidResumeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResume(final InvalidResumeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(final MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ErrorResponse("resume file is too large - the limit is 5MB"));
    }

    @ExceptionHandler(ResumeUploadConflictException.class)
    public ResponseEntity<ErrorResponse> handleResumeUploadConflict(final ResumeUploadConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("another resume upload was in progress, please try again"));
    }

    @ExceptionHandler(ResumeStorageException.class)
    public ResponseEntity<ErrorResponse> handleResumeStorageFailure(final ResumeStorageException ex) {
        LOGGER.error("Resume storage failed", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("we couldn't reach resume storage right now, please try again"));
    }
}
