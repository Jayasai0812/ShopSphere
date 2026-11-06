package com.shopsphere.service;

import com.shopsphere.common.CurrentUserProvider;
import com.shopsphere.dto.AddressRequestDTO;
import com.shopsphere.dto.AddressResponseDTO;
import com.shopsphere.dto.AddressUpdateDTO;
import com.shopsphere.entity.Address;
import com.shopsphere.entity.User;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.AddressRepository;
import com.shopsphere.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Address management for the logged-in user (TASK-013).
 *
 * Ownership and identity rules:
 * - The owning user is ALWAYS resolved from the JWT security context via
 * {@link CurrentUserProvider}; no user ID is ever accepted from a request.
 * - Every lookup goes through {@code findByIdAndUserId}, so an address that
 * belongs to another user is indistinguishable from a nonexistent one:
 * both return 404. That avoids leaking the existence of other users'
 * addresses while still blocking all read/update/delete access.
 *
 * Default-address rules (all enforced inside @Transactional boundaries):
 * - At most one address per user has isDefault = true.
 * - The first address a user creates is automatically the default, even if
 * the request asked for isDefault = false.
 * - Creating an address with isDefault = true, or promoting one via
 * {@link #setDefaultAddress}, un-sets the previous default in the SAME
 * transaction — there is never a moment with zero or two defaults (a
 * user with at least one address).
 * - Deleting the default address promotes the OLDEST remaining address
 * (lowest id) — {@code findByUserIdOrderByIdAsc} is the single
 * deterministic ordering used for both listing and promotion.
 *
 * CONCURRENCY NOTE (hardening): the transactional switch-unset logic alone
 * cannot stop two concurrent requests from both promoting their own
 * candidate (both read the old state before either commits). The database
 * schema guards the invariant instead: V4 adds a generated column
 * {@code is_default_key} = IF(is_default, user_id, NULL) with a UNIQUE
 * index, so a second default for the same user is rejected by the database
 * itself; MySQL treats unique-index NULLs as distinct, so non-default rows
 * never collide. The service catches the resulting
 * DataIntegrityViolationException and maps it to 409 CONFLICT.
 */
@Service
public class AddressService {

        private final AddressRepository addressRepository;
        private final UserRepository userRepository;
        private final CurrentUserProvider currentUserProvider;

        public AddressService(AddressRepository addressRepository,
                        UserRepository userRepository,
                        CurrentUserProvider currentUserProvider) {
                this.addressRepository = addressRepository;
                this.userRepository = userRepository;
                this.currentUserProvider = currentUserProvider;
        }

        /** Lists every address owned by the authenticated user. */
        @Transactional(readOnly = true)
        public List<AddressResponseDTO> getAddresses() {
                return addressRepository.findByUserIdOrderByIdAsc(currentUserId()).stream()
                                .map(AddressService::toDto)
                                .toList();
        }

        /**
         * Returns one address, 404 if it does not exist or is not owned by the caller.
         */
        @Transactional(readOnly = true)
        public AddressResponseDTO getAddress(Long addressId) {
                return toDto(loadOwnedAddress(addressId));
        }

        /**
         * Creates an address for the authenticated user. The first address
         * always becomes the default; a later create with isDefault = true
         * steals the default from the previous holder within the same
         * transaction.
         */
        @Transactional
        public AddressResponseDTO createAddress(AddressRequestDTO request) {
                User user = userRepository.findById(currentUserId())
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                List<Address> existing = addressRepository.findByUserIdOrderByIdAsc(user.getId());
                boolean isFirst = existing.isEmpty();

                Address address = new Address();
                address.setUser(user);
                applyFields(address,
                                request.fullName(), request.phone(), request.addressLine1(),
                                request.addressLine2(), request.city(), request.state(),
                                request.postalCode(), request.country(), request.addressType());

                boolean wantsDefault = Boolean.TRUE.equals(request.isDefault());
                address.setDefaultAddress(isFirst || wantsDefault);

                if (address.isDefaultAddress() && !isFirst) {
                        unsetExistingDefault(user.getId(), address);
                }
                try {
                        return toDto(addressRepository.saveAndFlush(address));
                } catch (DataIntegrityViolationException e) {
                        // Another concurrent request already claimed the default;
                        // the V4 unique index rejected the second row.
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Address was modified concurrently, please retry");
                }
        }

        /**
         * Partial update: only fields present (non-null) in the request
         * change; omitted or null fields keep their current value. An empty
         * string for addressLine2 clears it. A request flagging isDefault
         * performs the default switch in the same transaction as the edit.
         */
        @Transactional
        public AddressResponseDTO updateAddress(Long addressId, AddressUpdateDTO request) {
                Address address = loadOwnedAddress(addressId);

                if (request.fullName() != null) {
                        address.setFullName(request.fullName().trim());
                }
                if (request.phone() != null) {
                        address.setPhone(request.phone().trim());
                }
                if (request.addressLine1() != null) {
                        address.setLine1(request.addressLine1().trim());
                }
                if (request.addressLine2() != null) {
                        address.setLine2(request.addressLine2().isBlank() ? null : request.addressLine2().trim());
                }
                if (request.city() != null) {
                        address.setCity(request.city().trim());
                }
                if (request.state() != null) {
                        address.setState(request.state().trim());
                }
                if (request.postalCode() != null) {
                        address.setPincode(request.postalCode().trim());
                }
                if (request.country() != null) {
                        address.setCountry(request.country().trim());
                }
                if (request.addressType() != null) {
                        address.setAddressType(request.addressType());
                }

                if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefaultAddress()) {
                        unsetExistingDefault(address.getUser().getId(), address);
                        address.setDefaultAddress(true);
                }
                try {
                        return toDto(addressRepository.saveAndFlush(address));
                } catch (DataIntegrityViolationException e) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Address was modified concurrently, please retry");
                }
        }

        /**
         * Sets the given address as the user's default. The previous default
         * is un-set in the same transaction, so the invariant "exactly one
         * default per user with addresses" holds across concurrent readers.
         */
        @Transactional
        public AddressResponseDTO setDefaultAddress(Long addressId) {
                Address address = loadOwnedAddress(addressId);
                if (!address.isDefaultAddress()) {
                        unsetExistingDefault(address.getUser().getId(), address);
                        address.setDefaultAddress(true);
                        try {
                                address = addressRepository.saveAndFlush(address);
                        } catch (DataIntegrityViolationException e) {
                                throw new ResponseStatusException(HttpStatus.CONFLICT,
                                                "Address was modified concurrently, please retry");
                        }
                }
                return toDto(address);
        }

        /**
         * Deletes an owned address. If it was the default, another remaining
         * address is promoted to default within the same transaction.
         */
        @Transactional
        public void deleteAddress(Long addressId) {
                Address address = loadOwnedAddress(addressId);
                boolean wasDefault = address.isDefaultAddress();
                Long userId = address.getUser().getId();
                addressRepository.delete(address);

                if (wasDefault) {
                        List<Address> remaining = addressRepository.findByUserIdOrderByIdAsc(userId);
                        if (!remaining.isEmpty()) {
                                // Deterministic promotion: the repository orders by
                                // created_at, then id, so the oldest remaining address
                                // always wins instead of an arbitrary DB pick.
                                Address promoted = remaining.get(0);
                                promoted.setDefaultAddress(true);
                                addressRepository.save(promoted);
                        }
                }
        }

        /**
         * Loads the address only if it exists AND belongs to the caller; 404 otherwise.
         */
        private Address loadOwnedAddress(Long addressId) {
                return addressRepository.findByIdAndUserId(addressId, currentUserId())
                                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        }

        private Long currentUserId() {
                return currentUserProvider.getCurrentUserId();
        }

        /**
         * Un-sets the current default of the given user, ignoring the
         * candidate address itself. Runs inside the caller's transaction.
         */
        private void unsetExistingDefault(Long userId, Address candidate) {
                addressRepository.findByUserIdOrderByIdAsc(userId).stream()
                                .filter(Address::isDefaultAddress)
                                .filter(a -> !a.getId().equals(candidate.getId()))
                                .forEach(a -> {
                                        a.setDefaultAddress(false);
                                        addressRepository.save(a);
                                });
        }

        private void applyFields(Address address, String fullName, String phone, String line1,
                        String line2, String city, String state, String postalCode,
                        String country, com.shopsphere.entity.AddressType addressType) {
                address.setFullName(fullName.trim());
                address.setPhone(phone.trim());
                address.setLine1(line1.trim());
                address.setLine2(line2 == null || line2.isBlank() ? null : line2.trim());
                address.setCity(city.trim());
                address.setState(state.trim());
                address.setPincode(postalCode.trim());
                address.setCountry(country.trim());
                address.setAddressType(addressType);
        }

        private static AddressResponseDTO toDto(Address address) {
                return new AddressResponseDTO(
                                address.getId(),
                                address.getFullName(),
                                address.getPhone(),
                                address.getLine1(),
                                address.getLine2(),
                                address.getCity(),
                                address.getState(),
                                address.getPincode(),
                                address.getCountry(),
                                address.getAddressType(),
                                address.isDefaultAddress());
        }
}
