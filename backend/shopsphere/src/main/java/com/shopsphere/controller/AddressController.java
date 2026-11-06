package com.shopsphere.controller;

import com.shopsphere.dto.AddressRequestDTO;
import com.shopsphere.dto.AddressResponseDTO;
import com.shopsphere.dto.AddressUpdateDTO;
import com.shopsphere.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Self-service address endpoints for the authenticated user (TASK-013).
 *
 * All routes operate exclusively on the identity from the JWT security
 * context — there is no user ID path variable and no user ID in any request
 * body. Address ownership is enforced in {@link AddressService}; an address
 * belonging to another user is reported as 404, never 403, so the endpoint
 * does not reveal that the address exists.
 *
 * PATCH /me/addresses/{id} is a PARTIAL update: omitted or null fields keep
 * their current value. It is not a PUT-style replacement.
 */
@RestController
@RequestMapping("/api/v1/users/me/addresses")
public class AddressController {

        private final AddressService addressService;

        public AddressController(AddressService addressService) {
                this.addressService = addressService;
        }

        /** Creates an address for the logged-in user. Returns 201 + Location. */
        @PostMapping
        public ResponseEntity<AddressResponseDTO> createAddress(
                        @Valid @RequestBody AddressRequestDTO request) {
                AddressResponseDTO created = addressService.createAddress(request);
                return ResponseEntity
                                .created(URI.create("/api/v1/users/me/addresses/" + created.id()))
                                .body(created);
        }

        /** Lists the logged-in user's addresses. */
        @GetMapping
        public ResponseEntity<List<AddressResponseDTO>> getAddresses() {
                return ResponseEntity.ok(addressService.getAddresses());
        }

        /** Returns one owned address, or 404 if it is not the caller's. */
        @GetMapping("/{addressId}")
        public ResponseEntity<AddressResponseDTO> getAddress(@PathVariable Long addressId) {
                return ResponseEntity.ok(addressService.getAddress(addressId));
        }

        /** Partial update of selected fields of an owned address. */
        @PatchMapping("/{addressId}")
        public ResponseEntity<AddressResponseDTO> updateAddress(
                        @PathVariable Long addressId,
                        @Valid @RequestBody AddressUpdateDTO request) {
                return ResponseEntity.ok(addressService.updateAddress(addressId, request));
        }

        /** Deletes an owned address. Returns 204 No Content. */
        @DeleteMapping("/{addressId}")
        public ResponseEntity<Void> deleteAddress(@PathVariable Long addressId) {
                addressService.deleteAddress(addressId);
                return ResponseEntity.noContent().build();
        }

        /** Promotes an owned address to the user's single default address. */
        @PatchMapping("/{addressId}/default")
        public ResponseEntity<AddressResponseDTO> setDefaultAddress(@PathVariable Long addressId) {
                return ResponseEntity.ok(addressService.setDefaultAddress(addressId));
        }
}
