package com.shopsphere.service;

import com.shopsphere.dto.CheckoutRequestDTO;
import com.shopsphere.dto.OrderDetailsResponseDTO;
import com.shopsphere.dto.OrderHistoryResponseDTO;
import com.shopsphere.dto.OrderItemResponseDTO;
import com.shopsphere.dto.OrderResponseDTO;
import com.shopsphere.entity.Address;
import com.shopsphere.entity.Cart;
import com.shopsphere.entity.CartItem;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Order;
import com.shopsphere.entity.OrderItem;
import com.shopsphere.entity.User;
import com.shopsphere.enums.OrderStatus;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.AddressRepository;
import com.shopsphere.repository.CartRepository;
import com.shopsphere.repository.InventoryRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;

    public OrderService(OrderRepository orderRepository,
                        CartRepository cartRepository,
                        AddressRepository addressRepository,
                        UserRepository userRepository,
                        InventoryRepository inventoryRepository) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Creates an order from the user's cart.
     * <p>
     * The entire checkout runs inside one transaction so that the order,
     * inventory decrements and cart clear all commit or all roll back together.
     * Inventory updates use {@code @Version} optimistic locking; a concurrent
     * checkout that races for the same stock row triggers a conflict (409)
     * instead of an oversell.
     * <p>
     * No Payment entity is created — the order is left in PENDING status and
     * the Payment table is reserved for when payment processing is introduced.
     */
    public OrderResponseDTO checkout(Long userId, CheckoutRequestDTO request) {

        // 1-2. Resolve and verify the customer
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId));

        // 3. Find the user's cart
        Cart cart = cartRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Cannot checkout: cart is empty"));

        // 4. Reject if cart is empty (orphanRemoval cascades the deletes on clear())
        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Cannot checkout: cart is empty");
        }

        List<CartItem> cartItems = List.copyOf(cart.getItems());

        // 5. Verify the selected address belongs to that user
        Address address = addressRepository
                .findByIdAndUserId(request.addressId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address not found with id: " + request.addressId()));

        // 11. Create the order with PENDING status and a shipping-address
        //      snapshot so the order survives later edits to the saved address.
        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setShippingAddressLine1(address.getLine1());
        order.setShippingCity(address.getCity());
        order.setShippingState(address.getState());
        order.setShippingPincode(address.getPincode());
        order.setShippingCountry(address.getCountry());

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cartItems) {

            // 6. Verify product is active
            if (!cartItem.getProduct().isActive()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Product is not available: " + cartItem.getProduct().getName());
            }

            // 7. Retrieve current inventory
            Inventory inventory = inventoryRepository
                    .findByProductId(cartItem.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory not found for product id: "
                                    + cartItem.getProduct().getId()));

            // 8. Verify sufficient stock (re-checked right before decrementing)
            if (inventory.getStockQuantity() < cartItem.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Insufficient stock for product: "
                                + cartItem.getProduct().getName()
                                + ". Available: " + inventory.getStockQuantity()
                                + ", Requested: " + cartItem.getQuantity());
            }

            // 9. Calculate line total from the live DB price (never trust client)
            BigDecimal unitPrice = cartItem.getProduct().getPrice();
            BigDecimal lineTotal = unitPrice.multiply(
                    BigDecimal.valueOf(cartItem.getQuantity()));

            // Build order item with price and name snapshot
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setProductName(cartItem.getProduct().getName());
            orderItem.setUnitPrice(unitPrice);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setLineTotal(lineTotal);
            order.getItems().add(orderItem);

            // 10. Accumulate order total
            totalAmount = totalAmount.add(lineTotal);

            // 12. Decrease inventory — @Version optimistic lock protects this.
            //     The UPDATE is deferred to flush; a version mismatch at flush
            //     time throws ObjectOptimisticLockingFailureException (→ 409).
            inventory.setStockQuantity(
                    inventory.getStockQuantity() - cartItem.getQuantity());
            inventoryRepository.save(inventory);
        }

        order.setTotalAmount(totalAmount);

        // 13. Save the order and its order items (cascade = ALL)
        Order savedOrder = orderRepository.save(order);

        // Flush to execute pending INSERTs/UPDATEs and trigger @Version checks.
        // If a concurrent checkout modified the same inventory row, the version
        // mismatch causes ObjectOptimisticLockingFailureException which the
        // GlobalExceptionHandler maps to 409 Conflict.
        orderRepository.flush();

        // Build response while entities are fresh in the persistence context
        OrderResponseDTO response = toOrderResponse(savedOrder);

        // 14. Clear the cart via the relationship — orphanRemoval = true on the
        //     @OneToMany makes Hibernate delete each orphaned row at flush.
        //     If anything above failed, the transaction rolls back and the cart
        //     items are untouched.
        cart.getItems().clear();

        // 15. Return the created order
        return response;
    }

    // ── order history & details (TASK-012.2) ───────────────────────────────

    /**
     * Paginated order history for the current customer, sorted by creation
     * date (the caller passes {@code createdAt,desc} in the Pageable).
     * <p>
     * The repository method is ownership-scoped by construction — it only
     * ever returns this user's orders.
     */
    @Transactional(readOnly = true)
    public Page<OrderHistoryResponseDTO> getMyOrders(Long userId, Pageable pageable) {
        // Validate the user first so an unknown user gets a clear 404 rather
        // than an empty page that could mask the mistake.
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        // Map lazily from the entity page — items are LAZY-loaded inside the
        // open transaction. Item snapshots (name, unit price) are preserved.
        return orderRepository.findByUserId(userId, pageable)
                .map(order -> new OrderHistoryResponseDTO(
                        order.getId(),
                        order.getStatus().name(),
                        order.getTotalAmount(),
                        order.getCreatedAt(),
                        toItemDTOs(order)));
    }

    /**
     * Full details of one order, ONLY if it belongs to the current customer.
     * <p>
     * Uses the ownership-scoped repository lookup — never a plain findById —
     * so a customer cannot even learn that another customer's order exists:
     * both "someone else's order" and "no such order" return 404.
     */
    @Transactional(readOnly = true)
    public OrderDetailsResponseDTO getOrderDetails(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id: " + orderId));

        return new OrderDetailsResponseDTO(
                order.getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getShippingAddressLine1(),
                order.getShippingCity(),
                order.getShippingState(),
                order.getShippingPincode(),
                order.getShippingCountry(),
                toItemDTOs(order));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Maps an order's items to DTOs. Preserves the price and product-name
     * snapshots stored at checkout — the live catalog may have changed since.
     */
    private List<OrderItemResponseDTO> toItemDTOs(Order order) {
        return order.getItems().stream()
                .map(item -> new OrderItemResponseDTO(
                        item.getProduct().getId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineTotal()
                ))
                .toList();
    }

    private OrderResponseDTO toOrderResponse(Order order) {
        List<OrderItemResponseDTO> itemDTOs = order.getItems().stream()
                .map(item -> new OrderItemResponseDTO(
                        item.getProduct().getId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineTotal()
                ))
                .toList();

        return new OrderResponseDTO(
                order.getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getShippingAddressLine1(),
                order.getShippingCity(),
                order.getShippingState(),
                order.getShippingPincode(),
                order.getShippingCountry(),
                itemDTOs
        );
    }
}
