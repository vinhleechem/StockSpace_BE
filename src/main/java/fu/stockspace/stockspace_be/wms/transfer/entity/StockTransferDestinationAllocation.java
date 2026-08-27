package fu.stockspace.stockspace_be.wms.transfer.entity;

import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "stock_transfer_destination_allocations", indexes = {
        @Index(name = "idx_stock_transfer_destination_alloc_item_id", columnList = "item_id"),
        @Index(name = "idx_stock_transfer_destination_alloc_bin_id", columnList = "destination_bin_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "ux_stock_transfer_dest_alloc_item_location", columnNames = {"item_id", "destination_rack_id", "destination_bin_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StockTransferDestinationAllocation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private StockTransferItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_rack_id", nullable = false)
    private WarehouseRack destinationRack;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_bin_id", nullable = false)
    private WarehouseBin destinationBin;

    @Column(name = "quantity", nullable = false)
    private int quantity;
}
