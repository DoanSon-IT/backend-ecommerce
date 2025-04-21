package com.sondv.phone.repository;

import com.sondv.phone.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByUserId(Long userId);

    Page<Customer> findAllByOrderByIdAsc(Pageable pageable); // Sắp xếp theo id tăng dần có phân trang

    Page<Customer> findAllByOrderByIdDesc(Pageable pageable); // Sắp xếp theo id giảm dần có phân trang

    @Query("SELECT c FROM Customer c " +
            "WHERE LOWER(c.user.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(c.user.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Customer> searchCustomers(String keyword, Pageable pageable);
}
