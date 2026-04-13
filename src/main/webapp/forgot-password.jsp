<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="pageTitle" value="Quên mật khẩu - Bookish Bliss Haven" />
<!DOCTYPE html>
<html lang="vi">
<%@ include file="/WEB-INF/includes/header.jsp" %>

<main class="min-h-screen bg-gradient-to-br from-amber-900/20 via-amber-800/30 to-amber-950/40 flex items-center justify-center py-16 px-4">
  <section class="w-full max-w-lg">
    <div class="bg-white/95 backdrop-blur-sm rounded-3xl shadow-2xl border border-amber-100/80 p-10 space-y-8">
      <div class="text-center space-y-2">
        <span class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-amber-100 text-amber-700 shadow-inner">
          <i data-feather="key" class="w-7 h-7"></i>
        </span>
        <h1 class="title-font text-3xl font-bold text-amber-800">Quên mật khẩu</h1>
        <p class="text-gray-500 text-sm">Nhập địa chỉ email của bạn. Nếu tồn tại, chúng tôi sẽ gửi liên kết đặt lại mật khẩu.</p>
      </div>

      <form id="forgotForm" class="space-y-5">
        <div class="space-y-2">
          <label for="email" class="text-sm font-semibold text-gray-700">Email</label>
          <input id="email" name="email" type="email" required autocomplete="email"
                 class="w-full rounded-2xl border border-amber-200/70 bg-white px-4 py-3 text-gray-800 shadow-inner focus:border-amber-500 focus:ring-2 focus:ring-amber-400/60" />
        </div>
        <button id="forgotSubmit" type="submit"
                class="w-full flex items-center justify-center gap-2 rounded-2xl bg-amber-700 text-white font-semibold py-3 shadow-lg shadow-amber-900/20 hover:bg-amber-800 transition disabled:opacity-60 disabled:cursor-not-allowed">
          <i data-feather="send" class="w-5 h-5"></i>
          <span>Gửi liên kết đặt lại</span>
        </button>
      </form>

      <div id="forgotFeedback" class="space-y-2"></div>

      <div class="text-center text-sm text-gray-600">
        <a href="<%= request.getContextPath() %>/login.jsp" class="font-semibold text-amber-700 hover:text-amber-800">Quay lại đăng nhập</a>
      </div>
    </div>
  </section>
</main>

<%@ include file="/WEB-INF/includes/footer.jsp" %>

<script nonce="${csp_nonce}">
  const contextPath = '<%= request.getContextPath() %>';

  (function () {
    const form = document.getElementById('forgotForm');
    const feedback = document.getElementById('forgotFeedback');
    const submitBtn = document.getElementById('forgotSubmit');

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

    // 🔹 Xử lý form quên mật khẩu
    form.addEventListener('submit', async function (event) {
      event.preventDefault();
      const formData = new FormData(form);
      const payload = new URLSearchParams(formData);

      submitBtn.disabled = true;
      submitBtn.classList.add('opacity-60', 'cursor-wait');

      try {
        const response = await fetch(contextPath + '/api/auth/reset-password', {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: payload
        });

        const text = await response.text();
        let data = {};
        if (text) {
          try { data = JSON.parse(text); }
          catch (e) { console.warn('Không thể phân tích JSON quên mật khẩu', e); }
        }

        if (response.ok) {
          showMessage('success', '📧 Email đã được gửi!<br>Nếu địa chỉ email tồn tại trong hệ thống, chúng tôi đã gửi liên kết đặt lại mật khẩu. Vui lòng kiểm tra hộp thư của bạn.');
        } else {
          const errorMsg = data?.error || text || 'Gửi email thất bại.';
          showMessage('danger', '❌ ' + errorMsg);
        }

      } catch (error) {
        console.error('Forgot password error', error);
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
