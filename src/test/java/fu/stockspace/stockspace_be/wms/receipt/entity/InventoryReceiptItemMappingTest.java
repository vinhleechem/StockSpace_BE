package fu.stockspace.stockspace_be.wms.receipt.entity;

import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryReceiptItemMappingTest {

    @Test
    void mapsOptionalStockBatchAndPickSequenceForReceiptItems() throws NoSuchFieldException {
        Field stockBatch = InventoryReceiptItem.class.getDeclaredField("stockBatch");
        ManyToOne manyToOne = stockBatch.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = stockBatch.getAnnotation(JoinColumn.class);

        assertNotNull(manyToOne);
        assertEquals(StockBatch.class, stockBatch.getType());
        assertEquals("stock_batch_id", joinColumn.name());

        Field pickSequence = InventoryReceiptItem.class.getDeclaredField("pickSequence");
        Column column = pickSequence.getAnnotation(Column.class);

        assertEquals(Integer.class, pickSequence.getType());
        assertNotNull(column);
        assertEquals("pick_sequence", column.name());
    }

    @Test
    void exposesSortedLocationBatchQueryForFutureFifoAllocation() throws NoSuchMethodException {
        var method = fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository.class
                .getMethod(
                        "findAllBySkuIdAndWarehouseIdAndRackIdAndBinIdAndIsDeletedFalseOrderByArrivalDateAscCreatedAtAscIdAsc",
                        java.util.UUID.class,
                        java.util.UUID.class,
                        java.util.UUID.class,
                        java.util.UUID.class);

        assertTrue(List.class.isAssignableFrom(method.getReturnType()));
    }
}
