-- Align the database constraints with the scheduled prepaid publication
-- lifecycle. PENDING_APPROVAL and ACTIVATED remain accepted so historical
-- rows created by the previous approval workflow stay readable.

ALTER TABLE public.listing_orders
    DROP CONSTRAINT IF EXISTS listing_orders_status_check,
    DROP CONSTRAINT IF EXISTS listing_orders_period_check;

ALTER TABLE public.listing_orders
    ADD CONSTRAINT listing_orders_status_check
        CHECK (status IN (
            'PAID',
            'REFUNDED',
            'TERMINATED',
            'PENDING_APPROVAL',
            'ACTIVATED'
        )),
    ADD CONSTRAINT listing_orders_period_check
        CHECK (
            (
                status IN ('PAID', 'ACTIVATED', 'TERMINATED')
                AND period_start IS NOT NULL
                AND period_end IS NOT NULL
                AND period_end > period_start
            )
            OR
            (
                status = 'PENDING_APPROVAL'
                AND period_start IS NULL
                AND period_end IS NULL
            )
            OR
            (
                status = 'REFUNDED'
                AND (
                    (period_start IS NULL AND period_end IS NULL)
                    OR
                    (
                        period_start IS NOT NULL
                        AND period_end IS NOT NULL
                        AND period_end > period_start
                    )
                )
            )
        );

-- Post-check (expected: zero rows):
-- SELECT COUNT(*)
-- FROM public.listing_orders
-- WHERE status NOT IN ('PAID', 'REFUNDED', 'TERMINATED', 'PENDING_APPROVAL', 'ACTIVATED')
--    OR NOT (
--        (period_start IS NULL AND period_end IS NULL)
--        OR (period_start IS NOT NULL AND period_end IS NOT NULL AND period_end > period_start)
--    );
