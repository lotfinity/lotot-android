/*
 * LoToTi AI uses one authenticated gateway. Provider credentials, model choice
 * and failover stay on the server; only a restricted gateway token is packaged.
 */
package com.fr3ts0n.ecu.gui.androbd;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LoToTiAiClient
{
    interface Callback
    {
        void onAttempt();
        void onDelta(String delta);
        void onSuccess(String text);
        void onError(String message);
    }

    interface ConnectionFactory { HttpURLConnection open(URL url) throws Exception; }
    interface Clock { long now(); }

    private static final String MODEL = "lototi-auto";
    private static final long STATUS_TTL_MS = 90000;
    private static final long CHECK_INTERVAL_MS = 30000;
    private static final class Gateway
    {
        final String apiKey;
        final String baseUrl;
        final String model = MODEL;
        Gateway(String baseUrl, String apiKey)
        {
            this.apiKey = safe(apiKey);
            this.baseUrl = safe(baseUrl).replaceAll("/+$", "");
        }
        boolean isConfigured()
        {
            try {
                URL url = new URL(baseUrl);
                return !apiKey.isEmpty() && "https".equals(url.getProtocol())
                        && !url.getHost().isEmpty() && url.getUserInfo() == null
                        && url.getQuery() == null && url.getRef() == null;
            } catch (Exception ignored) { return false; }
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService statusExecutor = Executors.newSingleThreadExecutor();
    private final Gateway gateway;
    private final ConnectionFactory connections;
    private final Clock clock;
    private boolean checking;
    private boolean connected;
    private boolean available;
    private long checkedAt;
    private String availability = "unknown";

    LoToTiAiClient()
    {
        this(BuildConfig.LOTOTI_GATEWAY_BASE_URL, BuildConfig.LOTOTI_GATEWAY_API_KEY,
                url -> (HttpURLConnection) url.openConnection(), System::currentTimeMillis);
    }

    LoToTiAiClient(String baseUrl, String apiKey, ConnectionFactory connections, Clock clock)
    {
        this.gateway = new Gateway(baseUrl, apiKey);
        this.connections = connections;
        this.clock = clock;
    }

    synchronized JSONObject getState()
    {
        JSONObject state = new JSONObject();
        boolean fresh = checkedAt > 0 && clock.now() - checkedAt >= 0
                && clock.now() - checkedAt < STATUS_TTL_MS;
        try
        {
            state.put("configured", gateway.isConfigured());
            state.put("connected", fresh && connected);
            state.put("available", fresh && available);
            state.put("availability", !gateway.isConfigured() ? "unconfigured"
                    : checking ? "checking" : fresh ? availability : "unknown");
            state.put("checked_at", checkedAt);
        }
        catch (Exception ignored) { }
        return state;
    }

    /** A bounded synthetic request checks this token, route and actual inference.
     * It sends no diagnostic, vehicle, conversation or personal data.
     * UI visibility controls polling; native throttling coalesces duplicate calls.
     */
    void checkAvailability(Runnable changed)
    {
        synchronized (this)
        {
            if (!gateway.isConfigured() || checking
                    || (checkedAt > 0 && clock.now() - checkedAt >= 0
                    && clock.now() - checkedAt < CHECK_INTERVAL_MS))
            {
                changed.run();
                return;
            }
            checking = true;
        }
        changed.run();
        final long probeStarted = clock.now();
        statusExecutor.execute(() -> {
            HttpURLConnection connection = null;
            boolean reached = false;
            boolean ready = false;
            try
            {
                connection = connections.open(new URL(gateway.baseUrl + "/chat/completions"));
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + gateway.apiKey);
                connection.setRequestProperty("User-Agent", "LoToTi-Android/" + BuildConfig.VERSION_NAME);
                JSONObject body = new JSONObject();
                body.put("model", MODEL);
                body.put("messages", new JSONArray().put(message("user", "Reply OK.")));
                body.put("max_tokens", 1);
                body.put("stream", false);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
                int code = connection.getResponseCode();
                reached = true;
                if (code >= 200 && code < 300)
                {
                    JSONObject reply = new JSONObject(read(connection.getInputStream()));
                    JSONArray choices = reply.optJSONArray("choices");
                    ready = !reply.has("error") && choices != null && choices.length() > 0
                            && choices.optJSONObject(0) != null
                            && choices.optJSONObject(0).optJSONObject("message") != null;
                }
            }
            catch (Exception ignored) { /* Raw gateway/provider errors never reach the UI. */ }
            finally
            {
                if (connection != null) connection.disconnect();
                synchronized (this)
                {
                    // A slower probe must not overwrite a newer chat result.
                    if (checkedAt <= probeStarted)
                    {
                        connected = reached;
                        available = ready;
                        availability = ready ? "available" : reached ? "unavailable" : "offline";
                        checkedAt = clock.now();
                    }
                    checking = false;
                }
                changed.run();
            }
        });
    }

    private synchronized void recordRequestResult(boolean success)
    {
        connected = success;
        available = success;
        availability = success ? "available" : "unavailable";
        checkedAt = clock.now();
    }

    void ask(String question, String diagnosticContext, String responseLanguage, Callback callback)
    {
        executor.execute(() -> {
            if (!gateway.isConfigured())
            {
                callback.onError("LoToTi AI is not configured in this build.");
                return;
            }
            try
            {
                callback.onAttempt();
                String text = requestStream(gateway, question, diagnosticContext, responseLanguage, callback);
                if (text == null || text.trim().isEmpty()) throw new IllegalStateException("Empty answer");
                recordRequestResult(true);
                callback.onSuccess(text.trim());
            }
            catch (Exception ignored)
            {
                recordRequestResult(false);
                callback.onError("LoToTi AI is temporarily unavailable. Please try again.");
            }
        });
    }

    private String requestStream(Gateway gateway, String question, String diagnosticContext,
                                 String responseLanguage, Callback callback) throws Exception
    {
        URL url = new URL(gateway.baseUrl + "/chat/completions");
        HttpURLConnection connection = connections.open(url);
        try
        {
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(120000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setRequestProperty("Authorization", "Bearer " + gateway.apiKey);
        connection.setRequestProperty("User-Agent", "LoToTi-Android/" + BuildConfig.VERSION_NAME);

        JSONObject body = new JSONObject();
        body.put("model", gateway.model);
        body.put("temperature", 0.15);
        body.put("stream", true);
        body.put("max_tokens", 4096);
        JSONArray messages = new JSONArray();
        messages.put(message("system", systemPrompt(responseLanguage)));
        messages.put(message("user", "DIAGNOSTIC CONTEXT:\n" + safe(diagnosticContext)
                + "\n\nUSER QUESTION:\n" + safe(question)));
        body.put("messages", messages);

        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(data.length);
        try (OutputStream out = connection.getOutputStream()) { out.write(data); }

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300)
        {
            // Consume the body without surfacing it; provider errors can contain request details.
            read(connection.getErrorStream());
            throw new IllegalStateException("HTTP " + code);
        }

        StringBuilder answer = new StringBuilder();
        StringBuilder nonSse = new StringBuilder();
        int streamEvents = 0;
        int reasoningChars = 0;
        int malformedEvents = 0;
        String finishReason = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (!trimmed.startsWith("data:"))
                {
                    nonSse.append(trimmed);
                    continue;
                }
                String payload = trimmed.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) continue;
                try
                {
                    JSONObject chunk = new JSONObject(payload);
                    streamEvents++;
                    JSONArray choices = chunk.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) continue;
                    JSONObject choice = choices.optJSONObject(0);
                    if (choice != null && !choice.isNull("finish_reason")) finishReason = choice.optString("finish_reason", finishReason);
                    JSONObject delta = choice == null ? null : choice.optJSONObject("delta");
                    if (delta != null)
                    {
                        String reasoning = extractContent(delta.opt("reasoning_content"));
                        if (reasoning == null || reasoning.isEmpty()) reasoning = extractContent(delta.opt("reasoning"));
                        if (reasoning != null) reasoningChars += reasoning.length();
                    }
                    String text = extractContent(delta == null ? null : delta.opt("content"));
                    if (text != null && !text.isEmpty())
                    {
                        String addition = appendStreamingChunk(answer, text);
                        if (!addition.isEmpty()) callback.onDelta(addition);
                    }
                }
                catch (Exception ignored)
                {
                    malformedEvents++;
                    // Ignore malformed keepalive/event chunks; final content is validated below.
                }
            }
        }

        if (answer.length() > 0) return answer.toString();

        // Compatibility fallback if an OpenAI-compatible server ignored stream=true.
        if (nonSse.length() > 0)
        {
            JSONObject root = new JSONObject(nonSse.toString());
            JSONArray choices = root.optJSONArray("choices");
            if (choices != null && choices.length() > 0)
            {
                JSONObject choice = choices.optJSONObject(0);
                JSONObject fullMessage = choice == null ? null : choice.optJSONObject("message");
                String text = extractContent(fullMessage == null ? null : fullMessage.opt("content"));
                if (text != null && !text.isEmpty())
                {
                    callback.onDelta(text);
                    return text;
                }
            }
        }
        return null;
        }
        finally { connection.disconnect(); }
    }

    /**
     * Some OpenAI-compatible bridges emit overlapping text windows instead of
     * strict token deltas (for example "P04" then "P0420" then "20 wa...").
     * Merge the longest meaningful suffix/prefix overlap so both the live UI
     * and final answer remain readable without disabling streaming.
     */
    private static String appendStreamingChunk(StringBuilder answer, String chunk)
    {
        if (chunk == null || chunk.isEmpty()) return "";
        if (answer.length() == 0)
        {
            answer.append(chunk);
            return chunk;
        }

        String current = answer.toString();
        if (chunk.startsWith(current))
        {
            String addition = chunk.substring(current.length());
            answer.append(addition);
            return addition;
        }

        int maxOverlap = Math.min(current.length(), chunk.length());
        int overlap = 0;
        for (int size = maxOverlap; size >= 2; size--)
        {
            if (current.regionMatches(current.length() - size, chunk, 0, size))
            {
                overlap = size;
                break;
            }
        }
        String addition = chunk.substring(overlap);
        answer.append(addition);
        return addition;
    }

    private static String systemPrompt(String responseLanguage)
    {
        String lang = safe(responseLanguage);
        if (lang.isEmpty()) lang = "en";
        String languageStyle = "";
        if ("ar-DZ".equalsIgnoreCase(lang) || "ar_DZ".equalsIgnoreCase(lang))
        {
            languageStyle = "\n\nALGERIAN DARIJA LANGUAGE PROFILE:\n"
                    + "- Reply in natural Algerian Darija, primarily Arabic script.\n"
                    + "- Keep the French automotive and garage terminology Algerian users naturally use (for example: capteur, calculateur, pression, injecteur, bobine, bougie, débitmètre, papillon, turbo, catalyseur, sonde lambda, batterie, alternateur, code défaut, scan, diagnostic).\n"
                    + "- Keep OBD/DTC codes, ECU names, acronyms, CAN identifiers, model names and measurements unchanged in Latin/LTR form (for example P0301, ELM327, 7E0 -> 7E8, 13.8 V, -12.5 %).\n"
                    + "- Prefer familiar Algerian phrasing over literal Modern Standard Arabic. Do not overdo slang; stay professional, concise and mechanically useful.\n"
                    + "- Do not translate a familiar French technical term into obscure formal Arabic merely for purity.\n";
        }
        return "Your public identity is LoToTi AI. Do not introduce backend provider or model names as your identity. You are an evidence-first automotive diagnostic copilot. Your job is to help the user understand what the available vehicle data actually supports, not to fill gaps with plausible-sounding guesses.\n\n"
                + "STRICT DIAGNOSTIC RULES:\n"
                + "1. Treat SESSION STATE and DATA SOURCE labels in the supplied context as authoritative. If there is no live OBD/ECU session and no Demo ECU stream, explicitly say that vehicle/engine health cannot be assessed from phone-only sensors.\n"
                + "2. Never convert missing information into a negative finding. If no DTC records were supplied, say 'no DTC records were supplied' — never 'there are no DTCs' unless a scan explicitly reports zero.\n"
                + "3. Phone accelerometer/GPS data is auxiliary. It cannot by itself confirm suspension condition, wheel slip, engine health, transmission health, emissions health, or absence of faults.\n"
                + "4. Never swap axes or invent sensor semantics. Use the exact supplied signal label, mnemonic, unit, and source.\n"
                + "5. Separate OBSERVED facts from INTERPRETATION and from NEXT CHECKS. Use calibrated language such as 'may', 'suggests', or 'cannot determine' when evidence is incomplete.\n"
                + "6. A local DTC database definition explains a code; it does not prove that code is present on the connected vehicle.\n"
                + "7. If evidence is insufficient, ask the user to connect to the ECU or run the relevant scan instead of inventing a diagnosis.\n"
                + "8. Flag safety-critical evidence clearly, but do not manufacture urgency.\n"
                + "9. If SESSION STATE is Demo, clearly identify simulated observations as simulated.\n"
                + "10. Reply in the app language code '" + lang + "' unless the user clearly writes in or requests another language.\n"
                + "11. Treat OBD Mode 09 VIN, calibration IDs and ECU names as raw vehicle-identity evidence. Do not invent make, model, year, engine, trim or market from a VIN/WMI when those decoded details are not explicitly supplied. If you make a VIN-derived inference, label it as an inference rather than ECU-reported fact.\n"
                + "12. LIVE describes the transport/session, not provenance. Never say data comes from a physical/real vehicle or physical ECU unless context explicitly states Source type: physical_ecu. If Source type is emulator or demo, clearly describe readings and DTCs as emulated/replayed/simulated while still analyzing them diagnostically.\n"
                + "13. Use concise Markdown with short headings and bullets. Avoid long generic disclaimers and avoid repeating the raw context unless useful."
                + languageStyle;
    }

    private static JSONObject message(String role, String content) throws Exception
    {
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static String extractContent(Object content)
    {
        if (content instanceof String) return (String) content;
        if (content instanceof JSONArray)
        {
            StringBuilder builder = new StringBuilder();
            JSONArray parts = (JSONArray) content;
            for (int i = 0; i < parts.length(); i++)
            {
                JSONObject part = parts.optJSONObject(i);
                if (part != null && part.has("text")) builder.append(part.optString("text"));
            }
            return builder.toString();
        }
        return null;
    }

    private static String read(InputStream stream) throws Exception
    {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private static String safe(String value)
    {
        return value == null ? "" : value.trim();
    }

    void shutdown()
    {
        executor.shutdownNow();
        statusExecutor.shutdownNow();
    }
}
