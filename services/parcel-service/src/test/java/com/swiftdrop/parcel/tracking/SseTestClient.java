package com.swiftdrop.parcel.tracking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Minimal EventSource for tests: reads SSE frames from a real HTTP connection on a background thread. */
final class SseTestClient implements AutoCloseable {

    /** One parsed frame. {@code comment} is set for ":..." lines; {@code end} marks the end of the stream. */
    record Frame(String id, String event, String data, String retry, String comment, boolean end) {
        static final Frame END = new Frame(null, null, null, null, null, true);
    }

    final int status;
    final String contentType;
    final String cacheControl;
    final String accelBuffering;
    private final InputStream body;
    private final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
    private final Thread reader;

    private SseTestClient(HttpResponse<InputStream> response) {
        this.status = response.statusCode();
        this.contentType = response.headers().firstValue("Content-Type").orElse("");
        this.cacheControl = response.headers().firstValue("Cache-Control").orElse("");
        this.accelBuffering = response.headers().firstValue("X-Accel-Buffering").orElse("");
        this.body = response.body();
        this.reader = new Thread(this::read, "sse-test-client");
        this.reader.setDaemon(true);
        this.reader.start();
    }

    static SseTestClient connect(String url, String lastEventId) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(30))
                .GET();
        if (lastEventId != null) {
            request.header("Last-Event-ID", lastEventId);
        }
        HttpResponse<InputStream> response = HttpClient.newHttpClient()
                .send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        return new SseTestClient(response);
    }

    /** Next status event (comments and retry frames are skipped), or null on timeout or end of stream. */
    Frame nextEvent(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            Frame frame = frames.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (frame == null || frame.end()) {
                return null;
            }
            if (frame.event() != null) {
                return frame;
            }
        }
    }

    /** Next frame of any kind, or null on timeout. */
    Frame nextFrame(Duration timeout) throws InterruptedException {
        return frames.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Waits for the server to end the stream. */
    boolean awaitEnd(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Frame frame = frames.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (frame != null && frame.end()) {
                return true;
            }
        }
        return false;
    }

    String readAll() throws IOException {
        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }

    private void read() {
        if (status != 200) {
            return;
        }
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String id = null;
            String event = null;
            String retry = null;
            StringBuilder data = null;
            String line;
            while ((line = lines.readLine()) != null) {
                if (line.isEmpty()) {
                    if (id != null || event != null || data != null || retry != null) {
                        frames.add(new Frame(id, event, data == null ? null : data.toString(), retry, null, false));
                    }
                    id = event = retry = null;
                    data = null;
                } else if (line.startsWith(":")) {
                    frames.add(new Frame(null, null, null, null, line.substring(1), false));
                } else if (line.startsWith("id:")) {
                    id = line.substring(3).trim();
                } else if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("retry:")) {
                    retry = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    data = (data == null ? new StringBuilder() : data.append('\n')).append(line.substring(5));
                }
            }
        } catch (IOException ignored) {
            // closed by the test
        }
        frames.add(Frame.END);
    }

    @Override
    public void close() throws IOException {
        body.close();
    }
}
