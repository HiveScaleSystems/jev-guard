# jev-guard

AI chat moderation for Minecraft (Paper/Folia) and Hytale servers, powered by [TypeSafe](https://typesafe.ai)'s **Jev** model.

Jev does not generate text. For each chat message it returns calibrated probabilities over the
categories you define (harassment, hate, grooming, scams, …). The plugin applies **your**
thresholds and actions in code. It batches messages into single API calls, so it is fast and cheap.

> Community project, not affiliated with or endorsed by TypeSafe AI. "Jev" and "TypeSafe" belong to their owners.

## Features

- **Three modes:** `shadow` (log only), `deliver` (act after the message appears), `hold` (the message waits for the verdict and can be blocked)
- **Per-category thresholds and actions:** `cancel`, `warn`, `notify-staff`, `kick`, or any console `command:`. It works with LiteBans, EssentialsX, and similar plugins.
- **Staff alerts:** when a message is blocked, everyone with `jevguard.notify` sees who sent it, the message, the category, and how sure Jev was
- **Review band:** borderline messages go to staff and no other action runs
- **Batching:** one API request carries up to N messages
- **Resilience:** retries with backoff on 429/529, a circuit breaker, and a fail-open or fail-closed setting
- **Two providers:** TypeSafe's API directly, or Jev on Cloudflare through **AI Gateway** (logs, analytics, caching, rate limits)
- **Privacy:** only message text is sent. No player names or UUIDs.
- **Folia-safe scheduling**

## Requirements

- Minecraft: Paper (or Folia) 26.2+, Java 25
- Hytale: a Hytale server (Java 25)
- One of:
  - A TypeSafe API key: [console.typesafe.ai](https://console.typesafe.ai)
  - A Cloudflare account, with an API token that has **Workers AI > Read** permission

## Install

1. Download the jar from Releases, or build it (see Development):
   - **Minecraft:** `jev-guard-paper-*.jar` goes in `plugins/`. Config: `plugins/jev-guard/config.yml`.
   - **Hytale:** `jev-guard-hytale-*.jar` goes in `mods/`. Config: `mods/JevGuard_JevGuard/config.yml`.
2. Start the server once so it writes the default config.
3. Choose a provider in `config.yml` and set its credentials. Environment variables are recommended:

   **TypeSafe** (`api.provider: typesafe`)
   ```sh
   export TYPESAFE_API_KEY=ts_...
   ```

   **Cloudflare AI Gateway** (`api.provider: cloudflare`)
   ```sh
   export CLOUDFLARE_ACCOUNT_ID=...
   export CLOUDFLARE_API_TOKEN=...   # Account > Workers AI > Read
   ```
   Set `api.cloudflare.gateway-id` to choose a gateway. If it is empty, your account's default gateway is used.
   Requests go to `api.cloudflare.com/.../ai/run` with model `typesafe/jev`, and they appear in the AI Gateway dashboard.
   Billing is through your Cloudflare account.
4. Run `/jevguard reload`.

On Hytale, `hytale:Admin` (`*`) already has every permission. To give them to another group, use the
built-in permission commands, for example `/perm group add <group> jevguard.notify`.

## Recommended rollout

1. Run in `mode: shadow` for a few days. Flagged and review-worthy messages go to the console and to staff.
2. Use `/jevguard test <message>` to see the full probability distribution for edge cases.
3. Tune each category's `threshold` and `description`. Jev reads descriptions **literally**, so write the exact condition.
4. Change to `deliver` or `hold`.

## Commands and permissions

| Command | Permission | |
|---|---|---|
| `/jevguard status` | `jevguard.admin` | Mode, API health, counters |
| `/jevguard test <message>` | `jevguard.admin` | Classify a message and show the probabilities |
| `/jevguard simulate start [seconds]` | `jevguard.admin` | Send sample chat through Jev on a loop and show each verdict to you: green approved, yellow review, red blocked. Nothing reaches real chat. Each message is a real API call. Edit `simulate-messages.txt` to use your own samples |
| `/jevguard simulate stop` | `jevguard.admin` | Stop the simulation and show a summary |
| `/jevguard reload` | `jevguard.admin` | Reload `config.yml` |
| | `jevguard.notify` | Receive staff alerts (blocked, flagged, and review messages) |
| | `jevguard.bypass` | Chat is not checked |

## How it works

```
AsyncChatEvent ─► batcher (≤ max-size msgs or max-wait-ms) ─► POST api.typesafe.ai/v1/systemone
                                                                 (or Cloudflare /ai/run, typesafe/jev)
                                                                 state: { messages: [...] }
                                                                 questions: { m0: Choice, m1: Choice, ... }
                ◄── Policy: top category p ≥ threshold → FLAG ◄── probabilities per category
                                         p ≥ review-threshold → REVIEW
```

`hold` mode blocks only Paper's async chat thread, and never for longer than `hold.timeout-ms`.

## Privacy

Message text is sent to TypeSafe for classification. With the `cloudflare` provider it also passes through Cloudflare, and AI Gateway may log it. Turn off gateway logging if you don't want that. Read their
[privacy policy](https://typesafe.ai/legal/privacy-policy) and
[DPA](https://typesafe.ai/legal/data-processing). Tell your players that chat is moderated by a third-party AI. This is required in many jurisdictions (e.g. GDPR).
Set `privacy.log-message-content: false` to keep flagged text out of your logs.

## Limitations

- Jev can be steered by adversarial text (e.g. a message that argues for its own classification). The prompt tells Jev to ignore such text, but test your setup.
- Very large batches put more unrelated text in front of the model and can lower accuracy. Keep `batching.max-size` modest.
- The plugin checks each message alone. It does not detect harassment spread over several messages.

## Development

```
core/    shared engine: Jev client (TypeSafe + Cloudflare), batching, policy, config,
         moderation flow, commands, simulator. No server API dependencies.
paper/   Paper/Folia adapter: chat event, permissions, MiniMessage rendering, /jevguard
hytale/  Hytale adapter: PlayerChatEvent (non-blocking), permissions, Message rendering, /jevguard
```

```sh
./gradlew build                                              # core + paper
./gradlew build -PhytaleServerJar=/path/to/HytaleServer.jar  # + hytale
```

The Hytale server API isn't published to a Maven repository, so the `hytale` module builds only when you
point it at a server jar, with `-PhytaleServerJar` or `HYTALE_SERVER_JAR`. Without one it is skipped.

Adding a platform means implementing four small interfaces from `core` (`Platform`, `ChatSender`,
`Audience`, plus wiring the chat event and `/jevguard` command) and bundling `config.yml`.

## Releasing

Push a version tag and the `release` workflow builds both jars and publishes a GitHub Release:

```sh
git tag v0.2.0 && git push origin v0.2.0      # v0.2.0-beta.1 → pre-release
```

You can also start it by hand from **Actions → release → Run workflow**.

The Hytale jar needs the repository secret `HYTALE_SOURCE_TOKEN`: a fine-grained token with
read-only **Contents** access to `HypixelStudios/hytale-shared-source`. CI uses it to build
`HytaleServer.jar`, compiles against it, and never publishes it. Without the secret, the release
ships the Paper jar only.

## License

MIT
