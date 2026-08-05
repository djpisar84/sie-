#!/usr/bin/env python3
"""
Importuje embeddingi z JSON/JSONL do prostego chroma-store lub zapisuje jako JSONL backup.
Format wejścia akceptowany:
- JSON array: [{"id": ..., "vector": [...], "metadata": {...}}, ...]
- JSONL: jedna struktura JSON na linię

Użycie:
  python app\import_embeddings.py --input embeddings.json --out ./chroma_store
"""
import argparse
import json
import os


def read_entries(path):
    # JSON array
    with open(path, 'r', encoding='utf-8') as f:
        first = f.read(2)
        f.seek(0)
        if first.startswith('['):
            return json.load(f)
        else:
            # JSONL
            entries = []
            for line in f:
                line = line.strip()
                if not line:
                    continue
                entries.append(json.loads(line))
            return entries


def normalize_entry(e):
    # oczekujemy kluczy id, vector, metadata
    out = {}
    out['id'] = e.get('id') or e.get('uuid') or e.get('key')
    out['vector'] = e.get('vector') or e.get('embedding') or e.get('values')
    out['metadata'] = e.get('metadata') or e.get('meta') or {}
    return out


def save_jsonl(entries, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, 'embeddings.jsonl')
    with open(out_path, 'w', encoding='utf-8') as f:
        for e in entries:
            json.dump(e, f, ensure_ascii=False)
            f.write('\n')
    print('Zapisano', out_path)


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--input', '-i', required=True)
    p.add_argument('--out', '-o', required=True, help='Katalog docelowy (np. ./chroma_store)')
    args = p.parse_args()

    entries = read_entries(args.input)
    normalized = []
    for e in entries:
        n = normalize_entry(e)
        if n['id'] is None or n['vector'] is None:
            print('Pominięto wpis bez id lub vector:', e)
            continue
        normalized.append(n)

    # Próba zapisu do chroma, jeśli jest zainstalowane
    try:
        import chromadb
        from chromadb.config import Settings
        client = chromadb.Client(Settings(chroma_db_impl='duckdb', persist_directory=args.out))
        collection = client.create_collection('claude_import', get_or_create=True)
        ids = [e['id'] for e in normalized]
        vectors = [e['vector'] for e in normalized]
        metadatas = [e.get('metadata', {}) for e in normalized]
        collection.add(ids=ids, embeddings=vectors, metadatas=metadatas)
        client.persist()
        print('Zaimportowano do Chroma w', args.out)
    except Exception as ex:
        print('Chroma niedostępne lub wystąpił błąd, zapisuję jako JSONL fallback. Szczegóły:', ex)
        save_jsonl(normalized, args.out)
