// Shared helpers for CombatSystem static pages (Vue3 + fetch)
window.CombatUI = (() => {
  function uid() { return Date.now() + Math.random(); }

  function createToastStore(max = 4, ttlMs = 4200) {
    return {
      toasts: [],
      toast(title, message, level = 'info') {
        const id = uid();
        this.toasts.unshift({ id, title: title || '提示', message: String(message ?? ''), level });
        if (this.toasts.length > max) this.toasts = this.toasts.slice(0, max);
        setTimeout(() => { this.toasts = this.toasts.filter(t => t.id !== id); }, ttlMs);
      }
    };
  }

  async function httpJson(url, options = {}) {
    const res = await fetch(url, {
      credentials: 'same-origin',
      headers: {
        'Accept': 'application/json',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...(options.headers || {})
      },
      ...options,
      body: options.body ? JSON.stringify(options.body) : undefined
    });
    const ct = res.headers.get('content-type') || '';
    const isJson = ct.includes('application/json');
    const data = isJson ? await res.json().catch(() => null) : await res.text().catch(() => '');
    if (!res.ok) {
      const msg = (typeof data === 'string' && data) ? data : (data && data.message) ? data.message : (res.status + ' ' + res.statusText);
      const err = new Error(msg);
      err.status = res.status;
      err.data = data;
      throw err;
    }
    return data;
  }

  function downloadJson(filename, obj) {
    const blob = new Blob([JSON.stringify(obj, null, 2)], { type: 'application/json;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 1500);
  }

  function pageName() {
    const p = location.pathname.split('/').pop() || '';
    return p.toLowerCase();
  }

  function navLinks() {
    return [
      { href: '/app/commander-next', label: '重构工作台' },
      { href: '/app/simulation-dashboard', label: '仿真看板' },
      { href: '/app/run-center', label: '运行回放中心' },
      { href: '/app/campaign-center', label: '战役中心' },
    ];
  }

  function mountTopbarActive() {
    const pn = pageName();
    for (const a of document.querySelectorAll('.nav a')) {
      try {
        const u = new URL(a.getAttribute('href'), location.origin);
        const fn = (u.pathname.split('/').pop() || '').toLowerCase();
        if (fn === pn) a.classList.add('active');
      } catch (_) {}
    }
  }

  return { createToastStore, httpJson, downloadJson, navLinks, mountTopbarActive };
})();

