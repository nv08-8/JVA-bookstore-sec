package web;

import utils.DBUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

@WebServlet(name = "AdminServlet", urlPatterns = {"/api/admin/clear-users"})
public class AdminServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        
        String secret = req.getParameter("secret");
        String expectedSecret = System.getenv("ADMIN_PANEL_SECRET");
        String configuredSecret = expectedSecret != null && !expectedSecret.trim().isEmpty() ? expectedSecret.trim() : null;
        
        if (configuredSecret == null || !configuredSecret.equals(secret)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            try (PrintWriter out = resp.getWriter()) {
                out.write("{\"error\":\"Forbidden - Invalid or missing admin secret\"}");
            }
            return;
        }
        
        try {
            DBUtil.deleteAllUsers();
            try (PrintWriter out = resp.getWriter()) {
                out.write("{\"message\":\"All users deleted successfully\"}");
            }
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = resp.getWriter()) {
                out.write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
            }
        }
    }
}
