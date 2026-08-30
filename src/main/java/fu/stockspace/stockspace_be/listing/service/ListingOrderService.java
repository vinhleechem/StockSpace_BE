package fu.stockspace.stockspace_be.listing.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.listing.dto.ListingOrderResponse;
import fu.stockspace.stockspace_be.listing.dto.PurchaseListingPackageRequest;
import fu.stockspace.stockspace_be.listing.entity.ListingOrder;
import fu.stockspace.stockspace_be.listing.entity.ListingOrderStatus;
import fu.stockspace.stockspace_be.listing.entity.ListingPackage;
import fu.stockspace.stockspace_be.listing.repository.ListingOrderRepository;
import fu.stockspace.stockspace_be.listing.repository.ListingPackageRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.wallet.entity.Transaction;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingOrderService {

    private final ListingOrderRepository listingOrderRepository;
    private final ListingPackageRepository listingPackageRepository;
    private final WarehouseRepository warehouseRepository;
    private final TransactionRepository transactionRepository;
    private final WarehouseLayoutRepository warehouseLayoutRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final Clock publicationClock;

    @Transactional
    public ListingOrderResponse purchasePublication(
            UUID ownerId,
            UUID warehouseId,
            PurchaseListingPackageRequest request
    ) {
        if (request == null || request.getListingPackageId() == null) {
            throw new BadRequestException("Listing package ID is required");
        }
        if (request.getStartDate() == null) {
            throw new BadRequestException("Publication start date is required");
        }

        Warehouse warehouse = warehouseRepository.findByIdForUpdate(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        requireWarehouseOwner(warehouse, ownerId);
        validatePublishableWarehouse(warehouse);

        ListingPackage listingPackage = listingPackageRepository.findById(request.getListingPackageId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PACKAGE_NOT_FOUND));
        validateListingPackage(listingPackage);
        validateDefaultLayout(warehouseId);

        LocalDateTime now = LocalDateTime.now(publicationClock);
        LocalDate today = LocalDate.now(publicationClock);
        if (request.getStartDate().isBefore(today)) {
            throw new BadRequestException("Publication start date cannot be in the past");
        }

        if (!listingOrderRepository.findOpenPaidByWarehouseIdForUpdate(warehouseId, now).isEmpty()) {
            throw new ResourceConflictException(ErrorCode.LISTING_PUBLICATION_PENDING);
        }

        LocalDateTime periodStart = request.getStartDate().isEqual(today)
                ? now
                : request.getStartDate().atStartOfDay();
        LocalDateTime periodEnd = periodStart.plusDays(listingPackage.getDurationDays());

        User owner = warehouse.getOwner();
        ListingOrder order = ListingOrder.builder()
                .owner(owner)
                .warehouse(warehouse)
                .listingPackage(listingPackage)
                .durationDaysSnapshot(listingPackage.getDurationDays())
                .priceSnapshot(listingPackage.getPrice())
                .status(ListingOrderStatus.PAID)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .isActive(true)
                .isDeleted(false)
                .build();
        order = listingOrderRepository.save(order);

        Transaction transaction = walletService.deductBalance(
                ownerId,
                listingPackage.getPrice(),
                TransactionType.LISTING_FEE,
                "Warehouse listing package: " + listingPackage.getName(),
                null,
                null
        );
        transaction.setListingOrderId(order.getId());
        transactionRepository.save(transaction);

        warehouse.setPublishedAt(periodStart);
        warehouse.setVisibleUntil(periodEnd);
        warehouseRepository.save(warehouse);
        notifyPublication(ownerId, warehouse, periodEnd, periodStart.equals(now));

        log.info("Owner {} purchased {}-day listing package for warehouse {} with status {} until {}",
                ownerId, listingPackage.getDurationDays(), warehouseId, order.getStatus(), periodEnd);
        return mapToResponse(order, transaction.getId(), null);
    }

    private void notifyPublication(UUID ownerId, Warehouse warehouse, LocalDateTime periodEnd, boolean startsToday) {
        try {
            notificationService.push(
                    ownerId,
                    startsToday ? "Warehouse published" : "Warehouse publication scheduled",
                    startsToday
                            ? "Warehouse " + warehouse.getName() + " is visible until " + periodEnd + "."
                            : "Warehouse " + warehouse.getName() + " will be visible from "
                            + warehouse.getPublishedAt() + " until " + periodEnd + ".",
                    startsToday ? "LISTING_PUBLISHED" : "LISTING_SCHEDULED");
        } catch (Exception exception) {
            log.warn("Failed to push listing publication notification for warehouse {}: {}",
                    warehouse.getId(), exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ListingOrderResponse> getPublicationHistory(UUID ownerId, UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        requireWarehouseOwner(warehouse, ownerId);

        List<ListingOrder> orders = listingOrderRepository.findAllByOwnerIdAndWarehouseId(ownerId, warehouseId);
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> orderIds = orders.stream()
                .map(ListingOrder::getId)
                .toList();
        Map<UUID, UUID> paymentTransactionIds = transactionRepository
                .findAllByListingOrderIdInAndTransactionType(orderIds, TransactionType.LISTING_FEE)
                .stream()
                .filter(transaction -> transaction.getListingOrderId() != null)
                .collect(Collectors.toMap(
                        Transaction::getListingOrderId,
                        Transaction::getId,
                        (first, ignored) -> first
                ));
        Map<UUID, UUID> refundTransactionIds = transactionRepository
                .findAllByListingOrderIdInAndTransactionType(orderIds, TransactionType.LISTING_REFUND)
                .stream()
                .filter(transaction -> transaction.getListingOrderId() != null)
                .collect(Collectors.toMap(
                        Transaction::getListingOrderId,
                        Transaction::getId,
                        (first, ignored) -> first
                ));

        return orders.stream()
                .map(order -> mapToResponse(
                        order,
                        paymentTransactionIds.get(order.getId()),
                        refundTransactionIds.get(order.getId())))
                .toList();
    }

    private void requireWarehouseOwner(Warehouse warehouse, UUID ownerId) {
        if (warehouse.getOwner() == null || !ownerId.equals(warehouse.getOwner().getId())) {
            throw new ForbiddenException(ErrorCode.WAREHOUSE_NOT_OWNED);
        }
    }

    private void validatePublishableWarehouse(Warehouse warehouse) {
        if (!warehouse.isActive()
                || warehouse.isDeleted()
                || warehouse.getStatus() == null
                || warehouse.getStatus() != WarehouseStatus.AVAILABLE) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_NOT_AVAILABLE);
        }
    }

    private void validateDefaultLayout(UUID warehouseId) {
        WarehouseLayout layout = warehouseLayoutRepository
                .findByWarehouseIdAndIsDefaultTrue(warehouseId)
                .filter(candidate -> candidate.isActive()
                        && !candidate.isDeleted()
                        && candidate.getWidth() != null
                        && candidate.getWidth().signum() > 0
                        && candidate.getLength() != null
                        && candidate.getLength().signum() > 0
                        && candidate.getHeight() != null
                        && candidate.getHeight().signum() > 0)
                .orElseThrow(() -> new ResourceConflictException(
                        ErrorCode.WAREHOUSE_DEFAULT_LAYOUT_REQUIRED));
    }

    private void validateListingPackage(ListingPackage listingPackage) {
        if (!listingPackage.isActive() || listingPackage.isDeleted()) {
            throw new BadRequestException(ErrorCode.LISTING_PACKAGE_INACTIVE);
        }
        if (listingPackage.getDurationDays() == null
                || !java.util.Set.of(10, 15, 30).contains(listingPackage.getDurationDays())) {
            throw new BadRequestException("Listing package duration must be one of 10, 15, or 30 days");
        }
        if (listingPackage.getPrice() == null || listingPackage.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Listing package price is invalid");
        }
    }

    private ListingOrderResponse mapToResponse(
            ListingOrder order,
            UUID transactionId,
            UUID refundTransactionId
    ) {
        return ListingOrderResponse.builder()
                .id(order.getId())
                .warehouseId(order.getWarehouse().getId())
                .listingPackageId(order.getListingPackage().getId())
                .listingPackageName(order.getListingPackage().getName())
                .transactionId(transactionId)
                .refundTransactionId(refundTransactionId)
                .status(order.getStatus())
                .durationDays(order.getDurationDaysSnapshot())
                .price(order.getPriceSnapshot())
                .periodStart(order.getPeriodStart())
                .periodEnd(order.getPeriodEnd())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
