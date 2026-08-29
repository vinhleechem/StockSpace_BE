package fu.stockspace.stockspace_be.auth.entity;








public enum PermissionCode {
    AUTH_SESSION_MANAGE("Manage the authenticated session"),
    PROFILE_READ("View own profile"),
    MEDIA_UPLOAD("Upload media"),
    CHAT_USE("Use authenticated chatbot"),
    NOTIFICATION_READ("View own notifications"),
    NOTIFICATION_UPDATE("Update own notifications"),
    WALLET_READ("View own wallet and transactions"),
    WALLET_TOP_UP("Top up own wallet"),
    WALLET_WITHDRAW("Create and view own withdrawal requests"),

    WAREHOUSE_READ("View warehouses available to the actor"),
    WAREHOUSE_CREATE("Create own warehouse listings"),
    WAREHOUSE_UPDATE("Update own warehouse listings"),
    WAREHOUSE_DELETE("Delete own warehouse listings"),
    WAREHOUSE_LAYOUT_OWNER_MANAGE("Manage layouts of own warehouses"),
    WAREHOUSE_LAYOUT_TENANT_MANAGE("Manage layouts for warehouses covered by active contracts"),
    WAREHOUSE_REVIEW("Review warehouse listings"),

    CONTRACT_READ("View own rental contracts"),
    CONTRACT_OWNER_MANAGE("Create, edit, and submit own owner-side contracts"),
    CONTRACT_TENANT_MANAGE("Confirm, request changes to, or reject own tenant-side contracts"),

    INSPECTION_READ("View inspections"),
    INSPECTION_REQUEST("Request inspection for own warehouse"),
    INSPECTION_EXECUTE("Submit assigned inspection reports"),
    INSPECTION_ASSIGN("Assign inspectors to inspections"),

    INVENTORY_READ("View inventory"),
    INVENTORY_CREATE("Create inventory records"),
    INVENTORY_UPDATE("Update inventory records"),
    INVENTORY_DELETE("Delete inventory records"),
    INBOUND_CREATE("Create inbound receipts"),
    OUTBOUND_CREATE("Create outbound receipts"),
    INVENTORY_AUDIT_MANAGE("Create, submit, and decide inventory audits"),
    PRODUCT_MANAGE("Manage products, categories, and units visible to the tenant"),

    STAFF_MANAGE("Manage tenant staff and warehouse assignments"),
    STAFF_WORK_HISTORY_READ("View own staff work history"),
    PACKAGE_PURCHASE("Purchase and view tenant subscriptions"),

    ADMIN_USER_MANAGE("Manage users"),
    ADMIN_ROLE_MANAGE("Manage roles and role assignments"),
    ADMIN_PERMISSION_MANAGE("Manage permissions"),
    ADMIN_SYSTEM_CONFIG_MANAGE("Manage system configuration"),
    ADMIN_SYSTEM_POLICY_MANAGE("Manage system policies"),
    ADMIN_PACKAGE_MANAGE("Manage packages and subscriptions"),
    ADMIN_WAREHOUSE_TYPE_MANAGE("Manage warehouse types"),
    ADMIN_TRANSACTION_READ("View platform transactions"),
    ADMIN_WITHDRAWAL_MANAGE("Approve or reject withdrawals"),
    ADMIN_INVENTORY_READ("View platform inventory"),
    ADMIN_STATS_READ("View platform statistics"),
    OWNER_STATS_READ("View own owner statistics");

    private final String description;

    PermissionCode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
