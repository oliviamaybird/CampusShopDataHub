package de.campusshop.cw1.controller;

import de.campusshop.cw1.dto.ErrorResponse;
import de.campusshop.cw1.exception.DuplicateOrderException;
import de.campusshop.cw1.exception.OrderNotFoundException;
import de.campusshop.cw1.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps exceptions to stable {@link ErrorResponse} bodies and the correct HTTP status codes
 * (Week 4: {@code @ControllerAdvice} / {@code @ExceptionHandler}).
 *
 * <p>TODO (student): add {@code @ExceptionHandler} methods that map:
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} and {@link HttpMessageNotReadableException} → {@code 400};</li>
 *   <li>{@link OrderNotFoundException} → {@code 404};</li>
 *   <li>{@link DuplicateOrderException} → {@code 409};</li>
 *   <li>{@link StorageException} / any other exception → {@code 500}.</li>
 * </ul>
 * Each handler should return a stable {@link ErrorResponse} (code, message, timestamp).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class) // called whenever OrderNotFoundException is thrown
    public ResponseEntity<ErrorResponse> handleNotFound(OrderNotFoundException ex) { // handleNotFound method returns HTTP response whose body is ErrorResponse object
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("ORDER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleArgNotValid(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_FAILED", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handlemalformedJSON(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_FAILED", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateOrderException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DUPLICATE_ORDER", ex.getMessage()));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorageError(StorageException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("STORAGE_ERROR", ex.getMessage()));
    }

    // TODO: add handlers for validation (400), malformed JSON (400), duplicate (409) and
    //       unexpected/storage failures (500).
}
