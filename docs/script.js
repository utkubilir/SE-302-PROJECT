// Initialize Mermaid
mermaid.initialize({ startOnLoad: true, theme: 'default' });

// Mobile Menu
const hamburger = document.getElementById('hamburger');
const sidebar = document.getElementById('sidebar');
const overlay = document.getElementById('sidebarOverlay');

hamburger?.addEventListener('click', () => {
    sidebar.classList.toggle('open');
    overlay.classList.toggle('open');
});

overlay?.addEventListener('click', () => {
    sidebar.classList.remove('open');
    overlay.classList.remove('open');
});

// Close sidebar when clicking a link (mobile)
document.querySelectorAll('.nav-link').forEach(link => {
    link.addEventListener('click', () => {
        sidebar.classList.remove('open');
        overlay.classList.remove('open');
    });
});

// Active nav link highlighting
const navLinks = document.querySelectorAll('.nav-link');
const sections = document.querySelectorAll('.section');

window.addEventListener('scroll', () => {
    let current = '';
    sections.forEach(section => {
        const sectionTop = section.offsetTop - 120;
        if (window.scrollY >= sectionTop) {
            current = section.getAttribute('id');
        }
    });

    navLinks.forEach(link => {
        link.classList.remove('active');
        if (link.getAttribute('href') === `#${current}`) {
            link.classList.add('active');
        }
    });
});

// Progress Bar
window.addEventListener('scroll', () => {
    const winScroll = document.body.scrollTop || document.documentElement.scrollTop;
    const height = document.documentElement.scrollHeight - document.documentElement.clientHeight;
    const scrolled = (winScroll / height) * 100;
    document.getElementById('progressBar').style.width = scrolled + '%';
});

// Back to Top
const backToTop = document.getElementById('backToTop');

window.addEventListener('scroll', () => {
    if (window.scrollY > 500) {
        backToTop.classList.add('visible');
    } else {
        backToTop.classList.remove('visible');
    }
});

backToTop?.addEventListener('click', () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
});

// Dark Mode
const themeToggle = document.getElementById('themeToggle');
const savedTheme = localStorage.getItem('theme');

if (savedTheme === 'dark') {
    document.body.classList.add('dark-mode');
    themeToggle.textContent = '☀️';
}

themeToggle?.addEventListener('click', () => {
    document.body.classList.toggle('dark-mode');
    const isDark = document.body.classList.contains('dark-mode');
    themeToggle.textContent = isDark ? '☀️' : '🌙';
    localStorage.setItem('theme', isDark ? 'dark' : 'light');
});

// Search
const searchModal = document.getElementById('searchModal');
const searchInput = document.getElementById('searchInput');
const searchResults = document.getElementById('searchResults');

function openSearch() {
    searchModal.classList.add('open');
    searchInput.focus();
}

function closeSearch() {
    searchModal.classList.remove('open');
    searchInput.value = '';
    searchResults.innerHTML = '<div class="search-hint">Press ESC to close</div>';
}

// Keyboard shortcuts
document.addEventListener('keydown', (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        openSearch();
    }
    if (e.key === 'Escape') {
        closeSearch();
    }
});

searchModal?.addEventListener('click', (e) => {
    if (e.target === searchModal) closeSearch();
});

function performSearch() {
    const query = searchInput.value.toLowerCase().trim();
    if (query.length < 2) {
        searchResults.innerHTML = '<div class="search-hint">Type at least 2 characters...</div>';
        return;
    }

    const results = [];
    sections.forEach(section => {
        const title = section.querySelector('.section-title')?.textContent || '';
        const content = section.textContent.toLowerCase();
        
        if (content.includes(query)) {
            // Fix: Check index bounds for substring
            const index = content.indexOf(query);
            const start = Math.max(0, index - 30);
            const end = Math.min(content.length, index + 50);
            const previewText = content.substring(start, end);

            results.push({
                id: section.id,
                title: title.replace(/^\d+/, '').trim(),
                preview: previewText
            });
        }
    });

    if (results.length === 0) {
        searchResults.innerHTML = '<div class="search-hint">No results found</div>';
    } else {
        searchResults.innerHTML = results.map(r => `
            <div class="search-result-item" onclick="goToSection('${r.id}')">
                <strong>${r.title}</strong>
                <div style="font-size:0.85rem;color:var(--gray-500);">...${r.preview}...</div>
            </div>
        `).join('');
    }
}

function goToSection(id) {
    closeSearch();
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' });
}

// Copy Button for Code Blocks
document.querySelectorAll('pre:not(.mermaid)').forEach(pre => {
    const wrapper = document.createElement('div');
    wrapper.className = 'code-wrapper';
    pre.parentNode.insertBefore(wrapper, pre);
    wrapper.appendChild(pre);

    const btn = document.createElement('button');
    btn.className = 'copy-btn';
    btn.textContent = 'Copy';
    
    btn.onclick = () => {
        navigator.clipboard.writeText(pre.textContent);
        btn.textContent = 'Copied!';
        btn.classList.add('copied');
        
        setTimeout(() => {
            btn.textContent = 'Copy';
            btn.classList.remove('copied');
        }, 2000);
    };
    
    wrapper.appendChild(btn);
});
