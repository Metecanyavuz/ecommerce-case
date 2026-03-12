package com.mete.ecommerce.customer.service;

import com.mete.ecommerce.customer.Exception.CustomerEmailAlreadyExistsException;
import com.mete.ecommerce.customer.Exception.CustomerNotFoundException;
import com.mete.ecommerce.customer.dto.CreateCustomerRequest;
import com.mete.ecommerce.customer.dto.CustomerResponse;
import com.mete.ecommerce.customer.dto.UpdateCustomerRequest;
import com.mete.ecommerce.customer.entity.Customer;
import com.mete.ecommerce.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll()
                .stream()
                .map(CustomerResponse::new)
                .toList();
    }

    public CustomerResponse getCustomerById(Long id) {
        return new CustomerResponse(
                customerRepository.findById(id)
                        .orElseThrow(() -> new CustomerNotFoundException(id))
        );
    }

    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        Customer customer = Customer.builder()
                .name(request.getName())
                .surname(request.getSurname())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .build();
        return new CustomerResponse(customerRepository.save(customer));
    }

    public CustomerResponse updateCustomer(Long id , UpdateCustomerRequest request) {
        Customer customer =customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));

        if (request.getName() != null) customer.setName(request.getName());
        if (request.getSurname() != null) customer.setSurname(request.getSurname());
        if (request.getPhone() != null) customer.setPhone(request.getPhone());
        if (request.getAddress() != null) customer.setAddress(request.getAddress());

        if (request.getEmail() != null
                && !request.getEmail().equals(customer.getEmail())) {
            if (customerRepository.existsByEmail(request.getEmail())) {
                throw new CustomerEmailAlreadyExistsException(request.getEmail());
            }
            customer.setEmail(request.getEmail());
        }
        return new CustomerResponse(customerRepository.save(customer));

    }

    public void deleteCustomer(Long id) {
        if(!customerRepository.existsById(id)) {
            throw new CustomerNotFoundException(id);
        }
        customerRepository.deleteById(id);
    }
}
