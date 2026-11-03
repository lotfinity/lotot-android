# LoToTi AI / LiteLLM latency investigation — 3 Nov 2026

## Production configuration (observed, not modified)

- Debian `homeserver-litellm-yvutml-litellm-1`, LiteLLM 1.106.2, gateway `https://aigw.whatsynaptic.com/v1`.
- `lototi-auto` deployments use **order-based selection** in the same model group:
  - `order: 1` → `chatgpt/gpt-6-sol` (LiteLLM deployment `lototi-auto-primary-v1`).
  - `order: 2` → `stepfun-ai/step-5-preview` (NVIDIA NIM, deployment `lototi-auto-fallback-v1`).
- Per-deployment `timeout: 55.0`, `num_retries: 1`; global router uses `simple-shuffle`, with `stream_timeout` not configured. The runtime produced requests lasting longer than 55 seconds: treat it as **not a validated hard total latency limit**. `fallbacks: null` means no separately configured cross-group fallbacks; `order` manages priority **within** `lototi-auto`.
- Source: authenticated LiteLLM `/v2/model/info`, `/router/settings` and `/spend/logs/ui`. Official order docs: https://docs.litellm.ai/docs/routing .

## Measured traffic

17 `lototi-auto` production logs inspected during the original performance investigation:

| Deployment | Requests | Median total duration | Longest | Important qualification |
|---|---:|---:|---:|---|
| GPT-6 Sol | 11 | 1.84 seconds | 2.16 seconds | Mostly tiny Android availability probes, ~6 output tokens |
| Step-5 Preview | 6 | 55.16 seconds | 125.88 seconds | Includes real diagnostic-sized responses |

- Prior Note20 real-gateway smoke test: **83.066 sec** end-to-end. The corresponding Step-5 LiteLLM request lasted **81.564 sec**, with **544 output tokens** and a first completion event roughly six seconds after start.
- GPT-6 small-context stream without explicit temperature: primary deployment, **1.79 sec** total, first text ~1.29 sec.
- Same small context with `temperature: 0.15`: **no response headers within 30 sec** on a bounded probe. This indicates a compatibility/routing issue but does **not**, on its own, prove which upstream error triggered promotion to the fallback. The model metadata lists temperature as supported, so provider behavior requires investigation if the parameter is reintroduced.
- Synthetic Peugeot replay context (~2K tokens) without temperature: GPT-6 primary, **3.65 sec** total, first text ~1.4 sec.

## Minimal Android fix and validation

- Removed explicit `temperature: 0.15` from both the SSE and non-SSE fallback requests in `LoToTiAiClient.java`. Sampling defaults are now left to whichever provider is selected by LiteLLM.
- Kept `lototi-auto`, gateway URL, authentication, streaming parser and fallback chain unchanged.
- Added assertions to `LoToTiAiClientTest` checking that **neither** payload sends `temperature`.
- Rebuilt and installed `aiPreview` on Note20; the same real-gateway Android instrumentation smoke test passed in **4.196 sec** instead of 83.066 sec. LiteLLM logged this request under **GPT-6 Sol** at **3.474 sec**, with 2,190 input and 47 output tokens, `status=success`.
- This proves the client works for the synthetic smoke test, not that every real diagnostic request always completes in four seconds.

## Follow-up issues (not changed yet)

1. Step-5 remains a high-latency fallback; validate a faster, reliable backup with realistic Algerian-Darija/automotive prompts before switching production routing.
2. Set and verify an **end-to-end** completion deadline, particularly for streaming, instead of relying on a per-deployment timeout that did not cap the observed 126-second request.
3. The Android client requests up to 4,096 output tokens, unnecessarily high for short mobile responses; performance-test a smaller default budget before rollout.
4. Add structured, non-sensitive telemetry for chosen deployment, time to first visible text, completion duration, and sanitized error class, without capturing user chat or vehicle identity.
5. An earlier screenshot showed a disconnected status. The model-latency finding does **not** independently prove the cause of that specific transient failure; investigate Cloudflare/mobile connectivity separately if it recurs.
