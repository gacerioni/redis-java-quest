# Redis Java Quest

A self-paced Redis course for Java developers, with **Jedis** and **Lettuce** side by side.
Learn backend patterns against your own Redis (Redis Cloud free tier or Docker): sessions, counters, queues, events, search, and production practices. A small fictional game dataset, **Ember Realm**, provides consistent example data.

- Start with the guided workshop (PT-BR): https://platformengineer.io/redisjava/trilha/
- The 60-minute session assumes environment preparation in advance. Without it, the practical goal is PONG + SET/GET + TTL; the remaining topics are demonstrations.
- Full course: https://platformengineer.io/redisjava/
- 5 courses, 24 lessons, each with Jedis and Lettuce implementations and checks. Unsupported or unconfigured capabilities stay pending; a skipped capability is not a successful verification.
- Pinned clients: Jedis 8.0.1 and Lettuce 7.7.0. Both negotiate RESP3 by default, with RESP2 fallback.
- Code and comments in English, lesson prose and console output in Brazilian Portuguese (the audience).

![Redis Java Quest landing page](docs/screens/landing-1280.png)

## Quick start

Requirements: Java 21+, Git. Maven is optional (`./mvnw` downloads it). The course site walks a newcomer through all of this in "Lição 0: do zero ao primeiro comando".

```bash
git clone https://github.com/gacerioni/redis-java-quest.git
cd redis-java-quest
cp .env.example .env            # paste your Redis URL (redis://default:PASSWORD@host:port)
./quest doctor                  # first run builds the jar
./quest run 100-02 jedis        # first SET/GET; no seed required
./quest verify 100-02           # inspect the result in your Redis
# Change the message in l100_02/JedisLab.java, run again, observe your change.
./quest seed                    # now load example profiles, items and ranking
```

The full course also supports `start` (setup), `verify`, `solve`, and explicit `skip`. Most lessons are ready-to-run labs with optional challenges. **String (101-01)** includes the code-completion exercise: implement `castHeal` in `JedisExercise` or `LettuceExercise`, then run `./quest exercise 101-01 jedis`. Reference solutions live in `solutions/l101_01/`.

CLI checks inspect Redis state and execution evidence. They do not certify production readiness or prove concurrency safety. Page buttons record personal study in the browser, separately from CLI progress.

In an IDE, run `com.emberrealm.quest.core.Main` with arguments such as `run 100-02 jedis`, using the repository root as working directory. Individual `Lab` classes do not have their own `main`.

No Redis yet? `docker compose up -d` starts Redis 8 (all modules) on `localhost:6379` and Redis Insight on `http://localhost:5540`.
The local Redis is configured with `maxclients 30` on purpose to mirror the Redis Cloud free plan (lesson 102-04 depends on it).

Windows: use `quest.cmd` instead of `./quest`.

## Layout

| Path | What |
|---|---|
| `src/main/java/com/emberrealm/quest/core` | Tiny framework: `Env` (.env, URL, prefix), `Clients` (Jedis and Lettuce factories), `Console`, `Lab`, `Check`, `Ctx`, CLI `Main` |
| `src/main/java/com/emberrealm/quest/world` | Ember Realm dataset loaders and the `Seed` |
| `src/main/java/com/emberrealm/quest/lessons/l<id>` | One package per lesson: `JedisLab`, `LettuceLab`, `LessonCheck` |
| `src/main/resources/world` | Items (with 384-dim embeddings), players, zones, queries |
| `course/` | MkDocs Material site (PT-BR) |
| `scripts/smoke_all.sh` | The gauntlet: seed, run every lesson with both clients, check everything |
| `scripts/gen_embeddings.py` | Regenerates the embeddings with a local Ollama (`all-minilm`) |

## Lessons

| Course | Lessons |
|---|---|
| Fundamentos (100) | Topologies (standalone, OSS Cluster, Cloud/Software proxy and OSS Cluster API), connect, Redis Insight, keys/TTL/SCAN, pipeline and MULTI |
| Tipos (101) | String, Hash, List, Set, Sorted Set, Bloom |
| Eventos (102) | Pub/Sub, Streams, consumer groups, blocking connections |
| Busca (201) | JSON + FT.SEARCH, FT.AGGREGATE, FT.HYBRID, vector sets |
| Produção (301) | Timeouts/pool/retry, client-side caching, TLS, smart client handoffs, Active-Active failover |

## Testing

```bash
./mvnw -q test                                   # unit tests
REDIS_URL=redis://localhost:6379 scripts/smoke_all.sh   # full gauntlet against a Redis
```

The gauntlet exercises both clients in an isolated temporary checkout, including the String reference solutions and manual-command step. It reports verified, failed and unavailable capabilities separately: TLS and failover require their own environments; missing capability is not a green check. `scripts/dod.sh` chains text lint, unit tests, the gauntlet and a strict site build. See [release validation and publishing](docs/release.md).

## Building and publishing the site

```bash
python3 -m venv .venv && .venv/bin/pip install -r course/requirements.txt
.venv/bin/mkdocs serve -f course/mkdocs.yml       # http://127.0.0.1:8000
.venv/bin/mkdocs build -f course/mkdocs.yml --strict
scripts/deploy_platformengineer.sh                # ships course/site to platformengineer.io/redisjava/
```

## License

MIT. Ember Realm is a fictional world created for this course.

## Running a workshop on one shared Redis Cloud Pro database

Give every student their own ACL user; the course derives the key prefix from the username, so nobody steps on
anybody else's keys and nobody can flush the database.

```bash
export REDIS_CLOUD_API_KEY=...  REDIS_CLOUD_API_SECRET=...
printf 'ana\nbruno\ncarla\n' > students.txt
python3 scripts/cloud_acl_users.py --subscription <subId> --database <dbId> \
    --endpoint <host:port> --students students.txt --out students-credentials.csv
```

Each student pastes their `redis_url` into `.env`. The default rule is `+@all` minus destructive and admin commands,
keys restricted to `<name>:*`. Clean up after the workshop with `--delete`.
