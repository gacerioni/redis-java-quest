#!/usr/bin/env python3
"""Check local href/src destinations, including links embedded in raw Markdown HTML."""
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit


class References(HTMLParser):
    def __init__(self):
        super().__init__()
        self.references = []

    def handle_starttag(self, tag, attrs):
        for name, value in attrs:
            if value and name in ("href", "src"):
                self.references.append(value)


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "course/site").resolve()
    if not (root / "index.html").is_file():
        sys.exit(f"No generated site at {root}")
    failures = []
    checked = 0
    for page in root.rglob("*.html"):
        if page.name == "404.html":
            continue  # Relative links depend on the missing URL in a 404 page.
        parser = References()
        parser.feed(page.read_text(encoding="utf-8"))
        for ref in parser.references:
            url = urlsplit(ref)
            if url.scheme or url.netloc or not url.path:
                continue
            path = unquote(url.path)
            if path.startswith("/redisjava/"):
                target = root / path.removeprefix("/redisjava/")
            elif path.startswith("/"):
                continue  # Another route on the host.
            else:
                target = page.parent / path
            target = target.resolve()
            checked += 1
            if not target.is_relative_to(root) or not target.exists() or (target.is_dir() and not (target / "index.html").is_file()):
                failures.append(f"{page.relative_to(root)} -> {ref}")
    if failures:
        sys.exit("Broken site references:\n" + "\n".join(failures))
    print(f"Site links: {checked} local references checked, no missing destinations.")


if __name__ == "__main__":
    main()
