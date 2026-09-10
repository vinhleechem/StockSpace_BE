package fu.stockspace.stockspace_be.contract.service;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;

import java.math.BigDecimal;

/**
 * Resolves the leased dimensions that are authoritative for a rental contract.
 *
 * <p>The current pricing type already expresses the rental scope, so no
 * additional persisted scope is required:</p>
 * <ul>
 *     <li>{@code FIXED_MONTHLY} uses the complete default layout.</li>
 *     <li>{@code PER_SQUARE_METER_MONTHLY} and {@code NEGOTIATED} use a
 *     partial area supplied by the owner.</li>
 * </ul>
 */
public final class RentalAreaAllocationPolicy {

    private RentalAreaAllocationPolicy() {
    }

    public static LeasedDimensions resolveDimensions(
            RentalPricingType pricingType,
            BigDecimal requestedWidth,
            BigDecimal requestedLength,
            BigDecimal requestedHeight,
            WarehouseLayoutResponse defaultLayout) {
        if (pricingType == null) {
            throw new BadRequestException(ErrorCode.INVALID_LEASE_DIMENSIONS,
                    "Rental pricing type is required");
        }
        Dimensions defaultDimensions = readDefaultDimensions(defaultLayout);

        if (pricingType == RentalPricingType.FIXED_MONTHLY) {
            validateLegacyFixedDimensions(
                    requestedWidth, requestedLength, requestedHeight, defaultDimensions);
            return new LeasedDimensions(
                    defaultDimensions.width(),
                    defaultDimensions.length(),
                    defaultDimensions.height(),
                    defaultDimensions.width().multiply(defaultDimensions.length()));
        }

        validatePositiveDimensions(requestedWidth, requestedLength, requestedHeight);
        if (requestedWidth.compareTo(defaultDimensions.width()) > 0
                || requestedLength.compareTo(defaultDimensions.length()) > 0
                || requestedHeight.compareTo(defaultDimensions.height()) > 0) {
            throw new BadRequestException(ErrorCode.INVALID_LEASE_DIMENSIONS,
                    "Leased dimensions cannot exceed the warehouse default layout");
        }

        BigDecimal leasedArea = requestedWidth.multiply(requestedLength);
        BigDecimal totalArea = defaultDimensions.width().multiply(defaultDimensions.length());
        if (leasedArea.compareTo(totalArea) >= 0) {
            throw new BadRequestException(ErrorCode.INVALID_LEASE_DIMENSIONS,
                    "Partial rental area must be less than the warehouse default layout area");
        }

        return new LeasedDimensions(
                requestedWidth, requestedLength, requestedHeight, leasedArea);
    }

    public static boolean isWholeWarehouse(RentalPricingType pricingType) {
        return pricingType == RentalPricingType.FIXED_MONTHLY;
    }

    /**
     * Validates dimensions already stored on a contract against the current
     * default layout without resolving a new rental scope.
     */
    public static LeasedDimensions validatePreservedDimensions(
            RentalPricingType pricingType,
            BigDecimal width,
            BigDecimal length,
            BigDecimal height,
            WarehouseLayoutResponse defaultLayout) {
        return resolveDimensions(pricingType, width, length, height, defaultLayout);
    }

    public static BigDecimal calculateDefaultArea(WarehouseLayoutResponse defaultLayout) {
        Dimensions dimensions = readDefaultDimensions(defaultLayout);
        return dimensions.width().multiply(dimensions.length());
    }

    private static Dimensions readDefaultDimensions(WarehouseLayoutResponse defaultLayout) {
        if (defaultLayout == null
                || defaultLayout.getWidth() == null
                || defaultLayout.getLength() == null
                || defaultLayout.getHeight() == null
                || defaultLayout.getWidth().signum() <= 0
                || defaultLayout.getLength().signum() <= 0
                || defaultLayout.getHeight().signum() <= 0) {
            throw new BadRequestException(ErrorCode.INVALID_LEASE_DIMENSIONS,
                    "Warehouse default layout dimensions are incomplete");
        }
        return new Dimensions(
                defaultLayout.getWidth(), defaultLayout.getLength(), defaultLayout.getHeight());
    }

    private static void validateLegacyFixedDimensions(
            BigDecimal width,
            BigDecimal length,
            BigDecimal height,
            Dimensions defaultDimensions) {
        boolean anyDimensionProvided = width != null || length != null || height != null;
        if (!anyDimensionProvided) {
            return;
        }
        if (width == null || length == null || height == null
                || width.compareTo(defaultDimensions.width()) != 0
                || length.compareTo(defaultDimensions.length()) != 0
                || height.compareTo(defaultDimensions.height()) != 0) {
            throw new BadRequestException(ErrorCode.INVALID_LEASE_DIMENSIONS,
                    "FIXED_MONTHLY contracts must use the complete default layout dimensions");
        }
    }

    private static void validatePositiveDimensions(
            BigDecimal width, BigDecimal length, BigDecimal height) {
        if (width == null || length == null || height == null
                || width.signum() <= 0 || length.signum() <= 0 || height.signum() <= 0) {
            throw new BadRequestException(ErrorCode.INVALID_LEASE_DIMENSIONS,
                    "Leased dimensions must be greater than 0");
        }
    }

    public record LeasedDimensions(
            BigDecimal width,
            BigDecimal length,
            BigDecimal height,
            BigDecimal areaM2) {
    }

    private record Dimensions(BigDecimal width, BigDecimal length, BigDecimal height) {
    }
}
