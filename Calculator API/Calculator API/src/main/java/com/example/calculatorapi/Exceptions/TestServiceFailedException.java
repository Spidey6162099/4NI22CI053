package com.example.calculatorapi.Exceptions;

public class TestServiceFailedException extends RuntimeException{
    public TestServiceFailedException(String message){
        super(message);
    }
}
