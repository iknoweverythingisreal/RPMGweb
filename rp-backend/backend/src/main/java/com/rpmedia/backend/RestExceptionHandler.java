package com.rpmedia.backend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@ControllerAdvice
public class RestExceptionHandler {

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<?> handleIllegalState(IllegalStateException ex) {
    System.err.println("!!! [RestExceptionHandler] IllegalStateException caught: " + ex.getMessage());
    ex.printStackTrace();
    System.err.flush();
    String msg = ex.getMessage() == null ? "" : ex.getMessage();

    if (msg.startsWith("QUANTITY_EXCEEDS_AVAILABLE:")) {
      String available = msg.substring("QUANTITY_EXCEEDS_AVAILABLE:".length());
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("error", "QUANTITY_EXCEEDS_AVAILABLE", "available", available));
    }
    if ("UNIT_PRICE_REQUIRED".equals(msg)) {
      return ResponseEntity.badRequest().body(Map.of("error", "UNIT_PRICE_REQUIRED"));
    }
    if ("REQUESTED_QTY_REQUIRED".equals(msg)) {
      return ResponseEntity.badRequest().body(Map.of("error", "REQUESTED_QTY_REQUIRED"));
    }
    return ResponseEntity.badRequest().body(Map.of("error", msg));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> handleGenericException(Exception ex) {
    System.err.println(
        "!!! [RestExceptionHandler] Generic Exception caught: " + ex.getClass().getName() + " - " + ex.getMessage());
    ex.printStackTrace();
    System.err.flush();
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("error", "Internal Server Error", "message", ex.getMessage()));
  }
}
