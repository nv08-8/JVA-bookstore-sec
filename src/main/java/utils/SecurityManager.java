package utils;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Utility class for managing security features like login lockouts, password expiration, etc.
 */
public class SecurityManager {
    
    /**
     * Record a failed login attempt
     */
    public static void recordFailedLogin(String username, String ipAddress, String userAgent, String reason) {
        try (Connection conn = DBUtil.getConnection()) {
            // Get user ID
            Integer userId = null;
            String getUserIdSql = "SELECT id FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(getUserIdSql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getInt("id");
                    }
                }
            }
            
            // Record the failed attempt
            String insertSql = "INSERT INTO login_audit (user_id, username, ip_address, user_agent, login_status, failure_reason) " +
                             "VALUES (?, ?, ?, ?, 'failed', ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setObject(1, userId, Types.INTEGER);
                stmt.setString(2, username);
                stmt.setString(3, ipAddress);
                stmt.setString(4, userAgent);
                stmt.setString(5, reason);
                stmt.executeUpdate();
            }
            
            // Increment failed login attempts
            String updateSql = "UPDATE users SET failed_login_attempts = failed_login_attempts + 1 WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setString(1, username);
                stmt.executeUpdate();
            }
            
            // Check if we should lock the account
            checkAndLockAccount(conn, username);
            
        } catch (SQLException e) {
            System.err.println("Error recording failed login: " + e.getMessage());
        }
    }
    
    /**
     * Record a successful login attempt
     */
    public static void recordSuccessfulLogin(String username, String ipAddress, String userAgent) {
        try (Connection conn = DBUtil.getConnection()) {
            // Get user ID
            Integer userId = null;
            String getUserIdSql = "SELECT id FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(getUserIdSql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getInt("id");
                    }
                }
            }
            
            // Record the successful attempt
            String insertSql = "INSERT INTO login_audit (user_id, username, ip_address, user_agent, login_status) " +
                             "VALUES (?, ?, ?, ?, 'success')";
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setObject(1, userId, Types.INTEGER);
                stmt.setString(2, username);
                stmt.setString(3, ipAddress);
                stmt.setString(4, userAgent);
                stmt.executeUpdate();
            }
            
            // Reset failed login attempts and unlock
            String resetSql = "UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(resetSql)) {
                stmt.setString(1, username);
                stmt.executeUpdate();
            }
            
        } catch (SQLException e) {
            System.err.println("Error recording successful login: " + e.getMessage());
        }
    }
    
    /**
     * Check if account should be locked based on failed attempts
     */
    private static void checkAndLockAccount(Connection conn, String username) throws SQLException {
        try {
            int maxAttempts = Integer.parseInt(
                DBUtil.getSecuritySetting("max_failed_login_attempts", "5"));
            int lockoutMinutes = Integer.parseInt(
                DBUtil.getSecuritySetting("lockout_duration_minutes", "30"));
            
            // Get current failed attempts
            String checkSql = "SELECT failed_login_attempts FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int attempts = rs.getInt("failed_login_attempts");
                        
                        // Lock account if attempts exceed threshold
                        if (attempts >= maxAttempts) {
                            String lockSql = "UPDATE users SET locked_until = CURRENT_TIMESTAMP + INTERVAL '" + lockoutMinutes + " minutes' WHERE username = ?";
                            try (PreparedStatement lockStmt = conn.prepareStatement(lockSql)) {
                                lockStmt.setString(1, username);
                                lockStmt.executeUpdate();
                            }
                            System.out.println("Account locked for user: " + username + " for " + lockoutMinutes + " minutes");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking account lock: " + e.getMessage());
        }
    }
    
    /**
     * Check if account is currently locked
     */
    public static boolean isAccountLocked(String username) {
        try (Connection conn = DBUtil.getConnection()) {
            String sql = "SELECT locked_until FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Timestamp lockedUntil = rs.getTimestamp("locked_until");
                        if (lockedUntil != null) {
                            LocalDateTime lockTime = lockedUntil.toLocalDateTime();
                            LocalDateTime now = LocalDateTime.now();
                            
                            // Account is locked if current time is before locked_until
                            if (now.isBefore(lockTime)) {
                                return true;
                            } else {
                                // Unlock if lock time has expired
                                unlockAccount(username);
                                return false;
                            }
                        }
                    }
                    return false;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking account lock: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the time remaining for account unlock (in minutes)
     */
    public static long getAccountLockRemainingMinutes(String username) {
        try (Connection conn = DBUtil.getConnection()) {
            String sql = "SELECT locked_until FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Timestamp lockedUntil = rs.getTimestamp("locked_until");
                        if (lockedUntil != null) {
                            LocalDateTime lockTime = lockedUntil.toLocalDateTime();
                            LocalDateTime now = LocalDateTime.now();
                            return ChronoUnit.MINUTES.between(now, lockTime);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting lock time: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Unlock an account
     */
    public static void unlockAccount(String username) {
        try (Connection conn = DBUtil.getConnection()) {
            String sql = "UPDATE users SET locked_until = NULL, failed_login_attempts = 0 WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error unlocking account: " + e.getMessage());
        }
    }
    
    /**
     * Check if password has expired
     */
    public static boolean isPasswordExpired(String username) {
        try (Connection conn = DBUtil.getConnection()) {
            String sql = "SELECT password_expires_at FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Timestamp expiresAt = rs.getTimestamp("password_expires_at");
                        if (expiresAt != null) {
                            LocalDateTime expireTime = expiresAt.toLocalDateTime();
                            LocalDateTime now = LocalDateTime.now();
                            return now.isAfter(expireTime);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking password expiration: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Get days remaining for password expiration
     */
    public static long getDaysUntilPasswordExpires(String username) {
        try (Connection conn = DBUtil.getConnection()) {
            String sql = "SELECT password_expires_at FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Timestamp expiresAt = rs.getTimestamp("password_expires_at");
                        if (expiresAt != null) {
                            LocalDateTime expireTime = expiresAt.toLocalDateTime();
                            LocalDateTime now = LocalDateTime.now();
                            return ChronoUnit.DAYS.between(now, expireTime);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking password expiration: " + e.getMessage());
        }
        return -1;
    }
    
    /**
     * Update password and set expiration
     */
    public static void updatePasswordWithExpiration(String username, String newPasswordHash) {
        try (Connection conn = DBUtil.getConnection()) {
            // Get password expiry days from settings
            int expiryDays = 90;
            try {
                expiryDays = Integer.parseInt(
                    DBUtil.getSecuritySetting("password_expiry_days", "90"));
            } catch (Exception e) {
                System.err.println("Error getting password expiry days: " + e.getMessage());
            }
            
            // Store old password in history
            String selectOldSql = "SELECT id, password_hash FROM users WHERE username = ?";
            Long userId = null;
            String oldHash = null;
            try (PreparedStatement stmt = conn.prepareStatement(selectOldSql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getLong("id");
                        oldHash = rs.getString("password_hash");
                    }
                }
            }
            
            // Add to password history if there was an old password
            if (userId != null && oldHash != null) {
                String historySql = "INSERT INTO password_history (user_id, old_password_hash) VALUES (?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(historySql)) {
                    stmt.setLong(1, userId);
                    stmt.setString(2, oldHash);
                    stmt.executeUpdate();
                }
            }
            
            // Update password and set expiration
            String updateSql = "UPDATE users SET password_hash = ?, password_changed_at = CURRENT_TIMESTAMP, " +
                             "password_expires_at = CURRENT_TIMESTAMP + INTERVAL '" + expiryDays + " days', " +
                             "force_password_change = FALSE " +
                             "WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setString(1, newPasswordHash);
                stmt.setString(2, username);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error updating password with expiration: " + e.getMessage());
        }
    }
    
    /**
     * Check if password was recently used (prevent reuse)
     */
    public static boolean isPasswordRecentlyUsed(Long userId, String newPasswordHash) {
        try (Connection conn = DBUtil.getConnection()) {
            try {
                int historyCount = Integer.parseInt(
                    DBUtil.getSecuritySetting("password_history_count", "5"));
                
                String sql = "SELECT password_hash FROM password_history WHERE user_id = ? " +
                           "ORDER BY changed_at DESC LIMIT ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, userId);
                    stmt.setInt(2, historyCount);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String oldHash = rs.getString("password_hash");
                            if (BCrypt.checkpw(newPasswordHash, oldHash)) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error checking password history: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.err.println("Error connecting to database: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Import BCrypt for password checking
     */
    private static class BCrypt {
        public static boolean checkpw(String password, String hash) {
            return org.mindrot.jbcrypt.BCrypt.checkpw(password, hash);
        }
    }
}
