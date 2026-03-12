package com.mete.ecommerce.customer.Exception;

public class CustomerEmailAlreadyExistsException extends RuntimeException{
    public CustomerEmailAlreadyExistsException(String email){
        super("Email already exists: "  + email);
    }
}
