package org.example.wuzi5.demos;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.NoHandlerFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseBody
    public String handle404Error() {
        return "页面未找到，请检查访问路径。";
    }
}