package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Builds a stable, public-only document for warehouse semantic retrieval. */
public final class WarehouseSearchDocumentSupport {

    private WarehouseSearchDocumentSupport() {
    }

    public static String embeddingText(Warehouse warehouse) {
        if (warehouse == null) {
            return "";
        }
        String type = warehouse.getType() == null ? "" : warehouse.getType().getName();
        return embeddingText(
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getProvinceName(),
                warehouse.getDistrictName(),
                type,
                warehouse.getDescription()
        );
    }

    public static String embeddingText(
            String name,
            String address,
            String province,
            String district,
            String type,
            String description
    ) {
        return line("Tên kho", name)
                + line("Địa chỉ", address)
                + line("Tỉnh/thành", province)
                + line("Quận/huyện", district)
                + line("Loại kho", type)
                + line("Mô tả", description);
    }

    public static String contentHash(Warehouse warehouse) {
        return contentHash(embeddingText(warehouse));
    }

    public static String contentHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(safe(text).getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String line(String label, String value) {
        return label + ": " + safe(value) + "\n";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
