## 🚀 Live Demo

The API is currently deployed and running live on Render. 
- **Base URL:** `(https://grid07-assignment.onrender.com)`

*Note: Since this is a backend microservice, visiting the URL directly in a browser won't show a webpage. Please import the provided Postman collection and replace `localhost:8080` with the Base URL to test the endpoints.*

# Grid07 Social Bot 

This is my submission for the Grid07 backend assignment. I built a Spring Boot microservice that simulates a social platform where bots can interact with human posts, with Redis-based guardrails to prevent abuse.

## What I used

- Java 17 + Spring Boot 3.2
- PostgreSQL for persistent storage
- Redis for all the rate limiting and virality tracking stuff
- Docker Compose (just for postgres and redis — I run the app from IntelliJ)

## How to run it

Start postgres and redis first:

```bash
docker-compose up -d
```

Then run the app from your IDE or:

```bash
./mvnw spring-boot:run
```

App starts on `http://localhost:8080`. Import the Postman collection to test everything.

---

## Project structure

```
src/main/java/com/grid07/socialbot/
├── SocialbotApplication.java
├── config/
│   ├── RedisConfig.java
│   └── GlobalExceptionHandler.java
├── controller/
│   ├── PostController.java
│   ├── UserBotController.java
│   └── DebugController.java
├── dto/
├── entity/
├── repository/
├── scheduler/
│   └── NotificationSweeper.java
└── service/
    ├── RedisViralityService.java
    ├── PostService.java
    ├── CommentService.java
    └── UserBotService.java
```

---

## API endpoints

| Method | Path | What it does |
|--------|------|-------------|
| POST | `/api/users` | create a user |
| POST | `/api/bots` | create a bot |
| POST | `/api/posts` | create post (user or bot) |
| GET | `/api/posts/{id}` | get post + virality score |
| POST | `/api/posts/{id}/comments` | add comment (with guardrails) |
| GET | `/api/posts/{id}/comments` | list comments |
| POST | `/api/posts/{id}/like` | like a post (human only) |
| GET | `/api/debug/posts/{id}/stats` | see redis stats for a post |

---

## How I approached the Redis guardrails

This was honestly the most interesting part of the assignment. I had to implement three types of caps:

### 1. Horizontal cap (bot reply limit per post)

My approach was to use a Lua script for this. The reason is that you need the INCR + check + optional DECR to all happen atomically, and the only way to do that in Redis is with Lua. If I used regular Java code (read, compare, write), there'd be race conditions with concurrent requests.

```lua
local key = KEYS[1]
local max = tonumber(ARGV[1])
local current = redis.call('INCR', key)
if current > max then
    redis.call('DECR', key)
    return -1
end
return current
```

I struggled a bit figuring out why MULTI/EXEC wouldn't work here — turns out optimistic locking needs retries and still doesn't guarantee exact stopping at 100. Lua runs as a single atomic unit so it just works.

### 2. Vertical cap (comment depth)

Simple check — if depthLevel > 20, return 400. Nothing fancy needed here.

### 3. Cooldown cap (bot-human throttle)

I used `SETNX` with a TTL for this. It's atomic by nature — if the key already exists the set fails and we know the bot is on cooldown. Redis handles the expiry so I don't need any cleanup logic.

---

## Notification engine

When a bot comments on a human's post, the human should get notified. But I didn't want to spam them if multiple bots pile on, so I added a 15-minute cooldown per user. If they're on cooldown, the notification goes into a Redis list instead.

A cron job (`NotificationSweeper`) runs every 5 minutes and sweeps all the pending notifications, batching them into a single message per user. The SCAN + LRANGE + DEL pattern drains the list atomically.

---

## The 200 concurrent requests test

To verify exactly 100 bot replies are accepted out of 200 concurrent:

```bash
# need to send 200 concurrent requests with a bot authorId
ab -n 200 -c 200 -p /tmp/payload.json -T application/json \
   http://localhost:8080/api/posts/{POST_ID}/comments
```

Should see ~100 HTTP 201 and ~100 HTTP 429. Then check redis:

```bash
docker exec -it grid07-redis redis-cli GET "post:{POST_ID}:bot_count"
# should say 100
```

// TODO: maybe add pagination later for the comments list endpoint

---

## Config

```yaml
app:
  notification:
    cooldown-minutes: 15
    sweeper-cron: "0 */5 * * * *"
  bot:
    cooldown-minutes: 10
    max-bot-replies: 100
    max-comment-depth: 20
```
