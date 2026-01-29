package utils;

import java.util.regex.Pattern;

/**
 * Utility class for validating password complexity requirements
 */
public class PasswordValidator {
    
    // Pattern definitions
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>?]");
    
    public static class PasswordRequirement {
        public boolean valid;
        public String message;
        
        public PasswordRequirement(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
    }
    
    /**
     * Validate password with default settings (8+ chars, uppercase, lowercase, numbers, special chars)
     */
    public static PasswordRequirement validatePassword(String password) {
        return validatePassword(password, 8, true, true, true, true);
    }
    
    /**
     * Validate password with custom requirements
     */
    public static PasswordRequirement validatePassword(
            String password,
            int minLength,
            boolean requireUppercase,
            boolean requireLowercase,
            boolean requireNumbers,
            boolean requireSpecial) {
        
        if (password == null || password.isEmpty()) {
            return new PasswordRequirement(false, "Mật khẩu không được để trống");
        }
        
        // Check minimum length
        if (password.length() < minLength) {
            return new PasswordRequirement(false, 
                "Mật khẩu phải có ít nhất " + minLength + " ký tự");
        }
        
        // Check for uppercase letters
        if (requireUppercase && !UPPERCASE_PATTERN.matcher(password).find()) {
            return new PasswordRequirement(false, 
                "Mật khẩu phải chứa ít nhất một chữ in hoa (A-Z)");
        }
        
        // Check for lowercase letters
        if (requireLowercase && !LOWERCASE_PATTERN.matcher(password).find()) {
            return new PasswordRequirement(false, 
                "Mật khẩu phải chứa ít nhất một chữ in thường (a-z)");
        }
        
        // Check for numbers
        if (requireNumbers && !DIGIT_PATTERN.matcher(password).find()) {
            return new PasswordRequirement(false, 
                "Mật khẩu phải chứa ít nhất một chữ số (0-9)");
        }
        
        // Check for special characters
        if (requireSpecial && !SPECIAL_PATTERN.matcher(password).find()) {
            return new PasswordRequirement(false, 
                "Mật khẩu phải chứa ít nhất một ký tự đặc biệt (!@#$%^&* v.v.)");
        }
        
        return new PasswordRequirement(true, "Mật khẩu hợp lệ");
    }
    
    /**
     * Get password requirements as a formatted string
     */
    public static String getPasswordRequirementsText() {
        return "Yêu cầu mật khẩu:\n" +
               "• Ít nhất 8 ký tự\n" +
               "• Ít nhất 1 chữ in hoa (A-Z)\n" +
               "• Ít nhất 1 chữ in thường (a-z)\n" +
               "• Ít nhất 1 chữ số (0-9)\n" +
               "• Ít nhất 1 ký tự đặc biệt (!@#$%^&* v.v.)";
    }
    
    /**
     * Check if password meets complexity requirements from database settings
     */
    public static PasswordRequirement validatePasswordWithSettings(String password) throws Exception {
        try {
            int minLength = Integer.parseInt(
                DBUtil.getSecuritySetting("password_min_length", "8"));
            boolean requireUppercase = Boolean.parseBoolean(
                DBUtil.getSecuritySetting("password_require_uppercase", "true"));
            boolean requireLowercase = Boolean.parseBoolean(
                DBUtil.getSecuritySetting("password_require_lowercase", "true"));
            boolean requireNumbers = Boolean.parseBoolean(
                DBUtil.getSecuritySetting("password_require_numbers", "true"));
            boolean requireSpecial = Boolean.parseBoolean(
                DBUtil.getSecuritySetting("password_require_special", "true"));
            
            return validatePassword(password, minLength, requireUppercase, requireLowercase, 
                                   requireNumbers, requireSpecial);
        } catch (Exception e) {
            // Fall back to default validation
            return validatePassword(password);
        }
    }
}
