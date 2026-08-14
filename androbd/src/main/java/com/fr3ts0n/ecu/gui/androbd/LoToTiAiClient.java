/*
 * LoToTi prototype AI client.
 *
 * Provider credentials are intentionally embedded at build time for the
 * prototype requested by the product owner. Do not log or expose them.
 */
package com.fr3ts0n.ecu.gui.androbd;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LoToTiAiClient
{
    interface Callback
    {
        void onAttempt(String provider, String model);
        void onDelta(String provider, String model, String delta);
        void onSuccess(String provider, String model, String text);
        void onError(String message);
    }

    private static final class Provider
    {
        final String id;
        final String label;
        final String apiKey;
        final String baseUrl;
        final String model;

        Provider(String id, String label, String apiKey, String baseUrl, String model)
        {
            this.id = id;
            this.label = label;
            this.apiKey = safe(apiKey);
            this.baseUrl = safe(baseUrl).replaceAll("/+$", "");
            this.model = safe(model);
        }

        boolean isConfigured()
        {
            return !apiKey.isEmpty() && baseUrl.startsWith("https://")
                    && model.matches("[A-Za-z0-9._:/-]+") && !model.isEmpty();
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Provider> providers = new ArrayList<>();

    LoToTiAiClient()
    {
        Provider openCode = new Provider("opencode", "DeepSeek via OpenCode Go",
                BuildConfig.LOTOTI_OPENCODE_API_KEY,
                BuildConfig.LOTOTI_OPENCODE_BASE_URL,
                BuildConfig.LOTOTI_OPENCODE_MODEL);
        Provider nvidia = new Provider("nvidia", "NVIDIA NIM",
                BuildConfig.LOTOTI_NVIDIA_API_KEY,
                BuildConfig.LOTOTI_NVIDIA_BASE_URL,
                BuildConfig.LOTOTI_NVIDIA_MODEL);
        Provider liteLlm = new Provider("litellm", "LoToTi LiteLLM · Codex",
                BuildConfig.LOTOTI_LITELLM_API_KEY,
                BuildConfig.LOTOTI_LITELLM_BASE_URL,
                BuildConfig.LOTOTI_LITELLM_MODEL);
        Provider liteLlmFallback = new Provider("litellm_fallback", "LoToTi LiteLLM · StepFun fallback",
                BuildConfig.LOTOTI_LITELLM_API_KEY,
                BuildConfig.LOTOTI_LITELLM_BASE_URL,
                BuildConfig.LOTOTI_LITELLM_FALLBACK_MODEL);

        String primary = safe(BuildConfig.LOTOTI_AI_PRIMARY).toLowerCase(Locale.US);
        if ("nvidia".equals(primary))
        {
            add(nvidia); add(openCode); add(liteLlm); add(liteLlmFallback);
        }
        else if ("litellm".equals(primary))
        {
            add(liteLlm); add(liteLlmFallback); add(openCode); add(nvidia);
        }
        else
        {
            add(openCode); add(nvidia); add(liteLlm); add(liteLlmFallback);
        }
    }

    private void add(Provider provider)
    {
        if (provider.isConfigured()) providers.add(provider);
    }

    JSONObject getState()
    {
        JSONObject state = new JSONObject();
        JSONArray available = new JSONArray();
        try
        {
            state.put("configured", !providers.isEmpty());
            if (!providers.isEmpty())
            {
                state.put("primary", providers.get(0).id);
                state.put("primary_label", providers.get(0).label);
                state.put("primary_model", providers.get(0).model);
                state.put("model", providers.get(0).model);
            }
            for (Provider provider : providers)
            {
                JSONObject item = new JSONObject();
                item.put("id", provider.id);
                item.put("label", provider.label);
                item.put("model", provider.model);
                available.put(item);
            }
            state.put("providers", available);
        }
        catch (Exception ignored) { }
        return state;
    }

    void ask(String question, String diagnosticContext, String responseLanguage, Callback callback)
    {
        executor.execute(() ->
        {
            if (providers.isEmpty())
            {
                callback.onError("No LoToTi AI provider is configured in this build.");
                return;
            }
            List<String> errors = new ArrayList<>();
            for (Provider provider : providers)
            {
                try
                {
                    callback.onAttempt(provider.label, provider.model);
                    if (BuildConfig.DEBUG) Log.d("LoToTiAI", "Trying " + provider.id + " model=" + provider.model);
                    String text = requestStream(provider, question, diagnosticContext, responseLanguage, callback);
                    if (text != null && !text.trim().isEmpty())
                    {
                        if (BuildConfig.DEBUG) Log.d("LoToTiAI", "Success " + provider.id + " model=" + provider.model);
                        callback.onSuccess(provider.label, provider.model, text.trim());
                        return;
                    }
                    errors.add(provider.label + ": empty response");
                }
                catch (Exception ex)
                {
                    String detail = ex.getMessage();
                    if (detail == null || detail.trim().isEmpty()) detail = ex.getClass().getSimpleName();
                    if (detail.length() > 80) detail = detail.substring(0, 80);
                    errors.add(provider.label + ": " + detail);
                    if (BuildConfig.DEBUG) Log.w("LoToTiAI", "Failed " + provider.id + " model=" + provider.model + " (" + detail + ")");
                }
            }
            callback.onError(errors.isEmpty() ? "AI request failed" : String.join(" · ", errors));
        });
    }

    private String requestStream(Provider provider, String question, String diagnosticContext,
                                 String responseLanguage, Callback callback) throws Exception
    {
        URL url = new URL(provider.baseUrl + "/chat/completions");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(120000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setRequestProperty("Authorization", "Bearer " + provider.apiKey);
        if ("nvidia".equals(provider.id)) connection.setRequestProperty("X-NVIDIA-Source", "lototi");

        JSONObject body = new JSONObject();
        body.put("model", provider.model);
        body.put("temperature", 0.15);
        body.put("stream", true);
        body.put("max_tokens", 900);
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
                    JSONArray choices = chunk.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) continue;
                    JSONObject choice = choices.optJSONObject(0);
                    JSONObject delta = choice == null ? null : choice.optJSONObject("delta");
                    String text = extractContent(delta == null ? null : delta.opt("content"));
                    if (text != null && !text.isEmpty())
                    {
                        answer.append(text);
                        callback.onDelta(provider.label, provider.model, text);
                    }
                }
                catch (Exception ignored)
                {
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
                    callback.onDelta(provider.label, provider.model, text);
                    return text;
                }
            }
        }
        return null;
    }

    private static String systemPrompt(String responseLanguage)
    {
        String lang = safe(responseLanguage);
        if (lang.isEmpty()) lang = "en";
        return "You are LoToTi AI, an evidence-first automotive diagnostic copilot. Your job is to help the user understand what the available vehicle data actually supports, not to fill gaps with plausible-sounding guesses.\n\n"
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
                + "11. Use concise Markdown with short headings and bullets. Avoid long generic disclaimers and avoid repeating the raw context unless useful.";
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
    }
}
