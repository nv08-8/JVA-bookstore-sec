package filters;

import utils.JwtUtil;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebFilter(filterName = "JwtFilter", urlPatterns = {"/api/*"})
public class JwtFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization if needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String requestUri = req.getRequestURI();
        String contextPath = req.getContextPath() != null ? req.getContextPath() : "";
        String path = requestUri.substring(contextPath.length());
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        System.out.println("JwtFilter: Request URI = " + requestUri + " | normalized path = " + path);

        if (allowsAdminSecretBypass(path, req) || isPublicEndpoint(path, req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Check if user is authenticated via session (for logged-in users)
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("user_id") != null) {
            chain.doFilter(request, response);
            return;
        }

        // Fallback to JWT token from Authorization header
        String authHeader = req.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ") && authHeader.length() > 7) {
            String token = authHeader.substring(7);
            String user = JwtUtil.validateToken(token);
            if (user != null) {
                chain.doFilter(request, response);
                return;
            }
        }

        // Fallback to JWT token from cookie
        if (req.getCookies() != null) {
            for (javax.servlet.http.Cookie cookie : req.getCookies()) {
                if ("auth_token".equals(cookie.getName())) {
                    String user = JwtUtil.validateToken(cookie.getValue());
                    if (user != null) {
                        chain.doFilter(request, response);
                        return;
                    }
                }
            }
        }

        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json");
        resp.getWriter().write("{\"error\":\"Unauthorized\"}");
    }

    @Override
    public void destroy() {
        // Cleanup if needed
    }

    private boolean isPublicEndpoint(String path, String method) {
        if (path == null) {
            return false;
        }

        // Allow cart API và local lab endpoints cho khách vãng lai.
        if (path.equals("/api/cart") || path.startsWith("/api/cart/")
                || path.startsWith("/api/account/preferences/")) {
            return true;
        }

        // Core auth endpoints remain public
        switch (path) {
            case "/api/auth/register":
            case "/api/auth/login":
            case "/api/login":
            case "/api/auth/send-otp":
            case "/api/auth/verify-otp":
            case "/api/auth/reset-password":
            case "/api/auth/reset":
            case "/api/auth/verify":
            case "/api/auth/register-quick":
            case "/api/test-email":
            case "/api/health":
                return true;
            default:
                break;
        }

        // Allow book import endpoint
        if (path.equals("/api/books/import") && "POST".equalsIgnoreCase(method)) {
            return true;
        }

        // Allow anyone to browse catalog and category metadata.
        if ("GET".equalsIgnoreCase(method)) {
            if (path.equals("/api/books") || path.startsWith("/api/books/")) {
                return true;
            }
            if (path.equals("/api/books/download")) {
                return true;
            }
            if (path.equals("/api/profile/user-info")) {
                return true;
            }
            if (path.equals("/api/profile/export")) {
                return true;
            }
            if (path.equals("/api/catalog") || path.startsWith("/api/catalog/")) {
                return true;
            }
            if (path.equals("/api/reviews") || (path.startsWith("/api/reviews/") && !path.equals("/api/reviews/me"))) {
                return true;
            }
            if (path.equals("/api/comments") || (path.startsWith("/api/comments/") && !path.matches("/api/comments/.*\\d+"))) {
                return true;
            }
        }

        return false;
    }

    private boolean allowsAdminSecretBypass(String path, HttpServletRequest request) {
        if (path == null || !path.startsWith("/api/admin/orders")) {
            return false;
        }
        String expected = getAdminSecret();
        if (expected == null) {
            return false;
        }
        String paramSecret = trimToNull(request.getParameter("secret"));
        if (expected.equals(paramSecret)) {
            return true;
        }
        String headerSecret = trimToNull(request.getHeader("X-Admin-Secret"));
        return expected.equals(headerSecret);
    }

    private String getAdminSecret() {
        String env = System.getenv("ADMIN_PANEL_SECRET");
        if (env != null) {
            env = env.trim();
            if (!env.isEmpty()) {
                return env;
            }
        }
        // NO DEFAULT SECRET - MUST BE SET IN ENVIRONMENT VARIABLE
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
