# LoToTi AI gateway build

The app presents one LoToTi AI identity. Inference uses the HTTPS gateway at
https://aigw.whatsynaptic.com/v1 and the stable lototi-auto alias. Provider
selection, upstream credentials and fallback models stay in LiteLLM. No native
NVIDIA/OpenCode fallback or provider/model metadata is sent to the web UI.

A restricted inference-only gateway token is loaded from litellm.apiKey in the
external file named by LOTOTI_AI_PROPERTIES (default:
/home/lofa/.config/lototi/ai.properties). This token is extractable from an APK,
so it must never be the LiteLLM master key or an upstream provider key. Unconfigured
CI builds report Not configured and never simulate an online connection.

## Availability

Opening the AI tab, returning to it, or pressing the status control requests a
bounded asynchronous check. While the tab is visible it refreshes once a minute.
Native checks coalesce duplicates and cache a result for 30 seconds. The badge
expires a result after 90 seconds. Configuration alone never makes it green.

The check is a one-token synthetic chat request to lototi-auto, using the same
gateway token and route as chat. It sends only "Reply OK." and no vehicle,
diagnostic, location or conversation data. A valid successful inference reply
means Connected. HTTP/auth/provider failures mean Unavailable; network failures
mean Offline. A transport or TLS failure cannot be mistaken for availability.
Gateway error bodies and provider names never appear in UI errors.

The check runs independently of streaming chat. Server choice/failover is
transparent to users. A failed check does not prevent a manual chat retry.
Checks have connect/read timeouts of 10/30 seconds. No permanent background
polling service is added.

## Build and validation

Build as the ordinary user, not root. Run the UI tests, translation check,
testVerificationUnitTest, lintDebug, assembleDebug and assembleAiPreview.

The debug build retains com.lotot.android and needs the original signing key to
update an installed production app. The aiPreview build uses
com.lotot.android.ai.preview and is a usable side-by-side build for device QA when
that key is unavailable. Never uninstall the original app to bypass signing
incompatibility.
