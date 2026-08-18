package com.smarttrust.common.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails (currently: email-verification OTP).
 *
 * All credentials come from environment variables (see .env.example):
 *   SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, MAIL_FROM, MAIL_FROM_NAME
 *
 * Security rules honoured here:
 *  - The OTP appears ONLY inside the email body — never in API responses,
 *    never in logs (unless smarttrust.otp.mock-enabled=true for local dev).
 *  - No unnecessary personal data is included in the email.
 */
@Slf4j
@Service
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;
    private final String fromName;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${smarttrust.mail.from:noreply@smarttrust.pk}") String from,
                        @Value("${smarttrust.mail.from-name:SmartTrust}") String fromName) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
        this.fromName = fromName;
    }

    /** True when SMTP is configured (spring.mail.host set via SMTP_HOST). */
    public boolean isMailConfigured() {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        return sender != null;
    }

    /**
     * Sends the "SmartTrust — Verify Your Email" OTP email.
     * @return true if the mail was handed to the SMTP server successfully.
     */
    public boolean sendOtpEmail(String toEmail, String otpCode, int expiryMinutes) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("SMTP not configured (set SMTP_HOST/SMTP_USERNAME/SMTP_PASSWORD) — OTP email NOT sent to {}", maskEmail(toEmail));
            return false;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(toEmail);
            helper.setSubject("SmartTrust — Verify Your Email");
            helper.setText(buildOtpBody(otpCode, expiryMinutes), true);
            sender.send(message);
            log.info("OTP email sent to {}", maskEmail(toEmail)); // no OTP, no PII in logs
            return true;
        } catch (Exception ex) {
            log.error("Failed to send OTP email to {}: {}", maskEmail(toEmail), ex.getMessage());
            return false;
        }
    }

    private String buildOtpBody(String otp, int expiryMinutes) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    <tr><td align="center" style="padding:24px 12px;">
                      <table role="presentation" width="480" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:10px;overflow:hidden;border:1px solid #e3e8ec;">
                        <tr style="background:#0b5fff;">
                          <td style="padding:20px 28px;color:#ffffff;font-size:22px;font-weight:bold;">
                            Smart<span style="color:#9dc2ff;">Trust</span>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px;">
                            <p style="margin:0 0 14px;font-size:16px;color:#1c2733;">Verify Your Email</p>
                            <p style="margin:0 0 18px;font-size:14px;color:#4a5a6a;line-height:1.6;">
                              Use the verification code below to complete your registration.
                              It expires in <b>%d minutes</b>.
                            </p>
                            <div style="margin:0 0 18px;padding:14px;background:#f0f5ff;border:1px dashed #0b5fff;
                                        border-radius:8px;text-align:center;font-size:30px;letter-spacing:8px;
                                        font-weight:bold;color:#0b5fff;">%s</div>
                            <p style="margin:0 0 8px;font-size:12px;color:#7a8a99;line-height:1.6;">
                              &#128274; Security notice: never share this code with anyone — SmartTrust staff
                              will never ask for it. If you did not request this code, you can ignore this email.
                            </p>
                          </td>
                        </tr>
                        <tr style="background:#f8fafc;">
                          <td style="padding:14px 28px;font-size:11px;color:#9aa8b5;">
                            &copy; SmartTrust — Verified Home Services Marketplace
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(expiryMinutes, otp);
    }

    /** Masks an email for logs: ali***@gmail.com */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String prefix = local.length() <= 3 ? local.charAt(0) + "**" : local.substring(0, 3) + "***";
        return prefix + email.substring(at);
    }
}
