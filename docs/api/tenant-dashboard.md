# Tenant dashboard API

## Get dashboard

```http
GET /api/tenant/dashboard
Authorization: Bearer <access-token>
```

The endpoint resolves the tenant from the authenticated user. It does not
accept a `tenantId` parameter. Warehouse and stock metrics are limited to the
user's current `ACTIVE` rental contracts; soft-deleted and inactive records
are excluded.

Successful responses use the standard `ApiResponse` envelope:

```json
{
  "success": true,
  "message": "Tenant dashboard loaded",
  "data": {
    "activeWarehouseCount": 2,
    "activeContractCount": 2,
    "pendingContractCount": 1,
    "productCount": 8,
    "stockBatchCount": 12,
    "totalStockQuantity": 345,
    "pendingInboundReceiptCount": 4,
    "pendingOutboundReceiptCount": 1,
    "pendingAuditCount": 2,
    "pendingTransferCount": 3,
    "activeStaffCount": 5,
    "unreadNotificationCount": 6,
    "activeSubscription": {
      "id": "subscription-uuid",
      "packageName": "Pro",
      "startDate": "2026-09-01",
      "endDate": "2026-09-30",
      "status": "ACTIVE"
    }
  }
}
```

`activeSubscription` is `null` when the tenant has no current active
subscription. Access requires the `TENANT_DASHBOARD_READ` permission, which
is included in the default tenant role.
