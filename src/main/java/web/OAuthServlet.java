package web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import utils.DBUtil;
import utils.JwtUtil;
import org.mindrot.jbcrypt.BCrypt;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.UUID;

/**
 * OAuth2/SSO Authentication Servlet
 * Supports multiple OAuth providers: Google, Facebook, GitHub, Microsoft, etc.
 */
@WebServlet(name = "OAuthServlet", urlPatterns = {
        "/api/oauth/callback",
        "/api/oauth/config",
        "/api/oauth/link",
        "/api/oauth/unlink"
})
public class OAuthServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        String path = req.getServletPath();
        PrintWriter out = resp.getWriter();
        
        try {
            if ("/api/oauth/callback".equals(path)) {
                handleOAuthCallback(req, resp, out);
            } else if ("/api/oauth/config".equals(path)) {
                handleGetOAuthConfig(req, resp, out);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.write("{\"error\":\"Endpoint not found\"}");
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"error\":\"" + e.getMessage() + "\"}");
        } finally {
            out.flush();
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        String path = req.getServletPath();
        PrintWriter out = resp.getWriter();
        
        try {
            if ("/api/oauth/link".equals(path)) {
                handleLinkOAuthAccount(req, resp, out);
            } else if ("/api/oauth/unlink".equals(path)) {
                handleUnlinkOAuthAccount(req, resp, out);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.write("{\"error\":\"Endpoint not found\"}");
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"error\":\"" + e.getMessage() + "\"}");
        } finally {
            out.flush();
        }
    }
    
    /**
     * Handle OAuth callback from provider
     * This endpoint receives the authorization code from the OAuth provider
     */
    private void handleOAuthCallback(HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
            throws IOException, SQLException {
        String code = req.getParameter("code");
        String state = req.getParameter("state");
        String provider = req.getParameter("provider");
        String error = req.getParameter("error");
        
        if (error != null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.write("{\"error\":\"OAuth authorization failed: " + error + "\"}");
            return;
        }
        
        if (code == null || provider == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write("{\"error\":\"Missing code or provider parameter\"}");
            return;
        }
        
        try {
            // Exchange authorization code for access token
            // This is provider-specific and would need implementation for each provider
            String accessToken = exchangeCodeForToken(code, provider);
            
            if (accessToken == null) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.write("{\"error\":\"Failed to obtain access token\"}");
                return;
            }
            
            // Get user profile from OAuth provider
            OAuthUserProfile profile = getUserProfileFromProvider(provider, accessToken);
            
            if (profile == null) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.write("{\"error\":\"Failed to get user profile from provider\"}");
                return;
            }
            
            // Find or create user in our database
            Long userId = findOrCreateOAuthUser(provider, profile);
            
            if (userId == null) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.write("{\"error\":\"Failed to process OAuth authentication\"}");
                return;
            }
            
            // Generate JWT token
            String username = DBUtil.getUsernameById(userId);
            String email = DBUtil.getEmailById(userId);
            String token = JwtUtil.generateToken(email);
            
            // Create session
            HttpSession session = req.getSession(true);
            session.setAttribute("user_id", userId);
            session.setAttribute("username", username);
            session.setAttribute("token", token);
            
            // Return token and user info
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("token", token);
            response.addProperty("message", "OAuth login successful");
            response.addProperty("user_id", userId);
            response.addProperty("username", username);
            out.write(response.toString());
            
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"error\":\"OAuth callback error: " + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Get available OAuth provider configurations
     */
    private void handleGetOAuthConfig(HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
            throws SQLException {
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        
        // Get enabled OAuth providers from database
        // This would return configuration URLs for each enabled provider
        response.addProperty("google_enabled", isOAuthProviderEnabled("google"));
        response.addProperty("facebook_enabled", isOAuthProviderEnabled("facebook"));
        response.addProperty("github_enabled", isOAuthProviderEnabled("github"));
        response.addProperty("microsoft_enabled", isOAuthProviderEnabled("microsoft"));
        
        out.write(response.toString());
    }
    
    /**
     * Link an OAuth account to an existing user account
     */
    private void handleLinkOAuthAccount(HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
            throws IOException {
        // Get current user from session/JWT
        // Link OAuth account to their account
        out.write("{\"message\":\"OAuth account linking not yet implemented\"}");
    }
    
    /**
     * Unlink an OAuth account from a user account
     */
    private void handleUnlinkOAuthAccount(HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
            throws IOException {
        // Get current user from session/JWT
        // Unlink OAuth account from their account
        out.write("{\"message\":\"OAuth account unlinking not yet implemented\"}");
    }
    
    /**
     * Exchange authorization code for access token
     * Provider-specific implementation needed
     */
    private String exchangeCodeForToken(String code, String provider) {
        // TODO: Implement for each provider
        // - Google: https://oauth2.googleapis.com/token
        // - Facebook: https://graph.facebook.com/v12.0/oauth/access_token
        // - GitHub: https://github.com/login/oauth/access_token
        // - Microsoft: https://login.microsoftonline.com/common/oauth2/v2.0/token
        return null;
    }
    
    /**
     * Get user profile from OAuth provider
     */
    private OAuthUserProfile getUserProfileFromProvider(String provider, String accessToken) {
        // TODO: Implement for each provider
        // - Google: https://www.googleapis.com/oauth2/v2/userinfo
        // - Facebook: https://graph.facebook.com/me?fields=id,name,email,picture
        // - GitHub: https://api.github.com/user
        // - Microsoft: https://graph.microsoft.com/v1.0/me
        return null;
    }
    
    /**
     * Find existing OAuth account or create new user
     */
    private Long findOrCreateOAuthUser(String provider, OAuthUserProfile profile) {
        try {
            // Check if OAuth account already exists
            Long userId = DBUtil.findOAuthAccount(provider, profile.getProviderId());
            
            if (userId != null) {
                // Account exists, just update last login
                DBUtil.updateOAuthLastLogin(userId, provider);
                return userId;
            }
            
            // Check if email already exists in our system
            if (profile.getEmail() != null) {
                Long existingUserId = DBUtil.getUserIdByEmail(profile.getEmail());
                if (existingUserId != null) {
                    // Link OAuth account to existing user
                    DBUtil.createOAuthAccount(existingUserId, provider, profile);
                    return existingUserId;
                }
            }
            
            // Create new user account
            String username = generateUniqueUsername(profile);
            String randomPassword = UUID.randomUUID().toString(); // OAuth users don't need this
            String email = profile.getEmail() != null ? profile.getEmail() : username + "@oauth.local";
            String passwordHash = BCrypt.hashpw(randomPassword, BCrypt.gensalt());
            
            userId = DBUtil.createOAuthUser(username, email, passwordHash);
            DBUtil.createOAuthAccount(userId, provider, profile);
            
            return userId;
            
        } catch (SQLException e) {
            System.err.println("Error creating/finding OAuth user: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Generate unique username from OAuth profile
     */
    private String generateUniqueUsername(OAuthUserProfile profile) throws SQLException {
        String baseName = profile.getName() != null ? profile.getName().replaceAll("\\s+", "_") : "user_" + UUID.randomUUID().toString().substring(0, 8);
        String username = baseName;
        int counter = 1;
        
        while (DBUtil.userExists(username)) {
            username = baseName + "_" + counter;
            counter++;
        }
        
        return username;
    }
    
    /**
     * Check if OAuth provider is enabled
     */
    private boolean isOAuthProviderEnabled(String provider) {
        try {
            return DBUtil.isOAuthProviderEnabled(provider);
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * Inner class to represent OAuth user profile
     */
    public static class OAuthUserProfile {
        private String providerId;
        private String email;
        private String name;
        private String picture;
        private String locale;
        
        public OAuthUserProfile(String providerId, String email, String name, String picture, String locale) {
            this.providerId = providerId;
            this.email = email;
            this.name = name;
            this.picture = picture;
            this.locale = locale;
        }
        
        public String getProviderId() { return providerId; }
        public String getEmail() { return email; }
        public String getName() { return name; }
        public String getPicture() { return picture; }
        public String getLocale() { return locale; }
    }
}
