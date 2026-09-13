# Redis Java Quest

A self-paced Redis course for Java developers, with **Jedis** and **Lettuce** side by side.
You build the server of a fictional MMORPG, **Ember Realm**, against your own Redis (Redis Cloud free tier or Docker),
one short lesson at a time: cache, sessions, queues, events, search, and production practices.

- Course site (PT-BR): https://platformengineer.io/redisjava/
- 5 courses, 24 lessons, every lesson runnable with both clients and verified with an automatic check.
- Code and comments in English, lesson prose and console output in Brazilian Portuguese (the audience).

![Redis Java Quest landing page](docs/screens/landing-1280.png)

## Quick start

Requirements: Java 21+, Git. Maven is optional (`./mvnw` downloads it). The course site walks a newcomer through all of this in "Lição 0: do zero ao primeiro comando".

```bash
git clone https://github.com/gacerioni/redis-java-quest.git
cd redis-java-quest
cp .env.example .env            # paste your Redis URL (redis://default:PASSWORD@host:port)
./quest doctor                  # first run builds the jar
./quest seed                    # loads the Ember Realm world under your key prefix
./quest start 100-02            # setup + the steps of the lesson
./quest run 100-02 jedis        # step 1: run the ready lab (or lettuce)
./quest verify 100-02           # checks every step in your Redis
./quest solve 100-02            # stuck? does the next step for you (skip marks it as skipped)
```

Every lesson is a small workflow with the lab lifecycle of guided platforms: `start` (setup), `verify`, `solve`, `skip`.
Lessons also have a "Sua vez" step: a `JedisExercise`/`LettuceExercise` stub with a hole the student must implement
(`./quest exercise <id> jedis`); reference solutions live in `solutions/`.

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
| Fundamentos (100) | Topologies (OSS, Cluster, Redis Cloud proxy), connect, Redis Insight, keys/TTL/SCAN, pipeline and MULTI |
| Tipos (101) | String, Hash, List, Set, Sorted Set, Bloom |
| Eventos (102) | Pub/Sub, Streams, consumer groups, blocking connections |
| Busca (201) | JSON + FT.SEARCH, FT.AGGREGATE, FT.HYBRID, vector sets |
| Produção (301) | Timeouts/pool/retry, client-side caching, TLS, smart client handoffs, Active-Active failover |

## Testing

```bash
./mvnw -q test                                   # unit tests
REDIS_URL=redis://localhost:6379 scripts/smoke_all.sh   # full gauntlet against a Redis
```

The gauntlet is the definition of done: every lesson runs with Jedis, runs with Lettuce, and its check passes.
`scripts/dod.sh` chains the text lint, the unit tests, the gauntlet and a strict site build in one command.

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
