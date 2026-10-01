<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".github/assets/logo-dark.svg">
    <img src=".github/assets/logo.svg" alt="jev-guard" height="48">
  </picture>
</p>

<p align="center">
  AI chat moderation for Minecraft (Paper/Folia) and Hytale servers,<br>
  powered by <a href="https://typesafe.ai">TypeSafe</a>'s Jev model.
</p>

<p align="center">
  <a href="https://github.com/HiveScaleSystems/jev-guard/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/HiveScaleSystems/jev-guard?color=2f6bff"></a>
  <a href="https://github.com/HiveScaleSystems/jev-guard/actions/workflows/build.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/HiveScaleSystems/jev-guard/build.yml?branch=main"></a>
  <a href="LICENSE"><img alt="MIT license" src="https://img.shields.io/github/license/HiveScaleSystems/jev-guard"></a>
</p>

<p align="center">
  <a href="https://github.com/HiveScaleSystems/jev-guard/releases/latest"><b>Download</b></a> ·
  <a href="https://jev-guard.beehivesys.net">Website</a> ·
  <a href="#install">Install</a> ·
  <a href="#commands-and-permissions">Commands</a> ·
  <a href="#how-it-works">How it works</a>
</p>

---

Jev does not generate text. For each chat message it returns calibrated probabilities over the
categories you define (harassment, hate, grooming, scams, …). The plugin applies **your**
thresholds and actions in code, and batches busy chat into single API calls. A check takes about
200 ms.

When a message is blocked, staff see who sent it, what they wrote, and how sure Jev was:

```
[JevGuard] Blocked xXSniperXx: you're so stupid, nobody on this server likes you (harassment, 100% sure)
[JevGuard] Review DiamondDan: keep talking and I'll find where you live (harassment, 62%)
```

> Community project, not affiliated with or endorsed by TypeSafe AI. "Jev" and "TypeSafe" belong to their owners.

## Features

- **Three modes:** `shadow` (log only), `deliver` (act after the message appears), `hold` (the message waits for the verdict and can be blocked)
- **Per-category thresholds and actions:** `cancel`, `warn`, `notify-staff`, `kick`, or any console `command:`, so you can mute or ban through the punishment plugin you already use
- **Staff alerts:** when a message is blocked, everyone with `jevguard.notify` sees who sent it, the message, the category, and how sure Jev was
- **Review band:** borderline messages go to staff and no other action runs
- **Batching:** one API request carries up to 8 messages (configurable)
- **Resilience:** retries with backoff on 429/529, a circuit breaker, and a fail-open or fail-closed setting
- **Two providers:** TypeSafe's API directly, or Jev on Cloudflare through **AI Gateway** (logs, analytics, caching, rate limits)
- **Privacy:** only message text is sent. No player names or UUIDs.
- **Non-blocking:** Folia-safe scheduling on Paper, and a fully asynchronous chat hook on Hytale

## Requirements

- Minecraft: Paper (or Folia) 26.2+, Java 25
- Hytale: server 0.6.8+ (Java 25)
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
   If your host can't set environment variables, put the key in `api.typesafe.key` instead
   (Cloudflare: `api.cloudflare.account-id` and `api.cloudflare.api-token`).

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

## Default categories

Each category is one option in Jev's answer. A message is flagged when its top category reaches that
category's threshold. Between `review-threshold` (0.5) and the threshold, it goes to staff for review only.

| Category | Threshold | Actions |
|---|---|---|
| `harassment` | 0.8 | cancel, warn, notify-staff |
| `hate` | 0.75 | cancel, warn, notify-staff |
| `sexual` | 0.8 | cancel, warn, notify-staff |
| `grooming` | 0.6 | cancel, notify-staff |
| `self-harm` | 0.6 | notify-staff |
| `doxxing` | 0.7 | cancel, notify-staff |
| `scam` | 0.8 | cancel, warn, notify-staff |

`cancel` only has an effect in `hold` mode. Add, remove or reword categories in `config.yml`.

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
| `/jevguard simulate start [seconds]` | `jevguard.admin` | Run sample chat from `simulate-messages.txt` through Jev and show each verdict to you only. Each line is a real API call |
| `/jevguard simulate stop` | `jevguard.admin` | Stop the simulation and show a summary |
| `/jevguard reload` | `jevguard.admin` | Reload `config.yml` |
| | `jevguard.notify` | Receive staff alerts (blocked, flagged, and review messages) |
| | `jevguard.bypass` | Chat is not checked |

## How it works

```
chat event ─────► batcher (≤ max-size msgs or max-wait-ms) ─► POST api.typesafe.ai/v1/systemone
                                                                 (or Cloudflare /ai/run, typesafe/jev)
                                                                 state: { messages: [...] }
                                                                 questions: { m0: Choice, m1: Choice, ... }
                ◄── Policy: top category p ≥ threshold → FLAG ◄── probabilities per category
                                         p ≥ review-threshold → REVIEW
```

In `hold` mode a message waits for its verdict, never longer than `hold.timeout-ms`. On Paper that
wait happens on the async chat thread. On Hytale the verdict is chained into the event's future, so
no thread waits at all.

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
./gradlew build                                # core + paper + hytale, with unit tests
./gradlew build -PhytaleVersion=0.7.0-pre.4    # build against a Hytale pre-release
```

The Hytale server API comes from Hytale's Maven repository (`maven.hytale.com`). The default version is
`hytaleVersion` in `gradle.properties`.

Adding a platform means implementing four small interfaces from `core` (`Platform`, `ChatSender`,
`Audience`, plus wiring the chat event and `/jevguard` command) and bundling `config.yml`.

## Releasing

Push a version tag and the `release` workflow builds both jars and publishes a GitHub Release:

```sh
git tag v0.2.0 && git push origin v0.2.0      # v0.2.0-beta.1 → pre-release
```

You can also start it by hand from **Actions → release → Run workflow**.

## License

MIT
