package web;

import utils.AuthUtil;
import utils.DBUtil;
import org.mindrot.jbcrypt.BCrypt;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

/**
 * ⚠️ INTENTIONALLY VULNERABLE CODE FOR EDUCATIONAL PURPOSES ⚠️
 * 
 * This servlet demonstrates CSRF (Cross-Site Request Forgery) vulnerabilities.
 * Used for web security course (Project subject: Web Application Security)
 * 
 * The problem: State-changing operations (POST, PUT, DELETE) don't require CSRF tokens,
 * so attackers can forge requests from other websites.
 * 
 * DO NOT USE IN PRODUCTION - This is for learning CSRF attacks only.
 */
@WebServlet(name = "VulnerableCSRFServlet", urlPatterns = {
        "/api/csrf-demo/*"
})
public class VulnerableCSRFServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getPathInfo();
        Map<String, Object> response = new HashMap<>();

        try {
            // ⚠️ VULNERABLE: Only checks JWT token, NOT CSRF token
            // An attacker can forge a request from another domain
            Long userId = AuthUtil.resolveUserId(req);
            
            if (userId == null) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.put("error", "User not authenticated");
                resp.getWriter().write(gson.toJson(response));
                return;
            }

            // Check for CSRF token - MISSING! This is the vulnerability
            String csrfToken = req.getParameter("_csrf");
            String csrfFromSession = (String) req.getSession().getAttribute("_csrf_token");
            
            // ⚠️ VULNERABLE: No validation of CSRF token
            // Even if parameters exist, they're not validated
            // The code proceeds without checking
            System.out.println("[VULNERABLE CSRF] User: " + userId + " attempting action: " + path);

            switch (path) {
                case "/change-password":
                    handleChangePassword(req, resp, response, userId);
                    break;
                case "/delete-account":
                    handleDeleteAccount(req, resp, response, userId);
                    break;
                case "/update-address":
                    handleUpdateAddress(req, resp, response, userId);
                    break;
                case "/transfer-money":
                    handleTransferMoney(req, resp, response, userId);
                    break;
                default:
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.put("error", "Action not found");
                    resp.getWriter().write(gson.toJson(response));
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.put("error", e.getMessage());
            resp.getWriter().write(gson.toJson(response));
        }
    }

    /**
     * ⚠️ VULNERABLE: Change password without CSRF protection
     * 
     * CSRF Attack Example:
     * 1. Admin logs into banking website
     * 2. Admin visits attacker's website (still logged in with admin account)
     * 3. Attacker's page has hidden form that POST to:
     *    POST /api/csrf-demo/change-password?newPassword=hacked&confirmPassword=hacked
     * 4. Admin's password is changed without their knowledge
     * 
     * HTML Attack:
     * <img src="http://localhost:8443/api/csrf-demo/change-password?newPassword=hacked&confirmPassword=hacked" />
     * or
     * <form method="POST" action="http://localhost:8443/api/csrf-demo/change-password">
     *   <input name="newPassword" value="hacked" />
     *   <input name="confirmPassword" value="hacked" />
     *   <script>document.forms[0].submit();</script>
     * </form>
     */
    private void handleChangePassword(HttpServletRequest req, HttpServletResponse resp,
                                     Map<String, Object> response, Long userId) throws IOException {
        String newPassword = req.getParameter("newPassword");
        String confirmPassword = req.getParameter("confirmPassword");

        if (newPassword == null || newPassword.isEmpty() || 
            !newPassword.equals(confirmPassword)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.put("error", "Passwords do not match or are empty");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        // ⚠️ VULNERABLE: No CSRF validation before changing password
        // ACTUALLY UPDATE DATABASE
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE users SET password_hash = ? WHERE id = ?")) {
            
            // Hash password using BCrypt
            String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
            stmt.setString(1, hashedPassword);
            stmt.setLong(2, userId);
            
            int updated = stmt.executeUpdate();
            
            if (updated > 0) {
                System.out.println("[CSRF SUCCESS] User " + userId + " password ACTUALLY changed!");
                response.put("success", true);
                response.put("message", "Password changed successfully");
                response.put("warning", "[SECURITY] This should have been protected with CSRF token!");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.put("error", "User not found");
            }
            resp.getWriter().write(gson.toJson(response));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.put("error", "Database error: " + e.getMessage());
            resp.getWriter().write(gson.toJson(response));
        }
    }

    /**
     * ⚠️ VULNERABLE: Delete account without CSRF protection
     * 
     * CSRF Attack: Account Deletion
     * Attacker can trick user into clicking link that deletes their account:
     * <a href="http://localhost:8443/api/csrf-demo/delete-account?confirm=true">
     *   Click to claim free gift!
     * </a>
     */
    private void handleDeleteAccount(HttpServletRequest req, HttpServletResponse resp,
                                    Map<String, Object> response, Long userId) throws IOException {
        String confirm = req.getParameter("confirm");

        if (!"true".equals(confirm)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.put("error", "Confirm parameter required");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        // ⚠️ VULNERABLE: No CSRF validation before deleting account
        // ACTUALLY DELETE FROM DATABASE
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            
            stmt.setLong(1, userId);
            int deleted = stmt.executeUpdate();
            
            if (deleted > 0) {
                System.out.println("[CSRF SUCCESS] User " + userId + " account ACTUALLY deleted!");
                response.put("success", true);
                response.put("message", "Account deleted permanently");
                response.put("warning", "[SECURITY] Account deletion should require CSRF token!");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.put("error", "User not found");
            }
            resp.getWriter().write(gson.toJson(response));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.put("error", "Database error: " + e.getMessage());
            resp.getWriter().write(gson.toJson(response));
        }
    }

    /**
     * ⚠️ VULNERABLE: Update address without CSRF protection
     * 
     * CSRF Attack: Change Shipping Address
     * Attacker can change user's default shipping address to attacker's address,
     * then orders will be shipped to attacker!
     */
    private void handleUpdateAddress(HttpServletRequest req, HttpServletResponse resp,
                                    Map<String, Object> response, Long userId) throws IOException {
        String newAddress = req.getParameter("address");
        String city = req.getParameter("city");
        String country = req.getParameter("country");

        if (newAddress == null || newAddress.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.put("error", "Address is required");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        // ⚠️ VULNERABLE: No CSRF validation before updating address
        // ACTUALLY UPDATE DATABASE - Update shipping_address in users table
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE users SET shipping_address = ?, shipping_city = ?, shipping_country = ? WHERE id = ?")) {
            
            stmt.setString(1, newAddress);
            stmt.setString(2, city != null ? city : "");
            stmt.setString(3, country != null ? country : "");
            stmt.setLong(4, userId);
            
            int updated = stmt.executeUpdate();
            
            if (updated > 0) {
                System.out.println("[CSRF SUCCESS] User " + userId + " address ACTUALLY changed to: " + 
                                 newAddress + ", " + city + ", " + country);
                response.put("success", true);
                response.put("message", "Address updated successfully");
                response.put("warning", "[SECURITY] Address change should require CSRF token!");
                response.put("newAddress", newAddress);
                response.put("city", city);
                response.put("country", country);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.put("error", "User not found");
            }
            resp.getWriter().write(gson.toJson(response));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.put("error", "Database error: " + e.getMessage());
            resp.getWriter().write(gson.toJson(response));
        }
    }

    /**
     * ⚠️ VULNERABLE: Transfer money (hypothetical banking feature)
     * 
     * CSRF Attack: Money Transfer
     * Most common CSRF attack in real banks. Attacker can make user transfer money
     * from their account to attacker's account.
     */
    private void handleTransferMoney(HttpServletRequest req, HttpServletResponse resp,
                                    Map<String, Object> response, Long userId) throws IOException {
        String toAccount = req.getParameter("toAccount");
        String amount = req.getParameter("amount");

        if (toAccount == null || amount == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.put("error", "toAccount and amount are required");
            resp.getWriter().write(gson.toJson(response));
            return;
        }

        // ⚠️ VULNERABLE: No CSRF validation before transferring money
        // Simulate deducting balance from current user
        try {
            double transferAmount = Double.parseDouble(amount);
            if (transferAmount <= 0) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.put("error", "Amount must be positive");
                resp.getWriter().write(gson.toJson(response));
                return;
            }
            
            // Log transfer in comments - for educational purposes, 
            // we're just demonstrating that CSRF can execute this
            System.out.println("[CSRF SUCCESS] User " + userId + " transferred " + transferAmount + 
                             " to account: " + toAccount);
            
            response.put("success", true);
            response.put("message", "Money transferred successfully");
            response.put("warning", "[SECURITY] Money transfer should require CSRF token!");
            response.put("from", userId);
            response.put("to", toAccount);
            response.put("amount", transferAmount);
            resp.getWriter().write(gson.toJson(response));
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.put("error", "Invalid amount format");
            resp.getWriter().write(gson.toJson(response));
        }
    }
}
