package filters;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collection;

/**
 * Ensures all requests and responses use UTF-8 so Vietnamese characters render correctly everywhere.
 * Đồng thời:
 *  - Thêm security headers chống Clickjacking vào mọi response.
 *  - Tự động gắn SameSite=Strict; HttpOnly vào cookie JSESSIONID (chống CSRF).
 */
public class EncodingFilter implements Filter {

    private static final String UTF8 = "UTF-8";

    /**
     * Wrapper chặn Set-Cookie header của JSESSIONID và gắn thêm SameSite=Strict; HttpOnly
     * để trình duyệt không gửi session cookie kèm cross-site request.
     */
    private static class SameSiteWrapper extends HttpServletResponseWrapper {
        SameSiteWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, patchJsessionid(name, value));
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, patchJsessionid(name, value));
        }

        private String patchJsessionid(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name) && value != null && value.contains("JSESSIONID")) {
                if (!value.contains("SameSite")) {
                    value = value + "; SameSite=Strict";
                }
                if (!value.contains("HttpOnly")) {
                    value = value + "; HttpOnly";
                }
            }
            return value;
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // No initialization needed.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request.getCharacterEncoding() == null || !UTF8.equalsIgnoreCase(request.getCharacterEncoding())) {
            request.setCharacterEncoding(UTF8);
        }
        response.setCharacterEncoding(UTF8);
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpResp = new SameSiteWrapper((HttpServletResponse) response);

            // Generate nonce cho CSP
            String nonce = generateNonce();
            if (request instanceof HttpServletRequest) {
                ((HttpServletRequest) request).setAttribute("csp_nonce", nonce);
            }

            // CSP Policy với nonce - bao gồm tất cả các directive
            String csp = "default-src 'self'; " +
                    "script-src 'self' 'nonce-" + nonce + "' https://code.jquery.com https://cdn.jsdelivr.net https://cdn.tailwindcss.com https://unpkg.com https://cdnjs.cloudflare.com; " +
                    "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com https://cdnjs.cloudflare.com; " +
                    "img-src 'self' data: https://static.photos https://salt.tikicdn.com https://github.com https://cdnjs.cloudflare.com; " +
                    "font-src 'self' data: https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
                    "connect-src 'self' https://localhost https://unpkg.com https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                    "media-src 'self'; " +
                    "object-src 'none'; " +
                    "frame-ancestors 'none'; " +
                    "base-uri 'self'; " +
                    "form-action 'self'; " +
                    "upgrade-insecure-requests";

            httpResp.setHeader("Content-Security-Policy", csp);

            // Chống Timestamp Disclosure: ẩn Server header
            httpResp.setHeader("Server", "Bookish");

            // Chống Clickjacking: cấm nhúng trang vào iframe từ bất kỳ origin nào
            httpResp.setHeader("X-Frame-Options", "DENY");

            // Các security headers khác
            httpResp.setHeader("X-Content-Type-Options", "nosniff");
            httpResp.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            httpResp.setHeader("X-XSS-Protection", "1; mode=block");
            httpResp.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            httpResp.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");

            // Cache-Control: ngăn trình duyệt/proxy cache response chứa dữ liệu nhạy cảm
            // Áp dụng cho API endpoints; static resource tự quản lý cache của mình
            if (request instanceof HttpServletRequest) {
                String uri = ((HttpServletRequest) request).getRequestURI();
                if (uri != null && uri.contains("/api/")) {
                    httpResp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
                    httpResp.setHeader("Pragma", "no-cache");
                }
            }

            String contentType = httpResp.getContentType();
            if (contentType != null && contentType.startsWith("text/")) {
                httpResp.setContentType(contentType.split(";")[0] + "; charset=" + UTF8);
            }

            chain.doFilter(request, httpResp);
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // Nothing to clean up.
    }

    /**
     * Generate random nonce (32 bytes) encoded as Base64 cho CSP
     */
    private String generateNonce() {
        try {
            SecureRandom random = SecureRandom.getInstanceStrong();
            byte[] nonceBytes = new byte[32];
            random.nextBytes(nonceBytes);
            return Base64.getEncoder().encodeToString(nonceBytes);
        } catch (Exception e) {
            // Fallback nếu không thể tạo SecureRandom
            SecureRandom random = new SecureRandom();
            byte[] nonceBytes = new byte[32];
            random.nextBytes(nonceBytes);
            return Base64.getEncoder().encodeToString(nonceBytes);
        }
    }
}
