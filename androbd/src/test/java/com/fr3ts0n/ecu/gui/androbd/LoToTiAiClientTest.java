package com.fr3ts0n.ecu.gui.androbd;

import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class LoToTiAiClientTest
{
    static final class Reply extends HttpURLConnection
    {
        final int code;
        final String body;
        final ByteArrayOutputStream sent = new ByteArrayOutputStream();
        boolean closed;
        Reply(int code, String body) throws Exception {
            super(new URL("https://gateway.test/v1/chat/completions"));
            this.code = code; this.body = body;
        }
        @Override public int getResponseCode() { return code; }
        @Override public InputStream getInputStream() {
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }
        @Override public OutputStream getOutputStream() { return sent; }
        @Override public void disconnect() { closed = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }

    private void check(LoToTiAiClient client) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        client.checkAvailability(() -> {
            if (!"checking".equals(client.getState().optString("availability"))) done.countDown();
        });
        assertTrue("Availability callback did not finish", done.await(3, TimeUnit.SECONDS));
    }

    @Test public void successfulProbeIsAuthenticatedPrivateAndExpires() throws Exception {
        AtomicLong clock = new AtomicLong(100000);
        AtomicInteger calls = new AtomicInteger();
        Reply reply = new Reply(200, "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}");
        LoToTiAiClient client = new LoToTiAiClient("https://gateway.test/v1", "app-token", url -> {
            assertEquals("/v1/chat/completions", url.getPath());
            calls.incrementAndGet(); return reply;
        }, clock::get);
        try {
            assertFalse(client.getState().getBoolean("available"));
            check(client);
            JSONObject state = client.getState();
            assertTrue(state.getBoolean("available"));
            assertEquals("available", state.getString("availability"));
            assertFalse(state.has("model"));
            assertFalse(state.has("providers"));
            assertFalse(state.toString().contains("app-token"));
            JSONObject body = new JSONObject(reply.sent.toString("UTF-8"));
            assertEquals("lototi-auto", body.getString("model"));
            assertEquals(1, body.getInt("max_tokens"));
            assertEquals("Reply OK.", body.getJSONArray("messages").getJSONObject(0).getString("content"));
            assertEquals("Bearer app-token", reply.getRequestProperty("Authorization"));
            assertFalse(reply.getInstanceFollowRedirects());
            assertTrue(reply.closed);
            check(client);
            assertEquals("Recent checks must be cached", 1, calls.get());
            clock.addAndGet(90000);
            assertFalse(client.getState().getBoolean("available"));
            assertEquals("unknown", client.getState().getString("availability"));
        } finally { client.shutdown(); }
    }

    @Test public void authenticationFailureIsReachableButUnavailable() throws Exception {
        Reply reply = new Reply(401, "secret provider model and credential details");
        LoToTiAiClient client = new LoToTiAiClient("https://gateway.test/v1", "app-token", url -> reply, () -> 100000);
        try {
            check(client);
            assertTrue(client.getState().getBoolean("connected"));
            assertFalse(client.getState().getBoolean("available"));
            assertEquals("unavailable", client.getState().getString("availability"));
            assertFalse(client.getState().toString().contains("secret"));
            assertTrue(reply.closed);
        } finally { client.shutdown(); }
    }

    @Test public void unreachableAndInvalidRepliesNeverTurnGreen() throws Exception {
        LoToTiAiClient offline = new LoToTiAiClient("https://gateway.test/v1", "app-token",
                url -> { throw new SocketTimeoutException("private provider detail"); }, () -> 100000);
        try {
            check(offline);
            assertEquals("offline", offline.getState().getString("availability"));
            assertFalse(offline.getState().getBoolean("connected"));
        } finally { offline.shutdown(); }
        for (String invalid : new String[]{"{}", "not JSON", "{\"choices\":[]}", "{\"error\":\"internal model\"}"}) {
            Reply reply = new Reply(200, invalid);
            LoToTiAiClient client = new LoToTiAiClient("https://gateway.test/v1", "app-token", url -> reply, () -> 100000);
            try {
                check(client);
                assertFalse(client.getState().getBoolean("available"));
                assertEquals("unavailable", client.getState().getString("availability"));
                assertTrue(reply.closed);
            } finally { client.shutdown(); }
        }
    }

    @Test public void duplicateChecksShareOneRequest() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch opened = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        LoToTiAiClient client = new LoToTiAiClient("https://gateway.test/v1", "app-token", url -> {
            calls.incrementAndGet(); opened.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            return new Reply(200, "{\"choices\":[{\"message\":{}}]}");
        }, () -> 100000);
        try {
            client.checkAvailability(() -> {
                if (client.getState().optBoolean("available")) done.countDown();
            });
            assertTrue(opened.await(3, TimeUnit.SECONDS));
            client.checkAvailability(() -> { });
            assertEquals(1, calls.get());
            release.countDown();
            assertTrue(done.await(3, TimeUnit.SECONDS));
        } finally { release.countDown(); client.shutdown(); }
    }

    @Test public void insecureOrMissingConfigurationNeverConnects() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        for (String url : new String[]{"http://gateway.test/v1", "https://user:pass@gateway.test/v1", "invalid"}) {
            LoToTiAiClient client = new LoToTiAiClient(url, "app-token", u -> { calls.incrementAndGet(); return null; }, () -> 100000);
            try {
                check(client);
                assertFalse(client.getState().getBoolean("configured"));
                assertEquals("unconfigured", client.getState().getString("availability"));
            } finally { client.shutdown(); }
        }
        assertEquals(0, calls.get());
    }

    @Test public void chatUsesOnlyGatewayAliasAndSanitizesErrors() throws Exception {
        Reply reply = new Reply(200, "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\ndata: [DONE]\n");
        LoToTiAiClient client = new LoToTiAiClient("https://gateway.test/v1", "app-token", url -> reply, () -> 100000);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> answer = new AtomicReference<>();
        try {
            client.ask("Question", "Context", "en", new LoToTiAiClient.Callback() {
                @Override public void onAttempt() { }
                @Override public void onDelta(String delta) { }
                @Override public void onSuccess(String text) { answer.set(text); done.countDown(); }
                @Override public void onError(String text) { answer.set("ERROR"); done.countDown(); }
            });
            assertTrue(done.await(3, TimeUnit.SECONDS));
            assertEquals("Hello", answer.get());
            assertEquals("lototi-auto", new JSONObject(reply.sent.toString("UTF-8")).getString("model"));
            assertTrue(client.getState().getBoolean("available"));
            assertTrue(reply.closed);
        } finally { client.shutdown(); }
    }

    @Test public void chatFailureDoesNotLeakErrorsOrTryAnotherProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Reply reply = new Reply(503, "NVIDIA secret-token private-model-name");
        LoToTiAiClient client = new LoToTiAiClient("https://gateway.test/v1", "app-token", url -> {
            calls.incrementAndGet(); return reply;
        }, () -> 100000);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        try {
            client.ask("Question", "Context", "en", new LoToTiAiClient.Callback() {
                @Override public void onAttempt() { }
                @Override public void onDelta(String delta) { }
                @Override public void onSuccess(String text) { done.countDown(); }
                @Override public void onError(String text) { error.set(text); done.countDown(); }
            });
            assertTrue(done.await(3, TimeUnit.SECONDS));
            assertNotNull(error.get());
            assertFalse(error.get().contains("NVIDIA"));
            assertFalse(error.get().contains("secret-token"));
            assertFalse(error.get().contains("private-model-name"));
            assertEquals(1, calls.get());
            assertTrue(reply.closed);
        } finally { client.shutdown(); }
    }
}
