package com.college.events.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ModelAndView handleAccessDenied(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", 403); mav.addObject("error", "Access denied");
        mav.addObject("message", "This account cannot access the requested resource.");
        mav.addObject("timestamp", LocalDateTime.now()); mav.addObject("path", "");
        return mav;
    }
    @ExceptionHandler(com.college.events.exception.ProposalStateConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ModelAndView handleStateConflict(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", 409); mav.addObject("error", "State changed");
        mav.addObject("message", "This action is not available in the current state. Reload the page before continuing.");
        mav.addObject("timestamp", LocalDateTime.now()); mav.addObject("path", "");
        return mav;
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleRuntimeException(RuntimeException ex) {
        log.error("Unhandled application failure ({})", ex.getClass().getSimpleName());
        
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", 500);
        mav.addObject("error", "Internal Server Error");
        mav.addObject("message", "The request could not be completed. Please contact support if it persists.");
        mav.addObject("timestamp", LocalDateTime.now());
        mav.addObject("path", "");
        return mav;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid application request");
        
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", 400);
        mav.addObject("error", "Bad Request");
        mav.addObject("message", "The request contains invalid values.");
        mav.addObject("timestamp", LocalDateTime.now());
        return mav;
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView handleNoHandlerFound(NoHandlerFoundException ex) {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", 404);
        mav.addObject("error", "Page Not Found");
        mav.addObject("message", "The page you're looking for doesn't exist.");
        mav.addObject("timestamp", LocalDateTime.now());
        mav.addObject("path", ex.getRequestURL());
        return mav;
    }
}
