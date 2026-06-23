package fu.stockspace.stockspace_be.auth.entity;

/**
 * Provider đăng nhập của user.
 * LOCAL = đăng ký bằng email/mật khẩu thông thường.
 * GOOGLE = đăng nhập qua Google OAuth.
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
