package filters;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
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

            // Chống Clickjacking: cấm nhúng trang vào iframe từ bất kỳ origin nào
            httpResp.setHeader("X-Frame-Options", "DENY");
            httpResp.setHeader("Content-Security-Policy", "frame-ancestors 'none'");

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
}
