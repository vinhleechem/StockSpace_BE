package fu.stockspace.stockspace_be.listing.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.listing.dto.ListingOrderResponse;
import fu.stockspace.stockspace_be.listing.dto.PurchaseListingPackageRequest;
import fu.stockspace.stockspace_be.listing.entity.ListingOrder;
import fu.stockspace.stockspace_be.listing.entity.ListingPackage;
import fu.stockspace.stockspace_be.listing.repository.ListingOrderRepository;
import fu.stockspace.stockspace_be.listing.repository.ListingPackageRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.wallet.entity.Transaction;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingOrderService {

    private final ListingOrderRepository listingOrderRepository;
    private final ListingPackageRepository listingPackageRepository;
    private final WarehouseRepository warehouseRepository;
    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;

    @Transactional
    public ListingOrderResponse purchaseOrRenew(
            UUID ownerId,
            UUID warehouseId,
            PurchaseListingPackageRequest request
    ) {
        if (request == null || request.getListingPackageId() == null) {
            throw new BadRequestException("Listing package ID is required");
        }

        Warehouse warehouse = warehouseRepository.findByIdForUpdate(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        requireWarehouseOwner(warehouse, ownerId);
        validatePublishableWarehouse(warehouse);

        ListingPackage listingPackage = listingPackageRepository.findById(request.getListingPackageId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PACKAGE_NOT_FOUND));
        validateListingPackage(listingPackage);

        LocalDateTime now = LocalDateTime.now();
        boolean extendingActivePublication = warehouse.getVisibleUntil() != null
                && warehouse.getVisibleUntil().isAfter(now);
        LocalDateTime periodStart = extendingActivePublication
                ? warehouse.getVisibleUntil()
                : now;
        LocalDateTime periodEnd = periodStart.plusDays(listingPackage.getDurationDays());

        User owner = warehouse.getOwner();
        ListingOrder order = ListingOrder.builder()
                .owner(owner)
                .warehouse(warehouse)
                .listingPackage(listingPackage)
                .durationDaysSnapshot(listingPackage.getDurationDays())
                .priceSnapshot(listingPackage.getPrice())
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

        if (!extendingActivePublication || warehouse.getPublishedAt() == null) {
            warehouse.setPublishedAt(periodStart);
        }
        warehouse.setVisibleUntil(periodEnd);
        warehouseRepository.save(warehouse);

        notifyPublication(ownerId, warehouse, periodEnd, extendingActivePublication);

        log.info("Owner {} purchased {}-day listing package for warehouse {} until {}",
                ownerId, listingPackage.getDurationDays(), warehouseId, periodEnd);
        return mapToResponse(order, transaction.getId());
    }

    private void notifyPublication(UUID ownerId, Warehouse warehouse, LocalDateTime periodEnd, boolean renewed) {
        try {
            notificationService.push(
                    ownerId,
                    renewed ? "Warehouse publication renewed" : "Warehouse published",
                    "Warehouse " + warehouse.getName() + " is visible until " + periodEnd + ".",
                    renewed ? "LISTING_RENEWED" : "LISTING_PUBLISHED");
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

        return listingOrderRepository.findAllByOwnerIdAndWarehouseId(ownerId, warehouseId)
                .stream()
                .map(order -> transactionRepository.findByListingOrderId(order.getId())
                        .map(transaction -> mapToResponse(order, transaction.getId()))
                        .orElseGet(() -> mapToResponse(order, null)))
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
                || !warehouse.isVerified()
                || warehouse.getStatus() == null
                || warehouse.getStatus() == WarehouseStatus.INACTIVE) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_NOT_AVAILABLE);
        }
    }

    private void validateListingPackage(ListingPackage listingPackage) {
        if (!listingPackage.isActive() || listingPackage.isDeleted()) {
            throw new BadRequestException("Listing package is inactive");
        }
        if (listingPackage.getDurationDays() == null
                || !java.util.Set.of(10, 15, 30).contains(listingPackage.getDurationDays())) {
            throw new BadRequestException("Listing package duration must be one of 10, 15, or 30 days");
        }
        if (listingPackage.getPrice() == null || listingPackage.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Listing package price is invalid");
        }
    }

    private ListingOrderResponse mapToResponse(ListingOrder order, UUID transactionId) {
        return ListingOrderResponse.builder()
                .id(order.getId())
                .warehouseId(order.getWarehouse().getId())
                .listingPackageId(order.getListingPackage().getId())
                .listingPackageName(order.getListingPackage().getName())
                .transactionId(transactionId)
                .durationDays(order.getDurationDaysSnapshot())
                .price(order.getPriceSnapshot())
                .periodStart(order.getPeriodStart())
                .periodEnd(order.getPeriodEnd())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
