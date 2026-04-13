<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="pageTitle" value="Đặt lại mật khẩu - Bookish Bliss Haven" />
<!DOCTYPE html>
<html lang="vi">
<%@ include file="/WEB-INF/includes/header.jsp" %>

<main class="min-h-screen bg-gradient-to-br from-amber-900/20 via-amber-800/30 to-amber-950/40 flex items-center justify-center py-16 px-4">
  <section class="w-full max-w-lg">
    <div class="bg-white/95 backdrop-blur-sm rounded-3xl shadow-2xl border border-amber-100/80 p-10 space-y-8">
      <div class="text-center space-y-2">
        <span class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-amber-100 text-amber-700 shadow-inner">
          <i data-feather="lock" class="w-7 h-7"></i>
        </span>
        <h1 class="title-font text-3xl font-bold text-amber-800">Đặt lại mật khẩu</h1>
        <p class="text-gray-500 text-sm">Nhập mật khẩu mới cho tài khoản của bạn</p>
      </div>

      <%
        String status = request.getParameter("success");
        String error = request.getParameter("error");
        String token = request.getParameter("token");

        if ("invalid".equals(error)) {
      %>
          <div class="space-y-2">
            <div class="px-4 py-3 rounded-2xl border bg-red-100 border-red-200 text-red-700 text-sm font-medium">
              ❌ Token không hợp lệ hoặc đã hết hạn. Vui lòng yêu cầu liên kết mới.
            </div>
            <div class="text-center">
              <a href="<%= request.getContextPath() %>/forgot-password.jsp" class="inline-flex items-center justify-center gap-2 rounded-2xl bg-amber-700 text-white font-semibold py-3 px-6 shadow-lg shadow-amber-900/20 hover:bg-amber-800 transition">
                <i data-feather="refresh-ccw" class="w-5 h-5"></i>
                <span>Yêu cầu liên kết mới</span>
              </a>
            </div>
          </div>
      <%
        } else if ("true".equals(status)) {
      %>
          <div class="space-y-2">
            <div class="px-4 py-3 rounded-2xl border bg-emerald-100 border-emerald-200 text-emerald-800 text-sm font-medium">
              ✅ Đặt lại mật khẩu thành công! Bạn có thể đăng nhập với mật khẩu mới.
            </div>
            <div class="text-center">
              <a href="<%= request.getContextPath() %>/login.jsp" class="inline-flex items-center justify-center gap-2 rounded-2xl bg-amber-700 text-white font-semibold py-3 px-6 shadow-lg shadow-amber-900/20 hover:bg-amber-800 transition">
                <i data-feather="log-in" class="w-5 h-5"></i>
                <span>Đến trang đăng nhập</span>
              </a>
            </div>
          </div>
      <%
        } else {
      %>

      <form id="resetForm" class="space-y-5">
        <input type="hidden" name="token" value="<%= token != null ? token : "" %>" />
        <div class="space-y-2">
          <label for="password" class="text-sm font-semibold text-gray-700">Mật khẩu mới</label>
          <input id="password" name="password" type="password" required minlength="6"
                 class="w-full rounded-2xl border border-amber-200/70 bg-white px-4 py-3 text-gray-800 shadow-inner focus:border-amber-500 focus:ring-2 focus:ring-amber-400/60" />
        </div>
        <div class="space-y-2">
          <label for="confirmPassword" class="text-sm font-semibold text-gray-700">Xác nhận mật khẩu mới</label>
          <input id="confirmPassword" name="confirmPassword" type="password" required minlength="6"
                 class="w-full rounded-2xl border border-amber-200/70 bg-white px-4 py-3 text-gray-800 shadow-inner focus:border-amber-500 focus:ring-2 focus:ring-amber-400/60" />
        </div>
        <button id="resetSubmit" type="submit"
                class="w-full flex items-center justify-center gap-2 rounded-2xl bg-amber-700 text-white font-semibold py-3 shadow-lg shadow-amber-900/20 hover:bg-amber-800 transition disabled:opacity-60 disabled:cursor-not-allowed">
          <i data-feather="refresh-ccw" class="w-5 h-5"></i>
          <span>Đặt lại mật khẩu</span>
        </button>
      </form>

      <div id="resetFeedback" class="space-y-2"></div>

      <div class="text-center text-sm text-gray-600">
        <a href="<%= request.getContextPath() %>/login.jsp" class="font-semibold text-amber-700 hover:text-amber-800">Quay lại đăng nhập</a>
      </div>

      <% } %>
    </div>
  </section>
</main>

<%@ include file="/WEB-INF/includes/footer.jsp" %>
<script nonce="${csp_nonce}">
  const contextPath = '<%= request.getContextPath() %>';

  (function () {
    const form = document.getElementById('resetForm');
    if (!form) return;

    const feedback = document.getElementById('resetFeedback');
    const submitBtn = document.getElementById('resetSubmit');

    // 🔹 Hàm hiển thị thông báo
    function showMessage(type, message) {
      if (!feedback) return;
      feedback.innerHTML = '';
      const wrapper = document.createElement('div');
      const base = 'px-4 py-3 rounded-2xl border text-sm font-medium transition';
      let tone = 'bg-red-100 border-red-200 text-red-700';
      if (type === 'success') tone = 'bg-emerald-100 border-emerald-200 text-emerald-800';
      else if (type === 'info') tone = 'bg-amber-50 border-amber-200 text-amber-700';
      wrapper.className = base + ' ' + tone;
      wrapper.innerHTML = message;
      feedback.appendChild(wrapper);
    }

    // 🔹 Xử lý form đặt lại mật khẩu
    form.addEventListener('submit', async function (event) {
      event.preventDefault();
      const password = this.password.value;
      const confirm = this.confirmPassword.value;

      if (password !== confirm) {
        showMessage('danger', '❌ Mật khẩu xác nhận không khớp!');
        return;
      }
      if (password.length < 6) {
        showMessage('danger', '❌ Mật khẩu phải có ít nhất 6 ký tự!');
        return;
      }

      const formData = new FormData(form);
      const payload = new URLSearchParams(formData);

      submitBtn.disabled = true;
      submitBtn.classList.add('opacity-60', 'cursor-wait');

      try {
        const response = await fetch(contextPath + '/api/auth/reset', {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: payload
        });

        if (response.ok) {
          window.location.href = contextPath + '/reset-password.jsp?success=true';
        } else {
          const text = await response.text();
          showMessage('danger', '❌ ' + text);
        }

      } catch (error) {
        console.error('Reset error', error);
        showMessage('danger', '❌ Lỗi kết nối. Vui lòng thử lại.');
      } finally {
        submitBtn.disabled = false;
        submitBtn.classList.remove('opacity-60', 'cursor-wait');
      }
    });
  })();
</script>
</body>
</html>
