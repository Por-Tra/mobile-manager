"""Minimal Kotlin source parser for the documentation generator.

It is not a compiler front end: it recognises the declarations that matter for an API reference
(types, functions, properties, enum entries, primary constructor properties), their KDoc, their
annotations and their nesting, which is enough for a Doxygen-like reference of this code base.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field

CODE, COMMENT, STRING = 0, 1, 2


@dataclass
class Scan:
    """Source text plus three aligned views (same length, same line breaks)."""
    text: str
    code: str        # comments and string literals blanked (template expressions kept)
    nocomment: str   # only comments blanked (used for signatures)
    kinds: bytearray  # CODE / COMMENT / STRING for every character (syntax highlighting)


def scan(text: str) -> Scan:
    n = len(text)
    code = list(text)
    nocom = list(text)
    kinds = bytearray(n)

    def blank(arr, a, b):
        for k in range(a, b):
            if arr[k] != '\n':
                arr[k] = ' '

    def mark(a, b, kind):
        for k in range(a, b):
            kinds[k] = kind

    def scan_code(i: int, in_template: bool) -> int:
        depth = 0
        while i < n:
            c = text[i]
            if c == '/' and i + 1 < n and text[i + 1] == '/':
                j = text.find('\n', i)
                j = n if j < 0 else j
                blank(code, i, j); blank(nocom, i, j); mark(i, j, COMMENT)
                i = j
            elif c == '/' and i + 1 < n and text[i + 1] == '*':
                j, level = i + 2, 1
                while j < n and level:
                    if text.startswith('/*', j):
                        level += 1; j += 2
                    elif text.startswith('*/', j):
                        level -= 1; j += 2
                    else:
                        j += 1
                blank(code, i, j); blank(nocom, i, j); mark(i, j, COMMENT)
                i = j
            elif text.startswith('"""', i):
                i = scan_string(i, raw=True)
            elif c == '"':
                i = scan_string(i, raw=False)
            elif c == "'":
                j = i + 1
                if j < n and text[j] == '\\':
                    j += 2
                    while j < n and text[j] != "'":
                        j += 1
                else:
                    j += 1
                j = min(j + 1, n)
                blank(code, i, j); mark(i, j, STRING)
                i = j
            elif c == '{':
                depth += 1; i += 1
            elif c == '}':
                if in_template and depth == 0:
                    return i
                depth -= 1; i += 1
            else:
                i += 1
        return i

    def scan_string(i: int, raw: bool) -> int:
        quote = '"""' if raw else '"'
        start = i
        i += len(quote)
        seg = start
        while i < n:
            if not raw and text[i] == '\\':
                i += 2
                continue
            if text.startswith(quote, i):
                if raw:  # a raw string may end with more than three quotes
                    while text.startswith('""""', i):
                        i += 1
                i += len(quote)
                blank(code, seg, i); mark(seg, i, STRING)
                return i
            if text.startswith('${', i):
                blank(code, seg, i + 2); mark(seg, i + 2, STRING)
                end = scan_code(i + 2, in_template=True)
                if end < n:
                    code[end] = ' '
                    kinds[end] = STRING
                i = end + 1
                seg = i
                continue
            if not raw and text[i] == '\n':  # unterminated, give up on this line
                break
            i += 1
        blank(code, seg, i); mark(seg, i, STRING)
        return i

    scan_code(0, in_template=False)
    return Scan(text, ''.join(code), ''.join(nocom), kinds)


# ---------------------------------------------------------------------------------------------
# Declarations

MODIFIERS = (
    'public', 'private', 'internal', 'protected', 'override', 'open', 'abstract', 'final',
    'sealed', 'data', 'enum', 'annotation', 'inner', 'value', 'inline', 'suspend', 'operator',
    'infix', 'tailrec', 'external', 'const', 'lateinit', 'companion', 'expect', 'actual',
    'crossinline', 'noinline', 'reified',
)
VISIBILITIES = ('public', 'private', 'internal', 'protected')

DECL_RE = re.compile(
    r'^(?P<indent>\s*)(?P<annos>(?:@[\w.]+(?:\([^()]*\))?\s+)*)'
    r'(?P<mods>(?:(?:' + '|'.join(MODIFIERS) + r')\s+)*)'
    r'(?P<kw>fun\s+interface|class|interface|object|fun|val|var|typealias)\b(?P<rest>.*)$'
)
NAME_AFTER_TYPE_KW = re.compile(r'\s+(?P<name>`[^`]+`|[A-Za-z_]\w*)')
FUN_NAME = re.compile(
    r'\s*(?:<[^>]*(?:<[^>]*>[^>]*)*>\s*)?'          # type parameters
    r'(?:(?P<recv>[\w.<>?, *]+?)\.)?'                # receiver for extensions
    r'(?P<name>`[^`]+`|[A-Za-z_]\w*)\s*\('
)
PROP_NAME = re.compile(
    r'\s*(?:<[^>]*>\s*)?(?:(?P<recv>[\w.<>?, *]+?)\.)?(?P<name>`[^`]+`|[A-Za-z_]\w*)\s*(?::|=|by\b|$)'
)
WORD = re.compile(r'[A-Za-z_]\w*')


@dataclass
class Decl:
    kind: str                  # class, interface, object, enum, annotation, typealias, function, property, enum-entry
    name: str
    file: 'SourceFile'
    line: int                  # 1-based line of the declaration keyword
    signature: str = ''
    modifiers: list = field(default_factory=list)
    annotations: list = field(default_factory=list)
    kdoc: str = ''             # cleaned KDoc (logical lines, see clean_kdoc)
    parent: 'Decl | None' = None
    companion: bool = False
    receiver: str | None = None
    supertypes: list = field(default_factory=list)
    ctor_params: list = field(default_factory=list)   # (name, type, is_property) for classes
    members: list = field(default_factory=list)
    from_ctor: bool = False
    end_line: int = 0

    @property
    def visibility(self) -> str:
        for v in VISIBILITIES:
            if v in self.modifiers:
                return v
        return 'public'

    @property
    def is_type(self) -> bool:
        return self.kind in ('class', 'interface', 'object', 'enum', 'annotation')

    @property
    def qualified(self) -> str:
        chain = []
        d = self
        while d is not None:
            chain.append(d.name)
            d = d.parent
        return '.'.join(reversed(chain))

    @property
    def type_label(self) -> str:
        """Human label of the kind, MS Learn style (« Class », « Interface »…)."""
        m = self.modifiers
        if self.kind == 'class':
            if 'data' in m:
                return 'data class'
            if 'sealed' in m:
                return 'sealed class'
            if 'abstract' in m:
                return 'abstract class'
            if 'value' in m:
                return 'value class'
            return 'class'
        if self.kind == 'interface':
            if 'sealed' in m:
                return 'sealed interface'
            if 'fun' in m:
                return 'fun interface'
            return 'interface'
        if self.kind == 'object':
            return 'data object' if 'data' in m else ('companion object' if self.companion else 'object')
        if self.kind == 'enum':
            return 'enum class'
        if self.kind == 'annotation':
            return 'annotation class'
        if self.kind == 'function':
            if self.receiver:
                return 'extension function'
            return 'method' if self.parent else 'function'
        if self.kind == 'property':
            if 'const' in m:
                return 'constant'
            if self.receiver:
                return 'extension property'
            return 'property'
        if self.kind == 'enum-entry':
            return 'enum value'
        return self.kind


@dataclass
class SourceFile:
    path: str          # repository-relative, forward slashes
    module: str
    is_test: bool
    package: str = ''
    scan: Scan | None = None
    decls: list = field(default_factory=list)       # top-level declarations
    all_decls: list = field(default_factory=list)   # every declaration, in source order
    line_starts: list = field(default_factory=list)

    def line_of(self, pos: int) -> int:
        lo, hi = 0, len(self.line_starts) - 1
        while lo < hi:
            mid = (lo + hi + 1) // 2
            if self.line_starts[mid] <= pos:
                lo = mid
            else:
                hi = mid - 1
        return lo + 1


def clean_kdoc(raw: str) -> str:
    """`/** … */` → logical text: paragraphs separated by a blank line, list items and tags on their own line."""
    body = raw[3:-2] if raw.endswith('*/') else raw[3:]
    lines = []
    for line in body.split('\n'):
        line = line.strip()
        if line.startswith('*'):
            line = line[1:]
            if line.startswith(' '):
                line = line[1:]
        lines.append(line.rstrip())
    paragraphs, current = [], []
    for line in lines:
        if not line.strip():
            if current:
                paragraphs.append(current)
                current = []
            continue
        stripped = line.strip()
        starts_item = bool(re.match(r'^(-|\*|\d+\.)\s|^@\w+', stripped)) or stripped.startswith('```')
        if current and not starts_item and not current[-1].startswith('```'):
            current[-1] = current[-1] + ' ' + stripped
        else:
            current.append(stripped)
    if current:
        paragraphs.append(current)
    return '\n\n'.join('\n'.join(p) for p in paragraphs).strip()


def _header_extent(code: str, start: int, kind: str):
    """End of a declaration header and the position of its body brace (or None).

    Types and functions end at their body `{`; functions and properties also at a top-level `=`
    (expression body / initializer); everything ends at a newline outside parentheses unless the
    next line visibly continues the header.
    """
    depth = 0
    i = start
    n = len(code)
    while i < n:
        c = code[i]
        if c in '([':
            depth += 1
        elif c in ')]':
            depth -= 1
        elif depth == 0:
            if c == '{':
                return i, i
            if c == '=' and kind in ('function', 'property', 'typealias'):
                prev = code[i - 1] if i else ''
                nxt = code[i + 1] if i + 1 < n else ''
                if prev not in '=!<>' and nxt not in '=>':
                    return i, None
            if kind == 'property' and code.startswith(' by ', i - 1):
                return i - 1, None
            if c == '\n':
                line_start = code.rfind('\n', 0, i) + 1
                this_line = code[line_start:i].rstrip()
                j = i + 1
                while j < n and code[j] in ' \t\r\n':
                    j += 1
                nxt = code[j:j + 6]
                if this_line.endswith((',', ':', '(', '->', '.')) or nxt.startswith((':', ',', ')', '.', 'where', '->', '{')):
                    i += 1
                    continue
                return i, None
        i += 1
    return n, None


def _split_top_level(code: str, a: int, b: int):
    """Split code[a:b] on commas at nesting depth 0; yields (start, end) ranges."""
    depth = 0
    seg = a
    for i in range(a, b):
        c = code[i]
        if c in '([{<':
            depth += 1
        elif c in ')]}>':
            depth -= 1
        elif c == ',' and depth == 0:
            yield seg, i
            seg = i + 1
    if seg < b:
        yield seg, b


def _kdoc_before(text: str, pos: int, floor: int = 0) -> str:
    """KDoc ending right before pos (only whitespace in between), or ''."""
    j = pos
    while j > floor and text[j - 1] in ' \t\r\n':
        j -= 1
    if j >= 2 and text[j - 2:j] == '*/':
        start = text.rfind('/**', floor, j - 2)
        if start >= 0 and '*/' not in text[start + 3:j - 2]:
            return clean_kdoc(text[start:j])
    return ''


def _annotation_block_start(sf: SourceFile, decl_line_idx: int) -> int:
    """Index of the first line of the annotations stacked above a declaration line."""
    lines = sf.scan.code.split('\n')
    idx = decl_line_idx
    while idx > 0:
        prev = lines[idx - 1].strip()
        if prev.startswith('@'):
            idx -= 1
            continue
        if prev.endswith(')') and prev:
            # multi-line annotation such as @Database(…)
            depth, k = 0, idx - 1
            found = None
            while k >= 0 and found is None:
                for ch in reversed(lines[k]):
                    if ch == ')':
                        depth += 1
                    elif ch == '(':
                        depth -= 1
                        if depth == 0:
                            found = k
                            break
                if found is None:
                    k -= 1
            if found is not None and lines[found].strip().startswith('@'):
                idx = found
                continue
        break
    return idx


def _clean_signature(s: str) -> str:
    s = re.sub(r'\s+', ' ', s).strip()
    s = re.sub(r'\(\s+', '(', s)
    s = re.sub(r'\s+\)', ')', s)
    s = re.sub(r'\s+,', ',', s)
    s = re.sub(r',\s*\)', ')', s)
    s = s.rstrip(' {=')
    return s


def parse_file(sf: SourceFile, text: str) -> None:
    sc = scan(text)
    sf.scan = sc
    code = sc.code
    starts = [0]
    for m in re.finditer('\n', text):
        starts.append(m.end())
    sf.line_starts = starts

    pkg = re.search(r'^\s*package\s+([\w.]+)', code, re.M)
    sf.package = pkg.group(1) if pkg else ''

    lines = code.split('\n')
    candidates = []  # (keyword_pos, Decl, body_brace_pos)
    for idx, line in enumerate(lines):
        m = DECL_RE.match(line)
        if not m:
            continue
        kw = re.sub(r'\s+', ' ', m.group('kw'))
        rest = m.group('rest')
        mods = m.group('mods').split()
        line_pos = starts[idx]
        kw_pos = line_pos + m.start('kw')
        annos = re.findall(r'@[\w.]+(?:\([^()]*\))?', m.group('annos'))

        if kw in ('class', 'interface', 'object', 'fun interface'):
            nm = NAME_AFTER_TYPE_KW.match(rest)
            if kw == 'object' and 'companion' in mods:
                name = nm.group('name') if nm and nm.group('name') not in ('{',) else 'Companion'
            elif not nm:
                continue  # anonymous object expression
            else:
                name = nm.group('name')
            if 'enum' in mods:
                kind = 'enum'
            elif 'annotation' in mods:
                kind = 'annotation'
            elif kw == 'fun interface':
                kind, mods = 'interface', mods + ['fun']
            else:
                kind = kw
            decl_kind_for_extent = 'type'
        elif kw == 'fun':
            fm = FUN_NAME.match(rest)
            if not fm:
                continue
            name, kind = fm.group('name'), 'function'
            decl_kind_for_extent = 'function'
        elif kw in ('val', 'var'):
            pm = PROP_NAME.match(rest)
            if not pm:
                continue
            name, kind = pm.group('name'), 'property'
            decl_kind_for_extent = 'property'
        else:  # typealias
            tm = NAME_AFTER_TYPE_KW.match(rest)
            if not tm:
                continue
            name, kind = tm.group('name'), 'typealias'
            decl_kind_for_extent = 'typealias'

        end, body = _header_extent(code, kw_pos, decl_kind_for_extent)
        header_start = line_pos + len(m.group('indent'))
        signature = _clean_signature(sc.nocomment[header_start:end])
        if kw in ('val', 'var'):
            signature = signature.split(' by ')[0]
        anno_idx = _annotation_block_start(sf, idx)
        if anno_idx < idx:
            for k in range(anno_idx, idx):
                annos = re.findall(r'@[\w.]+(?:\([^()]*\))?', re.sub(r'\s+', ' ', ' '.join(lines[anno_idx:idx]))) + annos
                break
        # strip annotations from the signature, they are shown separately
        signature = re.sub(r'^(?:@[\w.]+(?:\([^()]*\))?\s+)+', '', signature)

        d = Decl(kind=kind, name=name.strip('`'), file=sf, line=idx + 1, signature=signature,
                 modifiers=mods, annotations=annos,
                 companion='companion' in mods)
        d.kdoc = _kdoc_before(text, starts[anno_idx])
        if kind == 'function':
            recv = FUN_NAME.match(rest).group('recv')
            d.receiver = recv.strip() if recv else None
        elif kind == 'property':
            recv = PROP_NAME.match(rest).group('recv')
            d.receiver = recv.strip() if recv else None
        if d.is_type:
            hdr = sc.code[kw_pos:end]
            _parse_type_header(sf, d, kw_pos, end, hdr)
        candidates.append((kw_pos, d, body, end))

    # Block structure: which `{` open a type body.
    bodies = {body: d for _, d, body, _ in candidates if d.is_type and body is not None}
    cand_iter = iter(sorted(candidates, key=lambda c: c[0]))
    nxt = next(cand_iter, None)
    stack = []  # entries: Decl (type body) or None (other block)
    parens = 0  # declarations inside parentheses are constructor or lambda parameters
    i, n = 0, len(code)
    while i <= n:
        while nxt is not None and nxt[0] <= i:
            _, d, body, end = nxt
            if parens == 0 and all(e is not None for e in stack):
                owner = stack[-1] if stack else None
                if owner is not None and owner.companion:
                    d.parent, d.companion = owner.parent, True
                else:
                    d.parent = owner
                if d.companion and d.kind == 'object':
                    pass  # companion members are merged into the enclosing type
                else:
                    sf.all_decls.append(d)
                    (d.parent.members if d.parent else sf.decls).append(d)
                    for cp in getattr(d, '_ctor_props', []):
                        cp.parent = d
                        sf.all_decls.append(cp)
                        d.members.append(cp)
            nxt = next(cand_iter, None)
        if i == n:
            break
        c = code[i]
        if c == '(':
            parens += 1
        elif c == ')':
            parens = max(0, parens - 1)
        elif c == '{':
            owner = bodies.get(i)
            if owner is not None and owner.kind == 'enum':
                _parse_enum_entries(sf, owner, i)
            stack.append(owner)
        elif c == '}':
            if stack:
                closed = stack.pop()
                if closed is not None:
                    closed.end_line = sf.line_of(i)
        i += 1


def _parse_type_header(sf: SourceFile, d: Decl, kw_pos: int, end: int, hdr: str) -> None:
    code, text = sf.scan.code, sf.scan.text
    # primary constructor
    name_pos = code.find(d.name, kw_pos)
    paren = -1
    j = name_pos + len(d.name) if name_pos >= 0 else kw_pos
    # skip type parameters, annotations and the `constructor` keyword
    depth = 0
    while j < end:
        ch = code[j]
        if ch == '<':
            depth += 1
        elif ch == '>':
            depth -= 1
        elif ch == '(' and depth == 0:
            paren = j
            break
        elif ch in ':{' and depth == 0:
            break
        j += 1
    after = paren
    props = []
    if paren >= 0:
        level, k = 0, paren
        while k < end:
            if code[k] == '(':
                level += 1
            elif code[k] == ')':
                level -= 1
                if level == 0:
                    break
            k += 1
        close = k
        for a, b in _split_top_level(code, paren + 1, close):
            seg = code[a:b]
            pm = re.match(r'\s*((?:@[\w.]+(?:\([^()]*\))?\s+)*)((?:(?:' + '|'.join(MODIFIERS) + r')\s+)*)(val|var)?\s*([A-Za-z_]\w*)\s*:\s*(.+)', seg, re.S)
            if not pm:
                continue
            ptype = re.sub(r'\s+', ' ', sf.scan.nocomment[a + pm.start(5):b]).strip()
            ptype = ptype.split(':', 1)[1].strip() if ':' in ptype else ptype
            is_prop = pm.group(3) is not None
            d.ctor_params.append((pm.group(4), _clean_signature(ptype), is_prop))
            if is_prop:
                kw_at = a + pm.start(3)
                cp = Decl(kind='property', name=pm.group(4), file=sf, line=sf.line_of(kw_at),
                          signature=_clean_signature(pm.group(2) + pm.group(3) + ' ' + pm.group(4) + ': ' + ptype),
                          modifiers=pm.group(2).split(), from_ctor=True,
                          annotations=re.findall(r'@[\w.]+(?:\([^()]*\))?', pm.group(1)))
                cp.kdoc = _kdoc_before(text, a + len(seg) - len(seg.lstrip()), floor=a - 1)
                props.append(cp)
        after = close + 1
    d._ctor_props = props
    # supertypes
    rest = code[(after if after > 0 else (name_pos + len(d.name) if name_pos >= 0 else kw_pos)):end]
    sm = re.match(r'\s*:\s*(.+)', rest, re.S)
    if sm:
        for a, b in _split_top_level(sm.group(1), 0, len(sm.group(1))):
            st = sm.group(1)[a:b].strip()
            st = re.sub(r'\(.*', '', st, flags=re.S).strip()
            st = re.sub(r'\s+by\s+.*', '', st)
            base = re.match(r'[\w.]+', st)
            if base:
                d.supertypes.append(base.group(0))


def _parse_enum_entries(sf: SourceFile, enum: Decl, brace: int) -> None:
    code, text = sf.scan.code, sf.scan.text
    depth, k = 0, brace + 1
    stop = len(code)
    while k < len(code):
        ch = code[k]
        if ch in '({[':
            depth += 1
        elif ch in ')]}':
            if depth == 0:
                stop = k
                break
            depth -= 1
        elif ch == ';' and depth == 0:
            stop = k
            break
        k += 1
    for a, b in _split_top_level(code, brace + 1, stop):
        seg = code[a:b]
        m = re.match(r'\s*((?:@[\w.]+(?:\([^()]*\))?\s+)*)([A-Za-z_]\w*)', seg)
        if not m or m.group(2) in ('fun', 'val', 'var', 'override', 'private', 'companion', 'abstract'):
            continue
        pos = a + m.start(2)
        e = Decl(kind='enum-entry', name=m.group(2), file=sf, line=sf.line_of(pos),
                 signature=_clean_signature(sf.scan.nocomment[pos:b]))
        e.kdoc = _kdoc_before(text, a + m.start(1) if m.group(1) else pos, floor=a - 1)
        e.parent = enum
        enum.members.append(e)
        sf.all_decls.append(e)
