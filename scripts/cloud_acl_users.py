#!/usr/bin/env python3
"""Creates one Redis Cloud ACL user per student on a shared (Pro) database, so every student gets
their own credentials and their own key prefix (the course derives the prefix from the username).

    export REDIS_CLOUD_API_KEY=...        # Account key (Redis Cloud console > Account settings > API keys)
    export REDIS_CLOUD_API_SECRET=...     # User key
    python3 scripts/cloud_acl_users.py --subscription 123456 --database 7890 \
        --endpoint redis-12345.c1.sa-east-1-1.ec2.redns.redis-cloud.com:12345 \
        --students students.txt --out students-credentials.csv

students.txt: one username per line (lowercase letters, digits, dash). The output CSV has name, password and
the ready-to-paste REDIS_URL. Re-running is safe: existing rules, roles and users are reused.
Cleanup: add --delete to remove every rule, role and user created by this script (names start with quest-).

Per-student ACL rule (override with --rule): +@all minus the destructive and admin commands, keys under <name>:*.
Pub/sub is permissive on Redis Cloud, so the chat lesson works without channel rules.
"""
import argparse
import csv
import json
import os
import re
import secrets
import string
import sys
import time
import urllib.error
import urllib.request

API = "https://api.redislabs.com/v1"
DEFAULT_RULE = "+@all -flushall -flushdb -keys -debug -config -monitor -shutdown -acl -migrate -replicaof -save -bgsave ~{name}:*"


def headers():
    key, secret = os.environ.get("REDIS_CLOUD_API_KEY"), os.environ.get("REDIS_CLOUD_API_SECRET")
    if not key or not secret:
        sys.exit("set REDIS_CLOUD_API_KEY and REDIS_CLOUD_API_SECRET")
    return {"x-api-key": key, "x-api-secret-key": secret, "Content-Type": "application/json", "Accept": "application/json"}


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method, headers=headers())
    try:
        with urllib.request.urlopen(req, timeout=60) as res:
            raw = res.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {path} -> HTTP {e.code}: {e.read().decode(errors='replace')[:400]}")


def wait_task(task):
    task_id = task.get("taskId")
    if not task_id:
        return task
    for _ in range(90):
        t = call("GET", f"/tasks/{task_id}")
        status = t.get("status", "")
        if status == "processing-completed":
            return t.get("response", {})
        if status in ("processing-error", "failed"):
            sys.exit(f"task {task_id} failed: {json.dumps(t)[:600]}")
        time.sleep(2)
    sys.exit(f"task {task_id} timed out")


def existing(kind):
    data = call("GET", f"/acl/{kind}")
    for key in (kind, "redisRules", "roles", "users"):
        if isinstance(data.get(key), list):
            return {item["name"]: item for item in data[key]}
    return {}


def password():
    alphabet = string.ascii_letters + string.digits
    while True:
        p = "".join(secrets.choice(alphabet) for _ in range(20))
        if re.search(r"[a-z]", p) and re.search(r"[A-Z]", p) and re.search(r"\d", p):
            return p


def create(args):
    names = [n.strip() for n in open(args.students, encoding="utf-8") if n.strip() and not n.startswith("#")]
    for n in names:
        if not re.fullmatch(r"[a-z0-9-]{3,32}", n):
            sys.exit(f"invalid username '{n}': use lowercase letters, digits and dash")
    rules, roles, users = existing("redisRules"), existing("roles"), existing("users")
    rows = []
    for name in names:
        rule_name = role_name = f"quest-{name}"
        if rule_name not in rules:
            print(f"rule  {rule_name}")
            wait_task(call("POST", "/acl/redisRules", {"name": rule_name, "redisRule": args.rule.format(name=name)}))
        if role_name not in roles:
            print(f"role  {role_name}")
            wait_task(call("POST", "/acl/roles", {"name": role_name, "redisRules": [
                {"ruleName": rule_name, "databases": [{"subscriptionId": args.subscription, "databaseId": args.database}]}]}))
        pw = None
        if name not in users:
            pw = password()
            print(f"user  {name}")
            wait_task(call("POST", "/acl/users", {"name": name, "role": role_name, "password": pw}))
        else:
            print(f"user  {name} already exists (password not changed; use --delete first to rotate)")
        rows.append({"name": name, "password": pw or "(unchanged)",
                     "redis_url": f"redis://{name}:{pw or 'SENHA'}@{args.endpoint}" if args.endpoint else ""})
    with open(args.out, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["name", "password", "redis_url"])
        w.writeheader()
        w.writerows(rows)
    print(f"\n{len(rows)} students -> {args.out} (keep it private, it holds passwords)")


def delete(args):
    for kind, label in (("users", "user"), ("roles", "role"), ("redisRules", "rule")):
        for name, item in existing(kind).items():
            if name.startswith("quest-") or (kind == "users" and item.get("role", "").startswith("quest-")):
                print(f"delete {label} {name}")
                wait_task(call("DELETE", f"/acl/{kind}/{item['id']}"))


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--subscription", type=int, help="Pro subscription id")
    ap.add_argument("--database", type=int, help="database id inside the subscription")
    ap.add_argument("--endpoint", help="host:port of the database, to render ready-to-use URLs")
    ap.add_argument("--students", default="students.txt")
    ap.add_argument("--out", default="students-credentials.csv")
    ap.add_argument("--rule", default=DEFAULT_RULE, help="ACL rule template, {name} is the username")
    ap.add_argument("--delete", action="store_true", help="remove everything this script created")
    a = ap.parse_args()
    if a.delete:
        delete(a)
    else:
        if not a.subscription or not a.database:
            sys.exit("--subscription and --database are required")
        create(a)
