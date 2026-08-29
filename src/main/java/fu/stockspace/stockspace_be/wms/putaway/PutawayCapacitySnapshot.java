package fu.stockspace.stockspace_be.wms.putaway;

/**
 * Rack and bin capacity observed when an allocation was recommended.
 */
public record PutawayCapacitySnapshot(
        PutawayLocationCapacity rack,
        PutawayLocationCapacity bin
) {
}
