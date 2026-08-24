/* ============================================================================
   YBS UI — shared behaviour for the design mockups.
   No backend. Nothing here calls the API; every interaction is local so the
   screens can be shown, clicked through and screenshotted on their own.
   ========================================================================== */

/* ------------------------------------------------------------------ toasts */
function toast(my, en) {
    let host = document.querySelector('.toasts');
    if (!host) {
        host = document.createElement('div');
        host.className = 'toasts';
        host.setAttribute('aria-live', 'polite');
        document.body.appendChild(host);
    }
    const t = document.createElement('div');
    t.className = 'toast';
    t.innerHTML = `<span class="ic" aria-hidden="true">✓</span><div><div>${my}</div>${
        en ? `<span class="en">${en}</span>` : ''}</div>`;
    host.appendChild(t);
    setTimeout(() => t.remove(), 3800);
}

/* ------------------------------------------------- segmented controls (tabs) */
/* Any .seg with buttons carrying data-panel switches those panels.
   A .seg without data-panel just reports its selection via the change event. */
document.querySelectorAll('.seg').forEach(seg => {
    const buttons = [...seg.querySelectorAll('button')];
    buttons.forEach(btn => {
        btn.addEventListener('click', () => {
            buttons.forEach(b => b.setAttribute('aria-selected', b === btn));
            const target = btn.dataset.panel;
            if (target) {
                const group = btn.closest('[data-seg-group]') || document;
                group.querySelectorAll('[data-panel-id]').forEach(p => {
                    p.classList.toggle('hidden', p.dataset.panelId !== target);
                });
            }
            seg.dispatchEvent(new CustomEvent('seg:change', { detail: btn.dataset }));
        });
        btn.addEventListener('keydown', e => {
            const i = buttons.indexOf(btn);
            if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
                e.preventDefault();
                buttons[(i + (e.key === 'ArrowRight' ? 1 : buttons.length - 1)) % buttons.length].focus();
            }
        });
    });
});

/* --------------------------------------------------------------- toggle chips */
document.querySelectorAll('.chip[aria-pressed]').forEach(chip => {
    chip.addEventListener('click', () => {
        chip.setAttribute('aria-pressed', chip.getAttribute('aria-pressed') !== 'true');
    });
});

/* ------------------------------------------------------------ password show */
document.querySelectorAll('[data-reveal]').forEach(btn => {
    btn.addEventListener('click', () => {
        const input = document.getElementById(btn.dataset.reveal);
        const shown = input.type === 'text';
        input.type = shown ? 'password' : 'text';
        btn.textContent = shown ? '👁' : '🙈';
        btn.setAttribute('aria-label', shown ? 'စကားဝှက် ပြရန်' : 'စကားဝှက် ဖျောက်ရန်');
    });
});

/* -------------------------------------------------------------- logout flow */
/* Sign-out is destructive enough to confirm, and the confirmation says what is
   actually lost — an in-progress trip — rather than asking "are you sure". */
const logoutDialog = document.getElementById('logout-dialog');
if (logoutDialog) {
    document.querySelectorAll('[data-logout]').forEach(btn =>
        btn.addEventListener('click', () => logoutDialog.showModal()));
    logoutDialog.querySelector('[data-logout-cancel]')
        .addEventListener('click', () => logoutDialog.close());
    logoutDialog.querySelector('[data-logout-confirm]')
        .addEventListener('click', () => {
            logoutDialog.close();
            window.location.href = 'login.html';
        });
}

/* ------------------------------------------------------------ mock submits */
document.querySelectorAll('[data-mock-submit]').forEach(btn => {
    btn.addEventListener('click', () => {
        const [my, en] = btn.dataset.mockSubmit.split('|');
        toast(my, en);
    });
});