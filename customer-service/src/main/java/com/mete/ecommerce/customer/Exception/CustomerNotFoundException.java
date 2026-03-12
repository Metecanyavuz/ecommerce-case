package com.mete.ecommerce.customer.Exception;

public class CustomerNotFoundException extends RuntimeException{
    public CustomerNotFoundException(Long id){
        super("Customer not found: "  + id);
    }
}
