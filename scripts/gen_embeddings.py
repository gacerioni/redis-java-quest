#!/usr/bin/env python3
"""Adds an `embedding` (384 floats) to every item and query in src/main/resources/world.

Uses a local Ollama with the all-minilm model, the same one lesson 201-03 uses for live questions.
The output is committed to the repo so students never need Ollama.

    ollama pull all-minilm
    python3 scripts/gen_embeddings.py
"""
import json
import os
import pathlib
import sys
import urllib.request

OLLAMA = os.environ.get("OLLAMA_URL", "http://localhost:11434")
MODEL = "all-minilm"
WORLD = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/world"


def embed(text: str) -> list[float]:
    body = json.dumps({"model": MODEL, "prompt": text}).encode()
    req = urllib.request.Request(f"{OLLAMA}/api/embeddings", data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as res:
        vec = json.load(res)["embedding"]
    return [round(float(x), 6) for x in vec]


def process(path: pathlib.Path, text_of) -> None:
    docs = json.loads(path.read_text(encoding="utf-8"))
    for doc in docs:
        doc["embedding"] = embed(text_of(doc))
    dims = {len(d["embedding"]) for d in docs}
    assert dims == {384}, f"unexpected dimensions {dims} in {path.name}"
    path.write_text(json.dumps(docs, ensure_ascii=False, indent=0) + "\n", encoding="utf-8")
    print(f"{path.name}: {len(docs)} docs embedded ({MODEL}, 384 dims)")


if __name__ == "__main__":
    try:
        process(WORLD / "items.json", lambda d: f"{d['name']}. {d['description']}")
        process(WORLD / "queries.json", lambda d: d["text"])
    except Exception as exc:  # noqa: BLE001
        print(f"failed: {exc}. Is Ollama running with `ollama pull {MODEL}`?", file=sys.stderr)
        sys.exit(1)
