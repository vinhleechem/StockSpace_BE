package fu.stockspace.stockspace_be.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service gửi email thông báo.
 * Dùng @Async để không block luồng chính khi gửi mail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:StockSpace <noreply@stockspace.com>}")
    private String fromAddress;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    // ==================== Welcome Email ====================

    /**
     * Gửi email chào mừng sau khi đăng ký thành công.
     * Chạy bất đồng bộ để không làm chậm response đăng ký.
     */
    @Async
    public void sendWelcomeEmail(String toEmail, String fullName) {
        try {
            String subject = "Chào mừng bạn đến với StockSpace! 🎉";
            String content = buildWelcomeEmailContent(fullName);
            sendHtmlEmail(toEmail, subject, content);
            log.info("Welcome email sent to: {}", toEmail);
        } catch (Exception e) {
            // Email thất bại không nên làm hỏng luồng đăng ký
            log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ==================== Password Reset Token ====================

    /**
     * Gửi email chứa đường dẫn đặt lại mật khẩu.
     * Chạy bất đồng bộ.
     */
    @Async
    public void sendPasswordResetEmail(String toEmail, String fullName, String token) {
        try {
            String subject = "[StockSpace] Đường dẫn đặt lại mật khẩu";
            String content = buildPasswordResetEmailContent(fullName, toEmail, token);
            sendHtmlEmail(toEmail, subject, content);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ==================== Private helpers ====================

    private void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true); // true = HTML
        mailSender.send(message);
    }

    private String buildWelcomeEmailContent(String fullName) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head><meta charset="UTF-8"></head>
                <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px;">
                  <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 8px; padding: 40px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                      <h1 style="color: #2563EB; margin: 0;">StockSpace</h1>
                      <p style="color: #6B7280; font-size: 14px;">Nền tảng quản lý kho bãi thông minh</p>
                    </div>
                    <h2 style="color: #1F2937;">Chào mừng %s! 🎉</h2>
                    <p style="color: #374151; line-height: 1.6;">
                      Cảm ơn bạn đã đăng ký tài khoản tại <strong>StockSpace</strong>.
                      Tài khoản của bạn đã được kích hoạt và sẵn sàng sử dụng.
                    </p>
                    <p style="color: #374151; line-height: 1.6;">
                      Với StockSpace, bạn có thể:
                    </p>
                    <ul style="color: #374151; line-height: 1.8;">
                      <li>🏭 Tìm kiếm và thuê kho bãi phù hợp</li>
                      <li>📋 Quản lý hợp đồng thuê kho dễ dàng</li>
                      <li>💰 Nạp/rút tiền qua ví điện tử an toàn</li>
                      <li>📊 Theo dõi tình trạng kho bãi thời gian thực</li>
                    </ul>
                    <div style="text-align: center; margin: 30px 0;">
                      <a href="%s" style="background-color: #2563EB; color: white; padding: 12px 30px; border-radius: 6px; text-decoration: none; font-weight: bold;">
                        Bắt đầu sử dụng ngay
                      </a>
                    </div>
                    <hr style="border: none; border-top: 1px solid #E5E7EB; margin: 30px 0;">
                    <p style="color: #9CA3AF; font-size: 12px; text-align: center;">
                      Nếu bạn không đăng ký tài khoản này, hãy bỏ qua email này.<br>
                      © 2024 StockSpace. All rights reserved.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(frontendUrl);
    }

    private String buildPasswordResetEmailContent(String fullName, String email, String token) {
        String resetLink = "%s/reset-password?token=%s&email=%s".formatted(
                frontendUrl,
                token,
                java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8)
        );
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head><meta charset="UTF-8"></head>
                <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px;">
                  <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 8px; padding: 40px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                      <h1 style="color: #2563EB; margin: 0;">StockSpace</h1>
                      <p style="color: #6B7280; font-size: 14px;">Nền tảng quản lý kho bãi thông minh</p>
                    </div>
                    <h2 style="color: #1F2937;">Đặt lại mật khẩu 🔐</h2>
                    <p style="color: #374151; line-height: 1.6;">Xin chào <strong>%s</strong>,</p>
                    <p style="color: #374151; line-height: 1.6;">
                      Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.
                      Vui lòng nhấn vào nút dưới đây để tiến hành đặt lại mật khẩu mới:
                    </p>
                    <div style="text-align: center; margin: 30px 0;">
                      <a href="%s" style="background-color: #2563EB; color: white; padding: 12px 30px; border-radius: 6px; text-decoration: none; font-weight: bold; display: inline-block;">
                        Đặt lại mật khẩu
                      </a>
                    </div>
                    <p style="color: #374151; line-height: 1.6;">
                      Hoặc bạn có thể sao chép và dán đường dẫn dưới đây vào trình duyệt của bạn:
                    </p>
                    <p style="color: #2563EB; word-break: break-all; font-size: 14px; background-color: #F3F4F6; padding: 10px; border-radius: 4px;">
                      %s
                    </p>
                    <p style="color: #EF4444; font-weight: bold; text-align: center;">
                      ⏰ Đường dẫn đặt lại mật khẩu có hiệu lực trong <strong>15 phút</strong>.
                    </p>
                    <p style="color: #374151; line-height: 1.6;">
                      Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.
                      Tài khoản của bạn vẫn an toàn.
                    </p>
                    <hr style="border: none; border-top: 1px solid #E5E7EB; margin: 30px 0;">
                    <p style="color: #9CA3AF; font-size: 12px; text-align: center;">
                      © 2024 StockSpace. All rights reserved.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(fullName, resetLink, resetLink);
    }
    // ==================== Staff Invitation ====================

    /**
     * Gửi email mời nhân viên kho.
     * Chạy bất đồng bộ — thất bại email không ảnh hưởng luồng chính.
     *
     * @param toEmail     Email nhân viên nhận lời mời
     * @param staffName   Tên nhân viên (Tenant nhập khi mời)
     * @param tenantName  Tên doanh nghiệp / tên Tenant mời
     * @param token       Token dùng một lần (UUID random, hết hạn sau 48h)
     */
    @Async
    public void sendStaffInvitationEmail(String toEmail, String staffName, String tenantName, String token) {
        try {
            String subject = "[StockSpace] Lời mời tham gia quản lý kho của " + tenantName;
            String content = buildStaffInvitationEmailContent(staffName, tenantName, toEmail, token);
            sendHtmlEmail(toEmail, subject, content);
            log.info("Staff invitation email sent to: {} (tenant: {})", toEmail, tenantName);
        } catch (Exception e) {
            log.error("Failed to send staff invitation email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildStaffInvitationEmailContent(String staffName, String tenantName, String email, String token) {
        String acceptLink = "%s/staff/accept?token=%s".formatted(frontendUrl, token);
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head><meta charset="UTF-8"></head>
                <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px;">
                  <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 8px; padding: 40px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                      <h1 style="color: #2563EB; margin: 0;">StockSpace</h1>
                      <p style="color: #6B7280; font-size: 14px;">Nền tảng quản lý kho bãi thông minh</p>
                    </div>
                    <h2 style="color: #1F2937;">Bạn được mời tham gia quản lý kho! 🏭</h2>
                    <p style="color: #374151; line-height: 1.6;">Xin chào <strong>%s</strong>,</p>
                    <p style="color: #374151; line-height: 1.6;">
                      Doanh nghiệp <strong>%s</strong> đã mời bạn tham gia hệ thống quản lý kho trên <strong>StockSpace</strong>
                      với vai trò <strong>Nhân viên kho (Staff)</strong>.
                    </p>
                    <p style="color: #374151; line-height: 1.6;">
                      Với tài khoản này, bạn có thể:
                    </p>
                    <ul style="color: #374151; line-height: 1.8;">
                      <li>📦 Tạo và duyệt phiếu nhập/xuất kho</li>
                      <li>📊 Theo dõi tồn kho thời gian thực</li>
                      <li>🗂️ Quản lý hàng hóa và danh mục</li>
                    </ul>
                    <div style="text-align: center; margin: 30px 0;">
                      <a href="%s" style="background-color: #059669; color: white; padding: 14px 36px; border-radius: 6px; text-decoration: none; font-weight: bold; display: inline-block; font-size: 16px;">
                        ✅ Xác nhận tham gia
                      </a>
                    </div>
                    <p style="color: #374151; line-height: 1.6;">
                      Hoặc bạn có thể sao chép đường dẫn dưới đây vào trình duyệt:
                    </p>
                    <p style="color: #2563EB; word-break: break-all; font-size: 13px; background-color: #F3F4F6; padding: 10px; border-radius: 4px;">
                      %s
                    </p>
                    <p style="color: #EF4444; font-weight: bold; text-align: center;">
                      ⏰ Lời mời này có hiệu lực trong <strong>48 giờ</strong>.
                    </p>
                    <p style="color: #374151; line-height: 1.6; font-size: 13px;">
                      Nếu bạn không mong đợi lời mời này, hãy bỏ qua email này. Không cần thực hiện thêm bất kỳ hành động nào.
                    </p>
                    <hr style="border: none; border-top: 1px solid #E5E7EB; margin: 30px 0;">
                    <p style="color: #9CA3AF; font-size: 12px; text-align: center;">
                      © 2024 StockSpace. All rights reserved.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(staffName, tenantName, acceptLink, acceptLink);
    }
}

