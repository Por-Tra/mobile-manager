/* Mobile Manager documentation: table of contents, filter, search, copy buttons, "In this article". */
(function () {
  'use strict';
  var data = window.DOC_DATA || { nav: [], symbols: [] };
  var root = document.body.getAttribute('data-root') || '';
  var page = document.body.getAttribute('data-page') || '';

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  // ---- Table of contents (left)
  var toc = document.getElementById('toc');
  var currentLink = null;

  function build(items, depth) {
    var ul = el('ul');
    items.forEach(function (item) {
      var li = el('li');
      var row = el('div', 'item');
      var hasChildren = item.c && item.c.length;
      if (hasChildren) {
        var twist = el('button', 'twist');
        twist.type = 'button';
        twist.setAttribute('aria-label', 'Expand or collapse');
        twist.addEventListener('click', function () { li.classList.toggle('open'); });
        row.appendChild(twist);
      } else {
        row.appendChild(el('span', 'spacer'));
      }
      var link;
      if (item.u) {
        link = el('a', null, item.t);
        link.href = root + item.u;
        if (item.u === page) { link.classList.add('current'); currentLink = link; }
      } else {
        link = el('span', 'label', item.t);
        link.addEventListener('click', function () { li.classList.toggle('open'); });
        link.style.cursor = 'pointer';
      }
      row.appendChild(link);
      li.appendChild(row);
      li.dataset.text = (item.t || '').toLowerCase();
      if (hasChildren) li.appendChild(build(item.c, depth + 1));
      if (depth === 0 && hasChildren && !item.u) li.classList.add('open');
      ul.appendChild(li);
    });
    return ul;
  }

  if (toc) {
    toc.appendChild(build(data.nav, 0));
    // Open the branch of the current page (or of its parent page when on a member anchor).
    if (!currentLink) {
      var candidates = toc.querySelectorAll('a');
      for (var i = 0; i < candidates.length; i++) {
        var href = candidates[i].getAttribute('href').slice(root.length);
        if (page.indexOf(href.replace(/index\.html$/, '')) === 0 && href !== 'index.html') currentLink = candidates[i];
      }
      if (currentLink) currentLink.classList.add('current');
    }
    if (currentLink) {
      var node = currentLink.parentNode;
      while (node && node !== toc) {
        if (node.tagName === 'LI') node.classList.add('open');
        node = node.parentNode;
      }
      var pane = toc.closest('aside');
      setTimeout(function () {
        var top = currentLink.getBoundingClientRect().top - pane.getBoundingClientRect().top + pane.scrollTop;
        pane.scrollTop = Math.max(0, top - pane.clientHeight / 2);
      }, 0);
    }
  }

  var filter = document.getElementById('toc-filter');
  if (filter && toc) {
    filter.addEventListener('input', function () {
      var q = filter.value.trim().toLowerCase();
      var lis = toc.querySelectorAll('li');
      if (!q) {
        lis.forEach(function (li) { li.classList.remove('hidden'); });
        return;
      }
      // Bottom-up: an item is shown if it or one of its descendants matches.
      var arr = Array.prototype.slice.call(lis).reverse();
      arr.forEach(function (li) {
        var match = li.dataset.text.indexOf(q) >= 0;
        var childVisible = li.querySelector(':scope > ul > li:not(.hidden)') !== null;
        li.classList.toggle('hidden', !(match || childVisible));
        if (childVisible) li.classList.add('open');
      });
    });
  }

  // ---- Search (header)
  var input = document.getElementById('search');
  var box = document.getElementById('search-results');
  var selected = -1;

  function score(sym, q) {
    var name = sym[0].toLowerCase();
    if (name === q) return 0;
    if (name.indexOf(q) === 0) return 1 + name.length / 100;
    var i = name.indexOf(q);
    if (i >= 0) return 3 + i / 10;
    if (sym[2].toLowerCase().indexOf(q) >= 0) return 6;
    return -1;
  }

  function render(q) {
    box.innerHTML = '';
    selected = -1;
    if (!q) { box.hidden = true; return; }
    var hits = [];
    data.symbols.forEach(function (s) {
      var sc = score(s, q);
      if (sc >= 0) hits.push([sc, s]);
    });
    hits.sort(function (a, b) { return a[0] - b[0] || a[1][0].length - b[1][0].length; });
    hits = hits.slice(0, 40);
    if (!hits.length) {
      box.appendChild(el('div', 'r-empty', 'No result for "' + q + '".'));
    }
    hits.forEach(function (h) {
      var s = h[1];
      var a = el('a');
      a.href = root + s[3];
      a.appendChild(el('span', 'r-name', s[0]));
      a.appendChild(el('span', 'r-kind', s[1]));
      a.appendChild(el('span', 'r-path', s[2]));
      box.appendChild(a);
    });
    box.hidden = false;
  }

  if (input && box) {
    input.addEventListener('input', function () { render(input.value.trim().toLowerCase()); });
    input.addEventListener('keydown', function (e) {
      var links = box.querySelectorAll('a');
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        if (!links.length) return;
        selected = (selected + (e.key === 'ArrowDown' ? 1 : -1) + links.length) % links.length;
        links.forEach(function (l, i) { l.classList.toggle('selected', i === selected); });
        links[selected].scrollIntoView({ block: 'nearest' });
      } else if (e.key === 'Enter') {
        var target = links[selected >= 0 ? selected : 0];
        if (target) window.location.href = target.href;
      } else if (e.key === 'Escape') {
        box.hidden = true;
        input.blur();
      }
    });
    document.addEventListener('click', function (e) {
      if (!box.contains(e.target) && e.target !== input) box.hidden = true;
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === '/' && document.activeElement !== input && document.activeElement !== filter) {
        e.preventDefault();
        input.focus();
      }
    });
  }

  // ---- Copy buttons
  document.querySelectorAll('.code .copy').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var pre = btn.closest('.code').querySelector('pre');
      var clone = pre.cloneNode(true);
      clone.querySelectorAll('.ln').forEach(function (n) { n.remove(); });
      var text = clone.innerText;
      var done = function () {
        btn.textContent = 'Copied';
        setTimeout(function () { btn.textContent = 'Copy'; }, 1500);
      };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(done, function () {});
      } else {
        var ta = document.createElement('textarea');
        ta.value = text;
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand('copy'); done(); } catch (err) { /* ignore */ }
        ta.remove();
      }
    });
  });

  // ---- Open the "Referenced in" box of a member reached through its anchor
  function openTarget() {
    if (!location.hash) return;
    var t = document.getElementById(location.hash.slice(1));
    if (t && t.classList.contains('member')) {
      var d = t.querySelector('details');
      if (d) d.open = true;
    }
  }
  window.addEventListener('hashchange', openTarget);
  openTarget();

  // ---- "In this article" (right): highlight the section being read
  var onPage = document.querySelectorAll('.on-page a');
  if (onPage.length && 'IntersectionObserver' in window) {
    var map = {};
    onPage.forEach(function (a) { map[a.getAttribute('href').slice(1)] = a; });
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting && map[entry.target.id]) {
          onPage.forEach(function (a) { a.classList.remove('active'); });
          map[entry.target.id].classList.add('active');
        }
      });
    }, { rootMargin: '-60px 0px -70% 0px' });
    Object.keys(map).forEach(function (id) {
      var h = document.getElementById(id);
      if (h) observer.observe(h);
    });
  }
})();
