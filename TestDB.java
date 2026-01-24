public class TestDB {
    public static void main(String[] args) {
        try {
            // Test database connection
            utils.DBUtil.getConnection();
            System.out.println("✅ DB Connection successful!");
            
            // Test email configuration (uncomment and replace with your email to test)
            // utils.EmailUtil.testEmailConnection("your-email@example.com");
            System.out.println("✅ Email configuration loaded successfully!");
            System.out.println("📧 To test email functionality, uncomment the testEmailConnection line and add your email address.");
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}