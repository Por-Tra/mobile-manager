#!/usr/bin/env python3
"""Generates the Mobile Manager documentation site in `doc/`.

    python doc/tools/gen_docs.py                 # regenerate the site
    python doc/tools/gen_docs.py --missing out   # write KDoc blocks that have no English translation yet

Inputs:
  - Kotlin sources of :app, :core:data, :core:domain (API reference, source browser)
  - test sources and AndroidManifest.xml (only as places where symbols are referenced)
  - doc/tools/pages/*.html        hand-written concept pages (English)
  - doc/tools/kdoc_en.txt         English translations of the (French) KDoc comments
  - core/data/schemas/…/<latest>.json  Room schema (database reference)
Outputs: doc/index.html, doc/concepts/, doc/api/, doc/src/, doc/assets/data.js
No third-party dependency: only the Python standard library.
"""
from __future__ import annotations

import html
import json
import os
import posixpath
import re
import shutil
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from kotlin_parser import CODE, COMMENT, STRING, Decl, SourceFile, parse_file  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / 'doc'
TOOLS = DOC / 'tools'

SOURCE_SETS = [
    (':app', 'app/src/main/kotlin', False),
    (':core:data', 'core/data/src/main/kotlin', False),
    (':core:domain', 'core/domain/src/main/kotlin', False),
    (':core:domain (tests)', 'core/domain/src/test/kotlin', True),
]
EXTRA_REFERENCE_FILES = ['app/src/main/AndroidManifest.xml']
MODULE_ORDER = [':core:domain', ':core:data', ':app']
MODULE_INFO = {
    ':core:domain': 'Pure Kotlin/JVM module: models, business logic (rule engine, profiles, sessions, zones, quotas, lock) and repository interfaces. No Android dependency; fully unit tested.',
    ':core:data': 'Persistence: Room database, DataStore preferences, repository implementations and Hilt bindings.',
    ':app': 'Android application: Hilt setup, Compose UI, accessibility service, foreground service, receivers and system integrations.',
}
KEYWORDS = set('''as break class continue do else false for fun if in interface is null object package return super this throw
true try typealias typeof val var when while by catch constructor delegate dynamic field file finally get import init param
property receiver set setparam where abstract actual annotation companion const crossinline data enum expect external final
infix inline inner internal lateinit noinline open operator out override private protected public reified sealed suspend
tailrec vararg value'''.split())


# ---------------------------------------------------------------------------------------------
# Loading

def load_sources():
    files = []
    for module, base, is_test in SOURCE_SETS:
        for path in sorted((ROOT / base).rglob('*.kt')):
            rel = path.relative_to(ROOT).as_posix()
            sf = SourceFile(path=rel, module=module, is_test=is_test)
            parse_file(sf, path.read_text(encoding='utf-8'))
            files.append(sf)
    return files


def load_translations():
    path = TOOLS / 'kdoc_en.txt'
    table = {}
    if not path.exists():
        return table
    text = path.read_text(encoding='utf-8')
    for block in re.split(r'^@@ fr\s*$', text, flags=re.M)[1:]:
        if '\n@@ en' not in block:
            continue
        fr, en = re.split(r'^@@ en\s*$', block, maxsplit=1, flags=re.M)
        fr, en = fr.strip(), en.strip()
        if fr and en:
            table[normalize(fr)] = en
    return table


def normalize(s: str) -> str:
    return '\n'.join(line.strip() for line in s.strip().split('\n'))


# ---------------------------------------------------------------------------------------------
# Site model

class Site:
    def __init__(self, files):
        self.files = files
        self.main_files = [f for f in files if not f.is_test]
        self.decls = [d for f in self.main_files for d in f.all_decls]
        self.types = [d for d in self.decls if d.is_type]
        self.types_by_name = defaultdict(list)
        for t in self.types:
            self.types_by_name[t.name].append(t)
        self.packages = defaultdict(list)       # package -> files
        for f in self.main_files:
            self.packages[f.package].append(f)
        self.package_module = {p: fs[0].module for p, fs in self.packages.items()}
        self.translations = load_translations()
        self.missing = {}
        self.url = {}
        self._assign_urls()
        self._index_words()
        self.subtypes = defaultdict(list)
        for t in self.types:
            for st in t.supertypes:
                for target in self.resolve_type(st.split('.')[-1], t):
                    self.subtypes[id(target)].append(t)

    # ---- URLs
    def _assign_urls(self):
        for d in self.decls:
            pkg = d.file.package
            if d.is_type:
                self.url[id(d)] = f'api/{pkg}/{d.qualified}.html'
        for f in self.main_files:
            used = defaultdict(int)
            for d in f.all_decls:
                if d.is_type:
                    continue
                owner = d.parent
                page = self.url[id(owner)] if owner else f'api/{f.package}/index.html'
                key = (page, d.name)
                used[key] += 1
                anchor = 'm-' + d.name + (f'-{used[key]}' if used[key] > 1 else '')
                self.url[id(d)] = page + '#' + anchor

    @staticmethod
    def src_url(path: str) -> str:
        return f'src/{path}.html'

    # ---- name resolution
    def resolve_type(self, name, context: Decl | None = None):
        cands = self.types_by_name.get(name, [])
        if len(cands) <= 1 or context is None:
            return cands
        same_pkg = [c for c in cands if c.file.package == context.file.package]
        return same_pkg or cands

    def resolve(self, ref: str, context: Decl | None, sf: SourceFile | None = None):
        parts = ref.split('.')
        head = parts[0]
        scopes = []
        d = context
        while d is not None:
            if d.is_type:
                scopes.append(d.members)
            d = d.parent
        if sf is not None:
            scopes.append(sf.decls)
        for scope in scopes:
            for m in scope:
                if m.name == head:
                    return self._descend(m, parts[1:])
        types = self.resolve_type(head, context)
        if types:
            return self._descend(types[0], parts[1:])
        if context is not None:
            for f in self.packages.get(context.file.package, []):
                for m in f.decls:
                    if m.name == head and m.visibility != 'private':
                        return self._descend(m, parts[1:])
        else:
            tops = [m for f in self.main_files for m in f.decls if m.name == head and m.visibility != 'private']
            if tops:
                return self._descend(tops[0], parts[1:])
        return None

    @staticmethod
    def _descend(d, rest):
        for part in rest:
            nxt = next((m for m in d.members if m.name == part), None)
            if nxt is None:
                return d
            d = nxt
        return d

    # ---- references
    def _index_words(self):
        self.ref_files = []   # (path, module, is_test, lines(original), code_lines)
        for f in self.files:
            self.ref_files.append((f.path, f.module, f.is_test, f.scan.text.split('\n'), f.scan.code.split('\n')))
        for extra in EXTRA_REFERENCE_FILES:
            p = ROOT / extra
            if p.exists():
                t = p.read_text(encoding='utf-8')
                self.ref_files.append((extra, ':app', False, t.split('\n'), t.split('\n')))
        self.words = {}       # path -> word -> [line numbers]
        for path, _, _, _, code_lines in self.ref_files:
            idx = defaultdict(list)
            for n, line in enumerate(code_lines, 1):
                for w in set(re.findall(r'[A-Za-z_]\w*', line)):
                    idx[w].append(n)
            self.words[path] = idx
        self.decl_lines = defaultdict(set)   # name -> {(path, line)}
        for f in self.files:
            for d in f.all_decls:
                self.decl_lines[d.name].add((f.path, d.line))

    def references(self, d: Decl):
        """Files and lines where the declaration name is used (Doxygen-like « Referenced by »)."""
        out = []
        scope_names = []
        owner = d.parent
        if owner is not None:
            scope_names.append(owner.name)
            if owner.parent is not None:
                scope_names.append(owner.parent.name)
        if d.receiver:
            scope_names.append(re.sub(r'[<?].*', '', d.receiver).split('.')[-1])
        private = d.visibility == 'private'
        for path, module, is_test, orig, _ in self.ref_files:
            if private and path != d.file.path:
                continue
            words = self.words[path]
            lines = words.get(d.name)
            if not lines:
                continue
            if path != d.file.path and scope_names and not any(s in words for s in scope_names):
                continue
            hits = [n for n in lines if (path, n) not in self.decl_lines[d.name]]
            if path == d.file.path:
                hits = [n for n in hits if n != d.line]
            if hits:
                out.append((path, module, is_test, [(n, orig[n - 1].strip()) for n in hits]))
        return out

    # ---- KDoc
    def kdoc_text(self, d: Decl) -> tuple[str, bool]:
        if not d.kdoc:
            return '', True
        en = self.translations.get(normalize(d.kdoc))
        if en is None:
            self.missing[normalize(d.kdoc)] = d
            return d.kdoc, False
        return en, True


# ---------------------------------------------------------------------------------------------
# HTML helpers

def esc(s) -> str:
    return html.escape(str(s), quote=True)


def rel(from_page: str, to: str) -> str:
    """Relative link from one generated page to another (both relative to doc/)."""
    target, _, anchor = to.partition('#')
    base = posixpath.dirname(from_page) or '.'
    r = posixpath.relpath(target, base)
    return r + ('#' + anchor if anchor else '')


def root_of(page: str) -> str:
    depth = page.count('/')
    return '../' * depth


def slug(s: str) -> str:
    return re.sub(r'[^a-z0-9]+', '-', s.lower()).strip('-')


class Renderer:
    def __init__(self, site: Site):
        self.site = site

    # ---- inline KDoc markup
    def inline(self, text: str, page: str, context: Decl | None, sf=None) -> str:
        parts = re.split(r'(`[^`]+`|\[[A-Za-z_][\w.]*\])', text)
        out = []
        for p in parts:
            if p.startswith('`') and p.endswith('`') and len(p) > 1:
                out.append(f'<code>{esc(p[1:-1])}</code>')
            elif p.startswith('[') and p.endswith(']'):
                ref = p[1:-1]
                target = self.site.resolve(ref, context, sf)
                if target is not None and id(target) in self.site.url:
                    out.append(f'<a href="{esc(rel(page, self.site.url[id(target)]))}"><code>{esc(ref)}</code></a>')
                else:
                    out.append(f'<code>{esc(ref)}</code>')
            else:
                out.append(esc(p).replace('**', ''))
        return ''.join(out)

    def kdoc_html(self, d: Decl, page: str) -> str:
        text, translated = self.site.kdoc_text(d)
        if not text:
            return ''
        sf = d.file
        blocks, params, returns = [], [], []
        for para in text.split('\n\n'):
            items, buf = [], []
            for line in para.split('\n'):
                m = re.match(r'@param\s+(\w+)\s*(.*)', line)
                if m:
                    params.append((m.group(1), m.group(2)))
                    continue
                m = re.match(r'@return\s*(.*)', line)
                if m:
                    returns.append(m.group(1))
                    continue
                m = re.match(r'(-|\*|\d+\.)\s+(.*)', line)
                if m:
                    items.append((m.group(1)[0].isdigit(), m.group(2)))
                else:
                    if items:
                        blocks.append(self.list_html(items, page, d, sf))
                        items = []
                    buf.append(line)
            if buf:
                blocks.append(f'<p>{self.inline(" ".join(buf), page, d, sf)}</p>')
            if items:
                blocks.append(self.list_html(items, page, d, sf))
        out = ''.join(blocks)
        if params:
            out += '<h5>Parameters</h5><table class="params"><tbody>' + ''.join(
                f'<tr><td><code>{esc(n)}</code></td><td>{self.inline(t, page, d, sf)}</td></tr>' for n, t in params) + '</tbody></table>'
        if returns:
            out += '<h5>Returns</h5>' + ''.join(f'<p>{self.inline(r, page, d, sf)}</p>' for r in returns)
        if not translated:
            out = '<div class="lang-note">Original comment (French, no English translation yet)</div>' + out
        return f'<div class="kdoc">{out}</div>'

    def list_html(self, items, page, d, sf) -> str:
        tag = 'ol' if all(numbered for numbered, _ in items) else 'ul'
        return f'<{tag}>' + ''.join(f'<li>{self.inline(t, page, d, sf)}</li>' for _, t in items) + f'</{tag}>'

    def summary(self, d: Decl, page: str) -> str:
        text, _ = self.site.kdoc_text(d)
        if not text:
            return '<span class="muted">—</span>'
        first = text.split('\n\n')[0].split('\n')[0]
        first = re.sub(r'^@\w+\s*', '', first)
        m = re.match(r'(.+?[.!?])(\s|$)', first)
        if m and len(m.group(1)) > 20:
            first = m.group(1)
        if len(first) > 240:
            first = first[:237].rstrip() + '…'
        return self.inline(first, page, d, d.file)

    # ---- code
    def signature_block(self, d: Decl, page: str) -> str:
        annos = ''.join(esc(a) + '\n' for a in d.annotations)
        sig = self.link_types(d.signature, page, d)
        return self.code_block(annos + sig, raw_html=True)

    def link_types(self, sig: str, page: str, context: Decl) -> str:
        out = []
        for tok in re.split(r'([A-Za-z_]\w*)', sig):
            if re.fullmatch(r'[A-Za-z_]\w*', tok):
                if tok in KEYWORDS:
                    out.append(f'<span class="kw">{tok}</span>')
                    continue
                cands = self.site.resolve_type(tok, context)
                if cands and tok != context.name:
                    out.append(f'<a href="{esc(rel(page, self.site.url[id(cands[0])]))}">{tok}</a>')
                    continue
            out.append(esc(tok))
        return ''.join(out)

    @staticmethod
    def code_block(content: str, raw_html=False, lang='Kotlin') -> str:
        body = content if raw_html else esc(content)
        return (f'<div class="code"><div class="code-header"><span>{lang}</span>'
                f'<button class="copy" type="button">Copy</button></div><pre><code>{body}</code></pre></div>')

    # ---- references
    def refs_html(self, d: Decl, page: str) -> str:
        refs = self.site.references(d)
        if not refs:
            return '<p class="muted refs-none">No reference found outside its declaration.</p>'
        rows = []
        total = 0
        for path, module, is_test, hits in refs:
            total += len(hits)
            shown = hits[:40]
            links = ', '.join(
                f'<a href="{esc(rel(page, Site.src_url(path)))}#L{n}" title="{esc(t[:160])}">{n}</a>' for n, t in shown)
            if len(hits) > len(shown):
                links += f', … (+{len(hits) - len(shown)})'
            name = path.rsplit('/', 1)[-1]
            folder = path.rsplit('/', 1)[0]
            tag = ' <span class="tag test">test</span>' if is_test else ''
            rows.append(f'<tr><td><a href="{esc(rel(page, Site.src_url(path)))}">{esc(name)}</a>{tag}'
                        f'<div class="path">{esc(folder)}</div></td><td>{esc(module)}</td><td class="lines">{links}</td></tr>')
        files = len(refs)
        return (f'<p class="refs-count">{total} reference{"s" if total > 1 else ""} in {files} file{"s" if files > 1 else ""}.</p>'
                '<table class="refs"><thead><tr><th>File</th><th>Module</th><th>Lines</th></tr></thead><tbody>'
                + ''.join(rows) + '</tbody></table>')


# ---------------------------------------------------------------------------------------------
# Layout

TOP_LINKS = [('Overview', 'index.html'), ('Architecture', 'concepts/architecture.html'),
             ('API reference', 'api/index.html'), ('Source files', 'src/index.html')]


def layout(page: str, title: str, body: str, breadcrumb: list, section: str, toc_headings=None) -> str:
    root = root_of(page)
    links = ''.join(
        f'<a href="{esc(rel(page, href))}" class="{"active" if section == label else ""}">{esc(label)}</a>'
        for label, href in TOP_LINKS)
    crumbs = ' <span class="sep">/</span> '.join(
        f'<a href="{esc(rel(page, href))}">{esc(label)}</a>' if href else f'<span>{esc(label)}</span>'
        for label, href in breadcrumb)
    on_page = ''
    if toc_headings:
        on_page = ('<aside class="on-page"><h2>In this article</h2><ol>'
                   + ''.join(f'<li><a href="#{esc(a)}">{esc(t)}</a></li>' for a, t in toc_headings)
                   + '</ol></aside>')
    return f'''<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{esc(title)} - Mobile Manager documentation</title>
<link rel="stylesheet" href="{root}assets/style.css">
</head>
<body data-root="{root}" data-page="{esc(page)}">
<header class="site-header">
  <a class="brand" href="{root}index.html"><span class="logo" aria-hidden="true"></span>Mobile Manager <span class="brand-sub">Docs</span></a>
  <nav class="top-links">{links}</nav>
  <div class="search">
    <input id="search" type="search" placeholder="Search classes, functions, files" autocomplete="off" aria-label="Search">
    <div id="search-results" class="search-results" hidden></div>
  </div>
</header>
<div class="layout">
  <aside class="toc" aria-label="Table of contents">
    <input id="toc-filter" type="search" placeholder="Filter by title" aria-label="Filter by title">
    <nav id="toc"></nav>
  </aside>
  <main class="content">
    <nav class="breadcrumb">{crumbs}</nav>
    <article>
{body}
    </article>
    <footer class="page-footer">Generated from the source code by <code>doc/tools/gen_docs.py</code>. Do not edit generated pages by hand.</footer>
  </main>
  {on_page}
</div>
<script src="{root}assets/data.js"></script>
<script src="{root}assets/site.js"></script>
</body>
</html>
'''


# ---------------------------------------------------------------------------------------------
# Pages

class Generator:
    def __init__(self, site: Site):
        self.site = site
        self.r = Renderer(site)
        self.written = []

    def write(self, page: str, content: str):
        path = DOC / page
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
        self.written.append(page)

    # ---- member details
    def member_section(self, d: Decl, page: str) -> str:
        anchor = self.site.url[id(d)].split('#', 1)[1]
        badges = f'<span class="badge">{esc(d.type_label)}</span>'
        if d.visibility != 'public':
            badges += f'<span class="badge vis">{esc(d.visibility)}</span>'
        if 'override' in d.modifiers:
            badges += '<span class="badge vis">override</span>'
        if 'suspend' in d.modifiers:
            badges += '<span class="badge vis">suspend</span>'
        if d.companion:
            badges += '<span class="badge vis">companion</span>'
        if any(a.startswith('@Composable') for a in d.annotations):
            badges += '<span class="badge vis">composable</span>'
        src = f'<a href="{esc(rel(page, Site.src_url(d.file.path)))}#L{d.line}">{esc(d.file.path.rsplit("/", 1)[-1])}, line {d.line}</a>'
        return f'''<section class="member" id="{esc(anchor)}">
<h3>{esc(d.name)} {badges}</h3>
{self.r.kdoc_html(d, page) or '<p class="muted">No description.</p>'}
{self.r.signature_block(d, page)}
<p class="defined">Defined in {src}</p>
<details class="refs-box"><summary>Referenced in</summary>{self.r.refs_html(d, page)}</details>
</section>'''

    def member_table(self, items, page, title_col='Name') -> str:
        rows = ''.join(
            f'<tr><td><a href="{esc(rel(page, self.site.url[id(m)]))}"><code>{esc(m.name)}</code></a>'
            f'{"" if m.visibility == "public" else f" <span class=tag>{esc(m.visibility)}</span>"}</td>'
            f'<td>{self.r.summary(m, page)}</td></tr>' for m in items)
        return f'<table class="summary"><thead><tr><th>{title_col}</th><th>Description</th></tr></thead><tbody>{rows}</tbody></table>'

    # ---- type page
    def type_page(self, t: Decl):
        page = self.site.url[id(t)]
        pkg = t.file.package
        heads = []
        title = f'{t.qualified} {t.type_label.title()}'
        parts = [f'<h1>{esc(t.qualified)} <span class="h1-kind">{esc(t.type_label)}</span></h1>']
        parts.append(f'<div class="meta"><span>Package: <a href="{esc(rel(page, f"api/{pkg}/index.html"))}">{esc(pkg)}</a></span>'
                     f'<span>Module: {esc(t.file.module)}</span>'
                     f'<span>Source: <a href="{esc(rel(page, Site.src_url(t.file.path)))}#L{t.line}">{esc(t.file.path.rsplit("/", 1)[-1])}</a></span></div>')
        parts.append(self.r.kdoc_html(t, page) or '<p class="muted">No description.</p>')
        parts.append(self.r.signature_block(t, page))

        def h2(text):
            a = slug(text)
            heads.append((a, text))
            return f'<h2 id="{a}">{esc(text)}</h2>'

        chain = []
        for st in t.supertypes:
            targets = self.site.resolve_type(st.split('.')[-1], t)
            chain.append(f'<a href="{esc(rel(page, self.site.url[id(targets[0])]))}"><code>{esc(st)}</code></a>' if targets else f'<code>{esc(st)}</code>')
        subs = self.site.subtypes.get(id(t), [])
        if chain or subs or t.parent:
            parts.append(h2('Inheritance'))
            rows = []
            if t.parent:
                rows.append(f'<tr><th>Nested in</th><td><a href="{esc(rel(page, self.site.url[id(t.parent)]))}"><code>{esc(t.parent.qualified)}</code></a></td></tr>')
            if chain:
                rows.append(f'<tr><th>Extends / implements</th><td>{", ".join(chain)}</td></tr>')
            if subs:
                rows.append('<tr><th>Known subtypes</th><td>' + ', '.join(
                    f'<a href="{esc(rel(page, self.site.url[id(s)]))}"><code>{esc(s.qualified)}</code></a>' for s in subs) + '</td></tr>')
            parts.append('<table class="kv"><tbody>' + ''.join(rows) + '</tbody></table>')

        plain_params = [p for p in t.ctor_params if not p[2]]
        if plain_params:
            parts.append(h2('Constructor parameters'))
            parts.append('<table class="summary"><thead><tr><th>Name</th><th>Type</th></tr></thead><tbody>' + ''.join(
                f'<tr><td><code>{esc(n)}</code></td><td><code>{self.r.link_types(ty, page, t)}</code></td></tr>' for n, ty, _ in plain_params)
                + '</tbody></table>')

        groups = [
            ('Enum values', [m for m in t.members if m.kind == 'enum-entry']),
            ('Nested types', [m for m in t.members if m.is_type]),
            ('Properties', [m for m in t.members if m.kind == 'property' and not m.companion]),
            ('Functions', [m for m in t.members if m.kind == 'function' and not m.companion]),
            ('Companion members', [m for m in t.members if m.companion and not m.is_type]),
            ('Type aliases', [m for m in t.members if m.kind == 'typealias']),
        ]
        for label, items in groups:
            if items:
                parts.append(h2(label))
                parts.append(self.member_table(items, page))
        details = [m for m in t.members if not m.is_type]
        if details:
            parts.append(h2('Member details'))
            parts.extend(self.member_section(m, page) for m in details)
        parts.append(h2('Referenced in'))
        parts.append(self.r.refs_html(t, page))
        crumbs = [('API reference', 'api/index.html'), (pkg, f'api/{pkg}/index.html')]
        if t.parent:
            crumbs.append((t.parent.qualified, self.site.url[id(t.parent)]))
        crumbs.append((t.name, None))
        self.write(page, layout(page, title, '\n'.join(parts), crumbs, 'API reference', heads))

    # ---- package page
    def package_page(self, pkg: str):
        page = f'api/{pkg}/index.html'
        files = self.site.packages[pkg]
        module = self.site.package_module[pkg]
        top_types = sorted((d for f in files for d in f.decls if d.is_type), key=lambda d: d.name.lower())
        funcs = [d for f in files for d in f.decls if d.kind == 'function']
        props = [d for f in files for d in f.decls if d.kind in ('property', 'typealias')]
        heads = []

        def h2(text):
            a = slug(text)
            heads.append((a, text))
            return f'<h2 id="{a}">{esc(text)}</h2>'

        parts = [f'<h1>{esc(pkg)} <span class="h1-kind">package</span></h1>',
                 f'<div class="meta"><span>Module: {esc(module)}</span><span>{len(files)} file{"s" if len(files) > 1 else ""}</span></div>']
        intro = PACKAGE_INTROS.get(pkg.replace('com.lucas.mobilemanager', '').lstrip('.') or '(root)')
        if intro:
            parts.append(f'<p>{intro}</p>')
        if top_types:
            parts.append(h2('Types'))
            rows = ''.join(
                f'<tr><td><a href="{esc(rel(page, self.site.url[id(t)]))}"><code>{esc(t.name)}</code></a></td>'
                f'<td>{esc(t.type_label)}</td><td>{self.r.summary(t, page)}</td></tr>' for t in top_types)
            parts.append(f'<table class="summary"><thead><tr><th>Name</th><th>Kind</th><th>Description</th></tr></thead><tbody>{rows}</tbody></table>')
        if funcs:
            parts.append(h2('Functions'))
            parts.append(self.member_table(funcs, page))
        if props:
            parts.append(h2('Properties and type aliases'))
            parts.append(self.member_table(props, page))
        parts.append(h2('Files'))
        parts.append('<ul class="files">' + ''.join(
            f'<li><a href="{esc(rel(page, Site.src_url(f.path)))}">{esc(f.path.rsplit("/", 1)[-1])}</a></li>' for f in files) + '</ul>')
        if funcs or props:
            parts.append(h2('Member details'))
            parts.extend(self.member_section(m, page) for m in funcs + props)
        crumbs = [('API reference', 'api/index.html'), (pkg, None)]
        self.write(page, layout(page, f'{pkg} package', '\n'.join(parts), crumbs, 'API reference', heads))

    def api_index(self):
        page = 'api/index.html'
        parts = ['<h1>API reference</h1>',
                 '<p>Every package of the three Gradle modules, generated from the Kotlin sources: types, functions, '
                 'properties and enum values with their documentation comment, declaration, source location and the '
                 'files that reference them.</p>',
                 '<div class="alert note"><p class="alert-title">Note</p><p>References are found by name, like Doxygen '
                 'without a compiler: a member is listed in a file when its name appears there in code (comments and '
                 'strings excluded) and the file also mentions the owning type. Members that share a name may therefore '
                 'appear together.</p></div>']
        heads = []
        for module in MODULE_ORDER:
            pkgs = sorted(p for p, m in self.site.package_module.items() if m == module)
            a = slug(module)
            heads.append((a, module))
            parts.append(f'<h2 id="{a}">{esc(module)}</h2><p>{esc(MODULE_INFO[module])}</p>')
            rows = []
            for p in pkgs:
                types = sum(1 for f in self.site.packages[p] for d in f.all_decls if d.is_type)
                intro = PACKAGE_INTROS.get(p.replace('com.lucas.mobilemanager', '').lstrip('.') or '(root)', '')
                rows.append(f'<tr><td><a href="{esc(rel(page, f"api/{p}/index.html"))}"><code>{esc(p)}</code></a></td>'
                            f'<td>{types}</td><td>{intro}</td></tr>')
            parts.append('<table class="summary"><thead><tr><th>Package</th><th>Types</th><th>Description</th></tr></thead><tbody>'
                         + ''.join(rows) + '</tbody></table>')
        self.write(page, layout(page, 'API reference', '\n'.join(parts), [('API reference', None)], 'API reference', heads))

    # ---- source browser
    def source_page(self, path: str, module: str, text: str, kinds, sf: SourceFile | None):
        page = Site.src_url(path)
        lines_html = self.highlight(text, kinds, page, sf)
        decl_rows = ''
        if sf is not None and sf.all_decls and not sf.is_test:
            decl_rows = '<table class="summary"><thead><tr><th>Declaration</th><th>Kind</th><th>Line</th></tr></thead><tbody>' + ''.join(
                f'<tr><td><a href="{esc(rel(page, self.site.url[id(d)]))}"><code>{esc(d.qualified)}</code></a></td>'
                f'<td>{esc(d.type_label)}</td><td><a href="#L{d.line}">{d.line}</a></td></tr>'
                for d in sf.all_decls if id(d) in self.site.url) + '</tbody></table>'
        users = self.file_users(sf) if sf is not None and not sf.is_test else []
        users_html = ''
        if users:
            users_html = '<details class="refs-box"><summary>Files that reference this file\'s declarations</summary><ul class="files">' + ''.join(
                f'<li><a href="{esc(rel(page, Site.src_url(p)))}">{esc(p.rsplit("/", 1)[-1])}</a> <span class="path">{esc(p.rsplit("/", 1)[0])}</span></li>'
                for p in users) + '</ul></details>'
        name = path.rsplit('/', 1)[-1]
        pkg = f'<span>Package: <a href="{esc(rel(page, f"api/{sf.package}/index.html"))}">{esc(sf.package)}</a></span>' if sf is not None and not sf.is_test and sf.package else ''
        body = (f'<h1>{esc(name)}</h1><div class="meta"><span>{esc(path)}</span><span>Module: {esc(module)}</span>{pkg}</div>'
                + ((f'<h2 id="declarations">Declarations</h2><details class="refs-box"><summary>{len([d for d in sf.all_decls if id(d) in self.site.url])} declarations in this file</summary>' + decl_rows + '</details>') if decl_rows else '') + users_html
                + f'<h2 id="source">Source</h2><div class="code source"><div class="code-header"><span>{"XML" if path.endswith(".xml") else "Kotlin"}</span>'
                  f'<button class="copy" type="button">Copy</button></div><pre class="numbered"><code>{lines_html}</code></pre></div>')
        crumbs = [('Source files', 'src/index.html'), (path.rsplit('/', 1)[0], None), (name, None)]
        heads = [('declarations', 'Declarations')] if decl_rows else []
        heads.append(('source', 'Source'))
        self.write(page, layout(page, name, body, crumbs, 'Source files', heads))

    def file_users(self, sf: SourceFile):
        users = set()
        for d in sf.all_decls:
            if d.is_type:
                for path, _, _, _ in self.site.references(d):
                    if path != sf.path:
                        users.add(path)
        return sorted(users)

    def highlight(self, text: str, kinds, page: str, sf: SourceFile | None) -> str:
        out_lines = []
        pos = 0
        type_links = {}
        for line_no, line in enumerate(text.split('\n'), 1):
            segs = []
            i = 0
            while i < len(line):
                k = kinds[pos + i] if kinds is not None else CODE
                j = i
                while j < len(line) and (kinds[pos + j] if kinds is not None else CODE) == k:
                    j += 1
                chunk = line[i:j]
                if k == COMMENT:
                    segs.append(f'<span class="cm">{esc(chunk)}</span>')
                elif k == STRING:
                    segs.append(f'<span class="st">{esc(chunk)}</span>')
                else:
                    for tok in re.split(r'(@?[A-Za-z_]\w*|\d[\w.]*)', chunk):
                        if not tok:
                            continue
                        if tok.startswith('@'):
                            segs.append(f'<span class="an">{esc(tok)}</span>')
                        elif tok in KEYWORDS and kinds is not None:
                            segs.append(f'<span class="kw">{tok}</span>')
                        elif tok[0].isdigit():
                            segs.append(f'<span class="nu">{esc(tok)}</span>')
                        elif tok[0].isupper() and kinds is not None:
                            if tok not in type_links:
                                cands = self.site.types_by_name.get(tok, [])
                                ctx = sf.decls[0] if sf is not None and sf.decls else None
                                if len(cands) > 1 and ctx is not None:
                                    cands = self.site.resolve_type(tok, ctx)
                                type_links[tok] = rel(page, self.site.url[id(cands[0])]) if cands else None
                            href = type_links[tok]
                            segs.append(f'<a class="ty" href="{esc(href)}">{tok}</a>' if href else esc(tok))
                        else:
                            segs.append(esc(tok))
                i = j
            out_lines.append(f'<span class="line" id="L{line_no}"><span class="ln"><a href="#L{line_no}">{line_no}</a></span>{"".join(segs)}</span>')
            pos += len(line) + 1
        return ''.join(out_lines)

    def source_index(self):
        page = 'src/index.html'
        groups = defaultdict(list)
        for path, module, is_test, _, _ in self.site.ref_files:
            groups[module].append(path)
        parts = ['<h1>Source files</h1><p>Every Kotlin file of the project with line numbers, syntax highlighting and links '
                 'from type names to their reference page. Reference lists link straight to the matching line.</p>']
        heads = []
        for module in [':core:domain', ':core:data', ':app', ':core:domain (tests)']:
            paths = sorted(groups.get(module, []))
            if not paths:
                continue
            a = slug(module)
            heads.append((a, module))
            parts.append(f'<h2 id="{a}">{esc(module)}</h2>')
            by_dir = defaultdict(list)
            for p in paths:
                by_dir[p.rsplit('/', 1)[0]].append(p)
            rows = []
            for folder in sorted(by_dir):
                short = re.sub(r'^.*?/kotlin/com/lucas/mobilemanager', '…', folder)
                for p in by_dir[folder]:
                    sf = next((f for f in self.site.files if f.path == p), None)
                    count = len(sf.all_decls) if sf else 0
                    lines = len(sf.scan.text.split('\n')) if sf else len((ROOT / p).read_text(encoding='utf-8').split('\n'))
                    rows.append(f'<tr><td><a href="{esc(rel(page, Site.src_url(p)))}">{esc(p.rsplit("/", 1)[-1])}</a></td>'
                                f'<td class="path">{esc(short)}</td><td>{lines}</td><td>{count}</td></tr>')
            parts.append('<table class="summary"><thead><tr><th>File</th><th>Folder</th><th>Lines</th><th>Declarations</th></tr></thead><tbody>'
                         + ''.join(rows) + '</tbody></table>')
        self.write(page, layout(page, 'Source files', '\n'.join(parts), [('Source files', None)], 'Source files', heads))

    # ---- hand-written pages
    def concept_pages(self):
        pages = []
        for path in sorted((TOOLS / 'pages').glob('*.html')):
            raw = path.read_text(encoding='utf-8')
            meta = dict(re.findall(r'^<!--\s*(\w+):\s*(.*?)\s*-->', raw, re.M))
            content = re.sub(r'^<!--\s*\w+:.*?-->\n?', '', raw, flags=re.M)
            out = meta.get('output', 'concepts/' + re.sub(r'^\d+-', '', path.stem) + '.html')
            pages.append((int(meta.get('order', 99)), out, meta.get('title', path.stem), meta.get('section', 'Overview'), content))
        pages.sort()
        self.concepts = [(out, title) for _, out, title, _, _ in pages]
        for _, out, title, section, content in pages:
            content = content.replace('<!--DB_SCHEMA-->', self.db_schema_html())
            content = content.replace('<!--DATASTORE_KEYS-->', self.datastore_html(out))
            content = self.expand_links(content, out)
            heads = [(a, re.sub('<[^>]+>', '', t)) for a, t in re.findall(r'<h2 id="([^"]+)">(.*?)</h2>', content)]
            crumbs = [('Documentation', 'index.html')] + ([] if out == 'index.html' else [(title, None)])
            self.write(out, layout(out, title, content, crumbs, section, heads))

    def expand_links(self, content: str, page: str) -> str:
        """`{{Type}}` / `{{Type.member}}` → link to the reference page; `{{src:path#L12}}` → source link; `{{page:x.html}}`."""
        def repl(m):
            ref = m.group(1)
            if ref.startswith('src:'):
                target = ref[4:]
                p, _, anchor = target.partition('#')
                label = p.rsplit('/', 1)[-1]
                return f'<a href="{esc(rel(page, Site.src_url(p)) + ("#" + anchor if anchor else ""))}"><code>{esc(label)}</code></a>'
            if ref.startswith('page:'):
                return esc(rel(page, ref[5:]))
            target = self.site.resolve(ref, None)
            if target is None:
                print(f'warning: unresolved link {{{{{ref}}}}} in {page}', file=sys.stderr)
                return f'<code>{esc(ref)}</code>'
            return f'<a href="{esc(rel(page, self.site.url[id(target)]))}"><code>{esc(ref)}</code></a>'
        return re.sub(r'\{\{([^{}]+)\}\}', repl, content)

    def db_schema_html(self) -> str:
        schema_dir = next((ROOT / 'core/data/schemas').glob('*'))
        latest = max(schema_dir.glob('*.json'), key=lambda p: int(p.stem))
        db = json.loads(latest.read_text(encoding='utf-8'))['database']
        out = [f'<p>Schema version <strong>{db["version"]}</strong>, read from <code>{esc(latest.relative_to(ROOT).as_posix())}</code>.</p>']
        for e in db['entities']:
            pk = e.get('primaryKey', {})
            pkc = set(pk.get('columnNames', []))
            out.append(f'<h3 id="table-{esc(e["tableName"])}"><code>{esc(e["tableName"])}</code></h3>')
            rows = ''.join(
                f'<tr><td><code>{esc(f["columnName"])}</code></td><td>{esc(f["affinity"])}</td>'
                f'<td>{"yes" if f.get("notNull") else "no"}</td><td>{"primary key" + (" (auto)" if pk.get("autoGenerate") else "") if f["columnName"] in pkc else ""}</td></tr>'
                for f in e['fields'])
            out.append(f'<table class="summary"><thead><tr><th>Column</th><th>Type</th><th>Not null</th><th>Key</th></tr></thead><tbody>{rows}</tbody></table>')
            for ix in e.get('indices', []):
                out.append(f'<p class="muted">Index <code>{esc(ix["name"])}</code> on {", ".join(esc(c) for c in ix["columnNames"])}{" (unique)" if ix.get("unique") else ""}.</p>')
        return '\n'.join(out)

    def datastore_html(self, page: str) -> str:
        rows = []
        for f in self.site.main_files:
            for n, line in enumerate(f.scan.text.split('\n'), 1):
                for m in re.finditer(r'(boolean|int|long|string|stringSet|float|double)PreferencesKey\("([\w]+)"\)', line):
                    rows.append((m.group(2), m.group(1), f.path, n))
        rows.sort()
        seen = set()
        html_rows = []
        for key, typ, path, n in rows:
            if key in seen:
                continue
            seen.add(key)
            legacy = ' <span class="tag">legacy, removed once</span>' if key.startswith(('scroll_guard', 'default_scroll')) else ''
            html_rows.append(f'<tr><td><code>{esc(key)}</code>{legacy}</td><td>{esc(typ)}</td>'
                             f'<td><a href="{esc(rel(page, Site.src_url(path)))}#L{n}">{esc(path.rsplit("/", 1)[-1])}</a></td></tr>')
        return ('<table class="summary"><thead><tr><th>Key</th><th>Type</th><th>Declared in</th></tr></thead><tbody>'
                + ''.join(html_rows) + '</tbody></table>')

    # ---- navigation and search data
    def data_js(self):
        nav = [{'t': 'Overview', 'u': 'index.html'}]
        nav.append({'t': 'Concepts', 'c': [{'t': t, 'u': u} for u, t in self.concepts if u != 'index.html']})
        api = []
        for module in MODULE_ORDER:
            pkgs = []
            for p in sorted(pk for pk, m in self.site.package_module.items() if m == module):
                types = sorted((d for f in self.site.packages[p] for d in f.all_decls if d.is_type), key=lambda d: d.qualified.lower())
                pkgs.append({'t': p.replace('com.lucas.mobilemanager.', '').replace('com.lucas.mobilemanager', '(root)'),
                             'u': f'api/{p}/index.html',
                             'c': [{'t': d.qualified, 'u': self.site.url[id(d)]} for d in types]})
            api.append({'t': module, 'c': pkgs})
        nav.append({'t': 'API reference', 'u': 'api/index.html', 'c': api})
        nav.append({'t': 'Source files', 'u': 'src/index.html'})
        symbols = []
        for d in self.site.decls:
            if id(d) in self.site.url:
                symbols.append([d.name, d.type_label, d.qualified if d.parent else f'{d.file.package}.{d.name}', self.site.url[id(d)]])
        for path, _, is_test, _, _ in self.site.ref_files:
            symbols.append([path.rsplit('/', 1)[-1], 'file', path, Site.src_url(path)])
        for u, t in self.concepts:
            symbols.append([t, 'article', 'Concepts', u])
        data = {'nav': nav, 'symbols': symbols}
        (DOC / 'assets').mkdir(parents=True, exist_ok=True)
        (DOC / 'assets' / 'data.js').write_text('window.DOC_DATA = ' + json.dumps(data, ensure_ascii=False, separators=(',', ':')) + ';\n', encoding='utf-8')

    def run(self):
        for sub in ('api', 'src', 'concepts'):
            shutil.rmtree(DOC / sub, ignore_errors=True)
        self.concept_pages()
        self.api_index()
        for pkg in self.site.packages:
            self.package_page(pkg)
        for t in self.site.types:
            self.type_page(t)
        files_by_path = {f.path: f for f in self.site.files}
        for path, module, _, orig, _ in self.site.ref_files:
            sf = files_by_path.get(path)
            text = sf.scan.text if sf else '\n'.join(orig)
            self.source_page(path, module, text, sf.scan.kinds if sf else None, sf)
        self.source_index()
        self.data_js()


PACKAGE_INTROS = {
    '(root)': 'Application entry points: the Hilt application class and the single activity.',
    'admin': 'Device administrator receiver used by focus sessions.',
    'apps': 'Installed applications: labels, icons and launchable apps.',
    'block': 'Full-screen block screen shown over a blocked app.',
    'dashboard': 'Dashboard data loading and daily usage snapshots (WorkManager).',
    'di': 'Hilt modules of the application module.',
    'diagnostic': 'Diagnostic mode: live overlay and accessibility tree captures.',
    'enforcement': 'Bridge between Android (foreground app, usage statistics, system whitelist) and the rule engine.',
    'lock': 'Trusted person code: in-memory unlock state on top of the domain lock policy.',
    'navigation': 'Navigation Compose graph and routes.',
    'permissions': 'Live permission checks and system settings intents.',
    'service': 'The accessibility service, source of truth for the foreground app.',
    'session': 'Focus session foreground service, boot receiver and protection alert.',
    'ui.apps': 'App icon and app picker components.',
    'ui.components': 'Shared Compose components (time picker, limit picker).',
    'ui.dashboard': 'Dashboard screen, charts and texts.',
    'ui.diagnostic': 'Diagnostic screen: overlay switch, capture viewer and detector editor.',
    'ui.format': 'Shared time and duration formats.',
    'ui.home': 'Home screen: status, today card, focus session card and profile list.',
    'ui.lock': 'Trusted person code UI: banner, PIN dialogs and settings screen.',
    'ui.onboarding': 'Permission onboarding screen.',
    'ui.profiles': 'Profile and default rules editor.',
    'ui.session': 'Start and override screens of focus sessions.',
    'ui.settings': 'Settings screen.',
    'ui.theme': 'Material 3 theme and extended colours.',
    'ui.zones': 'Zones and quotas settings screen.',
    'zones': 'Zone enforcement: detection, exit, entry point masking and zone time recording.',
    'data.db': 'Room database, entities, DAOs and mappers.',
    'data.di': 'Hilt modules binding repositories, database and DataStore.',
    'data.repository': 'Repository implementations backed by Room and DataStore.',
    'domain.dashboard': 'Pure dashboard calculations (totals, trends, zones, interruptions, goals).',
    'domain.foreground': 'Foreground app tracking from window events.',
    'domain.lock': 'Trusted person code: PIN hashing and attempt policy.',
    'domain.model': 'Core models (events, profiles, sessions, preferences).',
    'domain.onboarding': 'Onboarding state derived from permission statuses.',
    'domain.profiles': 'Profiles: defaults, validation, ordering, manual activation, deletion.',
    'domain.repository': 'Repository interfaces implemented by :core:data.',
    'domain.rules': 'Rule model, profile resolution and the rule engine.',
    'domain.session': 'Focus sessions: policy, manager and protected screen detection.',
    'domain.time': 'Time abstraction, schedules and day boundaries.',
    'domain.usage': 'Screen time computation from usage events.',
    'domain.zones': 'Zone detectors (JSON), matching, exit planning, zone time accounting and default zone rules.',
}


def write_missing(site: Site, out: Path):
    lines = []
    for fr, d in sorted(site.missing.items(), key=lambda kv: (kv[1].file.path, kv[1].line)):
        lines.append(f'# {d.file.path}:{d.line} {d.qualified}\n@@ fr\n{fr}\n@@ en\n\n')
    out.write_text(''.join(lines), encoding='utf-8')
    print(f'{len(site.missing)} untranslated KDoc blocks written to {out}')


def main():
    files = load_sources()
    site = Site(files)
    gen = Generator(site)
    gen.run()
    n_types = len(site.types)
    n_members = len(site.decls) - n_types
    print(f'{len(gen.written)} pages, {n_types} types, {n_members} members, '
          f'{len(site.missing)} KDoc blocks without English translation')
    if '--missing' in sys.argv:
        write_missing(site, Path(sys.argv[sys.argv.index('--missing') + 1]))


if __name__ == '__main__':
    main()
