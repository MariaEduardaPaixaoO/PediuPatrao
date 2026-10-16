// Alternar sidebar em telas pequenas
const botaoSidebar = document.querySelector('.toggle-btn');
if (botaoSidebar) {
    botaoSidebar.addEventListener('click', () => {
        document.getElementById('sidebar').classList.toggle('active');
    });
}

// Cabeçalhos com o token CSRF (lido das meta tags do layout) para chamadas fetch
function csrfHeaders(extras) {
    const headers = Object.assign({}, extras || {});
    const token = document.querySelector('meta[name="_csrf"]');
    const nome = document.querySelector('meta[name="_csrf_header"]');
    if (token && nome && token.content) {
        headers[nome.content] = token.content;
    }
    return headers;
}

// Escapa texto antes de inseri-lo em HTML (evita XSS ao montar tabelas via JavaScript)
function escapeHtml(valor) {
    return String(valor == null ? '' : valor)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// Alternar submenu
function toggleSubmenu(event, element) {
    event.preventDefault();
    element.parentElement.classList.toggle('open');
}