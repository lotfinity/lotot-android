package com.fr3ts0n.ecu.gui.androbd;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Opt-in network smoke test; never sends real vehicle data or device identity. */
@RunWith(AndroidJUnit4.class)
public class LoToTiLiveAiSmokeTest {
    @Test public void liveSyntheticPromptReceivesHumanReadableContent() throws Exception {
        Assume.assumeTrue("Run only when explicitly enabled", "true".equals(
                InstrumentationRegistry.getArguments().getString("liveGateway")));
        LoToTiAiClient ai = new LoToTiAiClient();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> answer = new AtomicReference<>("");
        AtomicReference<String> error = new AtomicReference<>("");
        try {
            assertTrue("Gateway configuration missing", ai.getState().optBoolean("configured"));
            ai.ask("Reply with the single word OK. This is a synthetic connectivity test.",
                    "SESSION STATE: No OBD connected. NO VEHICLE DATA. TEST ONLY.", "en",
                    new LoToTiAiClient.Callback() {
                        @Override public void onAttempt() { }
                        @Override public void onDelta(String delta) { }
                        @Override public void onSuccess(String text) { answer.set(text);done.countDown(); }
                        @Override public void onError(String message) { error.set(message);done.countDown(); }
                    });
            assertTrue("AI response timed out after 180 seconds", done.await(180, TimeUnit.SECONDS));
            assertTrue("AI error (sanitized): " + error.get() + " state=" + ai.getState().optString("availability"), error.get().isEmpty());
            assertFalse("AI returned no text", answer.get().trim().isEmpty());
            assertTrue(ai.getState().optBoolean("available"));
        } finally { ai.shutdown(); }
    }
}
