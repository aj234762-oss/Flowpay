package com.cfs.FlowPay.Common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException{

    public final HttpStatus status;


    public ApiException(HttpStatus status,String s) {
        super(s);
        this.status = status;
    }

    public static ApiException bad(String s){
        return new ApiException(HttpStatus.BAD_REQUEST,s);
    }

    public static ApiException conflict(String s){
        return new ApiException(HttpStatus.CONFLICT,s);
    }

    public static ApiException missing(){
        return new ApiException(HttpStatus.NOT_FOUND,"Resource not found");
    }
}
