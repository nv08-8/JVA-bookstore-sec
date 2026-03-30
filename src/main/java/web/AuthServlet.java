package web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import utils.JwtUtil;
import utils.DBUtil;
import utils.EmailUtil;
import utils.OTPUtil;
import utils.PasswordValidator;
import utils.SecurityManager;
import dao.ShopDAO;
import org.mindrot.jbcrypt.BCrypt;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

@WebServlet(name = "AuthServlet", urlPatterns = {
        "/api/login",
        "/api/auth/register",
        "/api/auth/send-otp",
        "/api/auth/verify-otp",
        "/api/auth/reset-password"
})
public class AuthServlet extends HttpServlet {

    private static final String ATTR_JSON_BODY = "AUTH_SERVLET_JSON_BODY";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");
        PrintWriter out = resp.getWriter();
        String email = req.getParameter("email");
        if (email == null || email.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write("{\"error\":\"Email is required\"}");
            return;
        }
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, email, full_name, role, status FROM users WHERE email = ?")) {
            stmt.setString(1, email.trim());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                out.write("{\"exists\":true,\"email\":\"" + rs.getString("email") + "\",\"name\":\"" + rs.getString("full_name") + "\",\"role\":\"" + rs.getString("role") + "\"}");
            } else {
                out.write("{\"exists\":false}");
            }
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"error\":\"Internal server error\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        System.out.println("DEBUG AuthServlet - doPost called, path: " + req.getServletPath());
        resp.setContentType("application/json");
        String path = req.getServletPath();
        PrintWriter out = resp.getWriter();
        try {
            if ("/api/login".equals(path)) {
                handleLogin(req, resp, out);
            } else if ("/api/auth/send-otp".equals(path)) {
                handleSendOTP(req, resp, out);
            } else if ("/api/auth/verify-otp".equals(path)) {
                handleVerifyOTP(req, resp, out);
            } else if ("/api/auth/register".equals(path)) {
                handleRegister(req, resp, out);
            } else if ("/api/auth/reset-password".equals(path)) {
                handleResetPassword(req, resp, out);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.write("{\"error\":\"Endpoint not found\"}");
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"error\":\"Internal server error\"}");
        } finally {
            out.flush();
        }
    }

    private void handleLogin(HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
            throws IOException, SQLException {
        try {
            String username = req.getParameter("username");
            String password = req.getParameter("password");
            String clientIp = req.getRemoteAddr();
            String userAgent = req.getHeader("User-Agent");

            if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.write("{\"error\":\"Username and password required\"}");
                return;
            }

            // Truy vấn thông tin user từ database
            String sql = "SELECT id, username, email, password_hash, role, status FROM users WHERE username = '" + username + "'";
            String dbHash = null;
            String dbEmail = null;
            String role = null;
            String status = null;
            int userId = 0;

            try (Connection conn = DBUtil.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    userId = rs.getInt("id");
                    dbEmail = rs.getString("email");
                    dbHash = rs.getString("password_hash");
                    role = rs.getString("role");
                    status = rs.getString("status");
                    username = rs.getString("username");
                } else {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    out.write("{\"error\":\"Invalid credentials\"}");
                    SecurityManager.recordFailedLogin(username, clientIp, userAgent, "User not found");
                    return;
                }
            }

            // Check if account is locked
            if (SecurityManager.isAccountLocked(username)) {
                long minutesRemaining = SecurityManager.getAccountLockRemainingMinutes(username);
                resp.setStatus(423);
                out.write("{\"error\":\"Tài khoản của bạn đã bị khóa. Vui lòng thử lại sau " + minutesRemaining + " phút\"}");
                return;
            }

            // Kiểm tra mật khẩu (bỏ qua nếu tài khoản chưa đặt mật khẩu)
            if (dbHash != null && !dbHash.trim().isEmpty()) {
                if (!BCrypt.checkpw(password, dbHash)) {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    out.write("{\"error\":\"Invalid credentials\"}");
                    SecurityManager.recordFailedLogin(username, clientIp, userAgent, "Invalid password");
                    return;
                }
            }

            if ("inactive".equalsIgnoreCase(status)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.write("{\"error\":\"Tài khoản của bạn đang bị tạm khóa\"}");
                return;
            } else if ("banned".equalsIgnoreCase(status)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.write("{\"error\":\"Tài khoản của bạn đã bị cấm\"}");
                return;
            }

            String subject = dbEmail != null && !dbEmail.trim().isEmpty() ? dbEmail : username;
            String token = JwtUtil.generateToken(subject);

            // Record successful login
            SecurityManager.recordSuccessfulLogin(username, clientIp, userAgent);

            // Lưu session cho JSP
            HttpSession session = req.getSession(true);
            session.setAttribute("username", username);
            session.setAttribute("role", role);
            session.setAttribute("user_id", userId);
            session.setAttribute("token", token);

            // Lưu token vào cookie để duy trì đăng nhập giữa các tab
            resp.addHeader("Set-Cookie", "auth_token=" + token + "; Path=/; Max-Age=86400; SameSite=None; Secure");

            String sellerStatus = null;
            if ("seller".equals(role)) {
                try {
                    sellerStatus = DBUtil.getUserStatus(username);
                    int shopId = ShopDAO.getShopIdByUserId(userId);
                    if (shopId > 0) {
                        session.setAttribute("shop_id", shopId);
                    }
                } catch (Exception e) {
                    System.out.println("Login - Failed to get seller info: " + e.getMessage());
                }
            }

            // Trả JSON phản hồi theo role
            String response;
            if ("admin".equals(role)) {
                response = "{\"token\":\"" + token + "\",\"message\":\"Login successful\",\"role\":\"admin\",\"redirect\":\"/admin-dashboard\"}";
            } else if ("seller".equals(role)) {
                if ("active".equalsIgnoreCase(sellerStatus)) {
                    response = "{\"token\":\"" + token + "\",\"message\":\"Login successful\",\"role\":\"seller\",\"redirect\":\"/seller/dashboard\"}";
                } else {
                    response = "{\"token\":\"" + token + "\",\"message\":\"Login successful\",\"role\":\"seller\",\"redirect\":\"/seller/pending\"}";
                }
            } else if ("shipper".equals(role)) {
                response = "{\"token\":\"" + token + "\",\"message\":\"Login successful\",\"role\":\"shipper\",\"redirect\":\"/dashboard-shipper.jsp\"}";
            } else {
                response = "{\"token\":\"" + token + "\",\"message\":\"Login successful\",\"role\":\"customer\"}";
            }
            out.write(response);

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"error\":\"Login failed\"}");
        }
    }

    private void handleRegister(HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
            throws IOException, SQLException {
        String email = extractParam(req, "email");
        String username = extractParam(req, "username");
        String password = extractParam(req, "password");

        if (email == null || email.isEmpty() || username == null || username.isEmpty()
                || password == null || password.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write("{\"error\":\"Email, username, and password required\"}");
            return;
        }

        // Validate password complexity
        PasswordValidator.PasswordRequirement pwRequirement = PasswordValidator.validatePassword(password);
        if (!pwRequirement.valid) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write("{\"error\":\"" + pwRequirement.message + "\"}");
            return;
        }

        if (DBUtil.userExists(username)) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            out.write("{\"error\":\"Username already exists\"}");
            return;
        }

        if (DBUtil.emailExists(email)) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            out.write("{\"error\":\"Email already registered\"}");
            return;
        }

        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        DBUtil.createUserVerified(username, email, hash);

        try {
            int userId = DBUtil.getUserIdByUsername(username);
            DBUtil.updateUserRole(userId, "customer", "active");
            // Set password expiration for new users
            SecurityManager.updatePasswordWithExpiration(username, hash);
        } catch (SQLException e) {
            System.err.println("Failed to set user role: " + e.getMessage());
        }

        try {
            EmailUtil.sendWelcomeEmail(email, username);
        } catch (Exception e) {
            System.err.println("Failed to send welcome email: " + e.getMessage());
        }

        out.write("{\"message\":\"Registration successful! You can now login.\"}");
    }

    private void handleResetPassword(HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
            throws IOException, SQLException {
        String email = req.getParameter("email");
        if (email == null || email.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write("{\"error\":\"Email required\"}");
            return;
        }

        if (!DBUtil.emailExists(email)) {
            JsonObject payload = new JsonObject();
            payload.addProperty("message", "If the email exists, a reset link has been sent.");
            out.write(payload.toString());
            return;
        }

        String username = DBUtil.getUserByEmail(email);
        String resetToken = UUID.randomUUID().toString();
        boolean tokenStored = DBUtil.setResetToken(email, resetToken);
        JsonObject payload = new JsonObject();
        payload.addProperty("message", "If the email exists, a reset link has been sent.");

        if (tokenStored) {
            try {
                EmailUtil.sendResetEmail(email, resetToken, username);
            } catch (RuntimeException mailEx) {
                System.err.println("Reset mail failed: " + mailEx.getMessage());
            }
        }
        out.write(payload.toString());
    }

    private void handleSendOTP(HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
            throws IOException, SQLException {
        String email = req.getParameter("email");
        if (email == null || email.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write("{\"error\":\"Email is required\"}");
            return;
        }

        if (!OTPUtil.canRequestNewOTP(email)) {
            long remaining = OTPUtil.getRemainingCooldownSeconds(email);
            resp.setStatus(429);
            out.write("{\"error\":\"Please wait " + remaining + " seconds before requesting new OTP\"}");
            return;
        }

        String otp = OTPUtil.generateOTP();
        if (OTPUtil.storeOTP(email, otp)) {
            try {
                EmailUtil.sendOTPEmail(email, otp);
                out.write("{\"message\":\"OTP sent successfully\"}");
            } catch (Exception e) {
                out.write("{\"message\":\"OTP generated (email disabled)\",\"debugOtp\":\"" + otp + "\"}");
            }
        } else {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"error\":\"Failed to generate OTP\"}");
        }
    }

    private void handleVerifyOTP(HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
            throws IOException, SQLException {
        String email = extractParam(req, "email");
        String otp = extractParam(req, "otp");
        String username = extractParam(req, "username");
        String password = extractParam(req, "password");

        if (email == null || otp == null || username == null || password == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write("{\"error\":\"Email, OTP, username, and password are required\"}");
            return;
        }

        if (OTPUtil.verifyOTP(email, otp)) {
            // Validate password complexity
            PasswordValidator.PasswordRequirement pwRequirement = PasswordValidator.validatePassword(password);
            if (!pwRequirement.valid) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.write("{\"error\":\"" + pwRequirement.message + "\"}");
                return;
            }
            
            if (DBUtil.userExists(username)) {
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
                out.write("{\"error\":\"Username already exists\"}");
                return;
            }
            if (DBUtil.emailExists(email)) {
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
                out.write("{\"error\":\"Email already registered\"}");
                return;
            }

            String hash = BCrypt.hashpw(password, BCrypt.gensalt());
            DBUtil.createUserVerified(username, email, hash);

            try {
                int userId = DBUtil.getUserIdByUsername(username);
                DBUtil.updateUserRole(userId, "customer", "active");
                // Set password expiration for new users
                SecurityManager.updatePasswordWithExpiration(username, hash);
            } catch (SQLException e) {
                System.err.println("Failed to set user status: " + e.getMessage());
            }

            try {
                EmailUtil.sendWelcomeEmail(email, username);
            } catch (Exception e) {
                System.err.println("Failed to send welcome email: " + e.getMessage());
            }

            out.write("{\"message\":\"Registration successful! You can now login.\"}");
        } else {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.write("{\"error\":\"Invalid or expired OTP.\"}");
        }
    }

    private String extractParam(HttpServletRequest req, String name) throws IOException {
        String value = req.getParameter(name);
        if (value != null && !value.trim().isEmpty()) return value.trim();

        JsonObject json = getJsonBody(req);
        if (json != null && json.has(name)) {
            String val = json.get(name).getAsString();
            if (val != null && !val.trim().isEmpty()) return val.trim();
        }
        return null;
    }

    private JsonObject getJsonBody(HttpServletRequest req) throws IOException {
        Object cached = req.getAttribute(ATTR_JSON_BODY);
        if (cached instanceof JsonObject) return (JsonObject) cached;
        if (Boolean.FALSE.equals(cached)) return null;

        String contentType = req.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            req.setAttribute(ATTR_JSON_BODY, Boolean.FALSE);
            return null;
        }

        StringBuilder jsonPayload = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) jsonPayload.append(line);
        }

        if (jsonPayload.length() == 0) {
            req.setAttribute(ATTR_JSON_BODY, Boolean.FALSE);
            return null;
        }

        try {
            JsonObject json = JsonParser.parseString(jsonPayload.toString()).getAsJsonObject();
            req.setAttribute(ATTR_JSON_BODY, json);
            return json;
        } catch (Exception e) {
            req.setAttribute(ATTR_JSON_BODY, Boolean.FALSE);
            System.err.println("AuthServlet - Failed to parse JSON body: " + e.getMessage());
            return null;
        }
    }
}
