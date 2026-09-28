package fu.stockspace.stockspace_be.wms.putaway;

public record PutawayCapacitySnapshot(
        PutawayLocationCapacity rack,
        PutawayLocationCapacity bin
) {
}
