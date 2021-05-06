package com.dlb.dlb.advice;


import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * This is the exception handler for the whole application, it will be invoked when there is a runtime exception.
 */
@ControllerAdvice
public class CustomExceptionHandler {
    @ExceptionHandler(Exception.class)
    @ResponseBody
    String handle(Throwable e) {
        return "Internal Error";
    }
}
