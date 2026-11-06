package com.shopsphere.repository;

import com.shopsphere.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    /**
     * Deterministic ordering: lowest id first. ids are assigned by an
     * AUTO_INCREMENT/IDENTITY column, so id order == creation order even
     * when two rows share a creation timestamp. The service relies on this
     * order both for listing and for choosing which address is promoted to
     * default when the current default is deleted — the outcome must not
     * depend on arbitrary database read order.
     */
    List<Address> findByUserIdOrderByIdAsc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);
}
