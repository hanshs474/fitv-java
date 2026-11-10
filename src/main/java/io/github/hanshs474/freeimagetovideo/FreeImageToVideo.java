package io.github.hanshs474.freeimagetovideo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Official Java SDK for <a href="https://freeimagetovideo.ai">Free Image to Video</a>.
 * Free image to video AI with no daily cap: start with no sign-up, keep going on a free account.
 * Every video comes with sound and no watermark.
 *
 * <pre>{@code
 * FreeImageToVideo fitv = new FreeImageToVideo(); // reads FREEIMAGETOVIDEO_API_KEY
 * String clip = fitv.generateVideo("a paper lantern drifting over a night river");
 * String moved = fitv.generateVideo("the camera slowly orbits the statue",
 *         "https://example.com/statue.jpg", Map.of("duration", 6, "aspect_ratio", "9:16"));
 * int left = fitv.credits();
 * }</pre>
 *
 * <p>Create a key at https://freeimagetovideo.ai/settings/apikeys. API runs need a paid plan: each run
 * draws on the plan's full-speed allowance, then waits in the relaxed queue. Java 11+, no dependencies.
 */
public final class FreeImageToVideo {
    /** The service this library talks to. */
    public static final String BASE_URL = "https://freeimagetovideo.ai";
    /** Where an API key is created. */
    public static final String KEYS_URL = BASE_URL + "/settings/apikeys";
    /** The model the site runs (MiniMax H3). */
    public static final String DEFAULT_MODEL = "minimax-h3";
    /** Library version, sent in the User-Agent. */
    public static final String VERSION = "0.1.0";

    /** One run as {@code /api/ai/query} reports it. */
    public static final class Task {
        /** The run id. */
        public final String id;
        /** {@code pending}, {@code processing}, {@code success}, {@code failed} or {@code canceled}. */
        public final String status;
        /** Finished clip URLs once {@code success}. */
        public final List<String> videos;
        /** Why the run failed, when it did. */
        public final String errorMessage;
        /** The failure category, when the run failed. */
        public final String failureKind;
        /** True while the run waits in the relaxed queue. */
        public final boolean queued;
        /** Estimated wait in seconds while queued, else 0. */
        public final double queueEta;
        /** The whole {@code data} object as the service sent it. */
        public final Map<String, Object> raw;

        Task(Map<String, Object> d) {
            this.raw = Collections.unmodifiableMap(d);
            this.id = str(d.get("id"));
            this.status = str(d.get("status"));
            List<String> v = new ArrayList<>();
            if (d.get("videos") instanceof List) {
                for (Object o : (List<?>) d.get("videos")) if (o != null) v.add(o.toString());
            }
            this.videos = Collections.unmodifiableList(v);
            this.errorMessage = str(d.get("errorMessage"));
            this.failureKind = str(d.get("failureKind"));
            this.queued = Boolean.TRUE.equals(d.get("queued"));
            this.queueEta = d.get("queueEta") instanceof Number ? ((Number) d.get("queueEta")).doubleValue() : 0;
        }

        @Override
        public String toString() {
            return "Task{id=" + id + ", status=" + status + ", videos=" + videos + "}";
        }
    }

    /** A raw HTTP answer. */
    static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    /** Transport seam: POST a JSON body (null = empty), return status and raw body. */
    interface Transport {
        Response post(String url, String json, String key) throws IOException, InterruptedException;
    }

    private final String apiKey;
    private final String base;
    private final Transport transport;
    private Duration timeout = Duration.ofMinutes(30);
    private Duration pollEvery = Duration.ofSeconds(10);

    /** A client that reads the key from {@code FREEIMAGETOVIDEO_API_KEY}. */
    public FreeImageToVideo() {
        this(System.getenv("FREEIMAGETOVIDEO_API_KEY"));
    }

    /** A client for one API key. The host is {@code FREEIMAGETOVIDEO_BASE_URL} when set. */
    public FreeImageToVideo(String apiKey) {
        this(apiKey, envBase(), null);
    }

    FreeImageToVideo(String apiKey, String base, Transport transport) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.transport = transport != null ? transport : httpTransport();
    }

    private static String envBase() {
        String b = System.getenv("FREEIMAGETOVIDEO_BASE_URL");
        return b == null || b.trim().isEmpty() ? BASE_URL : b.trim();
    }

    /** How long {@code generateVideo} waits. Defaults to 30 minutes; runs can queue. */
    public FreeImageToVideo timeout(Duration v) {
        timeout = v;
        return this;
    }

    /** How often {@code generateVideo} asks for the status. Defaults to ten seconds. */
    public FreeImageToVideo pollEvery(Duration v) {
        pollEvery = v;
        return this;
    }

    private static Transport httpTransport() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        return (url, json, key) -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .header("User-Agent", "fitv-java/" + VERSION)
                    .POST(json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return new Response(res.statusCode(), res.body());
        };
    }

    /** Text to video: generate one clip with sound from a prompt and return its URL. */
    public String generateVideo(String prompt) {
        return generateVideo(prompt, null, null);
    }

    /**
     * Generate one clip with sound and return its URL.
     *
     * @param imageUrl a public https image to use as the first frame (image to video), or null for text to video
     * @param options e.g. {@code Map.of("duration", 6, "resolution", "768p", "aspect_ratio", "9:16")}; null for the defaults
     */
    public String generateVideo(String prompt, String imageUrl, Map<String, ?> options) {
        return waitFor(submit(prompt, imageUrl, options));
    }

    /** The request body for {@code POST /api/ai/generate}. */
    public static Map<String, Object> body(String prompt, String imageUrl, Map<String, ?> options) {
        Map<String, Object> opts = new LinkedHashMap<>();
        if (options != null) opts.putAll(options);
        opts.put("sound", true);
        String scene = "text-to-video";
        if (imageUrl != null && !imageUrl.isEmpty()) {
            opts.put("image_input", Collections.singletonList(imageUrl));
            scene = "image-to-video";
        }
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("provider", "auto");
        b.put("mediaType", "video");
        b.put("model", DEFAULT_MODEL);
        b.put("scene", scene);
        b.put("prompt", prompt);
        b.put("options", opts);
        return b;
    }

    /** Start a run without waiting and return its task id; follow it with {@link #status(String)} or {@link #waitFor(String)}. */
    public String submit(String prompt, String imageUrl, Map<String, ?> options) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new FreeImageToVideoException(FreeImageToVideoException.Kind.API, "prompt is required");
        }
        Object id = asMap(post("/api/ai/generate", body(prompt, imageUrl, options))).get("id");
        if (id == null || id.toString().isEmpty()) {
            throw new FreeImageToVideoException(FreeImageToVideoException.Kind.API, "freeimagetovideo.ai accepted the run but returned no id");
        }
        Object v = id;
        if (v instanceof Double && ((Double) v) == Math.rint((Double) v)) v = ((Double) v).longValue();
        return v.toString();
    }

    /** Ask where a run stands. */
    public Task status(String taskId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", taskId);
        Object d = post("/api/ai/query", body);
        if (!(d instanceof Map)) throw new FreeImageToVideoException(FreeImageToVideoException.Kind.API, "unexpected status answer");
        return new Task(asMap(d));
    }

    /** The credits left on the account. */
    public int credits() {
        Object left = asMap(post("/api/user/get-user-credits", null)).get("remainingCredits");
        return left instanceof Number ? ((Number) left).intValue() : 0;
    }

    /** Poll a submitted run until it finishes and return its video URL. */
    public String waitFor(String taskId) {
        Instant deadline = Instant.now().plus(timeout);
        while (true) {
            Task t = status(taskId);
            if ("success".equals(t.status)) {
                if (t.videos.isEmpty()) {
                    throw new FreeImageToVideoException(FreeImageToVideoException.Kind.GENERATION,
                            "run " + taskId + ": finished with no video", 0, 0, null, taskId, "finished with no video", null);
                }
                return t.videos.get(0);
            }
            if ("failed".equals(t.status) || "canceled".equals(t.status)) {
                String why = t.errorMessage == null || t.errorMessage.isEmpty() ? t.status : t.errorMessage;
                throw new FreeImageToVideoException(FreeImageToVideoException.Kind.GENERATION,
                        "generation failed: run " + taskId + ": " + why, 0, 0, null, taskId, t.errorMessage, t.failureKind);
            }
            if (Instant.now().plus(pollEvery).isAfter(deadline)) {
                throw new FreeImageToVideoException(FreeImageToVideoException.Kind.TIMEOUT,
                        "still generating; check run " + taskId + " later", 0, 0, null, taskId, null, null);
            }
            try {
                Thread.sleep(pollEvery.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FreeImageToVideoException(FreeImageToVideoException.Kind.API, "interrupted");
            }
        }
    }

    /**
     * Read one answer: the envelope's {@code data} on code 0, else throw the error that says what to do next.
     * Refusals usually arrive as HTTP 200 with a non-zero code, so the status alone reads a refusal as success.
     */
    static Object parseEnvelope(int status, String raw) {
        Map<String, Object> env = new HashMap<>();
        if (raw != null && !raw.trim().isEmpty()) {
            Object parsed;
            try {
                parsed = Json.parse(raw);
            } catch (RuntimeException e) {
                if (status == 401) throw classify(401, new HashMap<>());
                throw new FreeImageToVideoException(FreeImageToVideoException.Kind.API,
                        "freeimagetovideo.ai returned a non-JSON response (HTTP " + status + ")", status, 0, null, null, null, null);
            }
            env = asMap(parsed);
        }
        Object code = env.get("code");
        if (status < 400 && code instanceof Number && ((Number) code).intValue() == 0) return env.get("data");
        throw classify(status, env);
    }

    /** Map an HTTP status and envelope to the error that says what to do next. */
    static FreeImageToVideoException classify(int status, Map<String, Object> env) {
        String msg = str(env.get("message"));
        if (msg == null || msg.isEmpty()) msg = "request failed (HTTP " + status + ")";
        int code = env.get("code") instanceof Number ? ((Number) env.get("code")).intValue() : 0;
        String reason = str(asMap(env.get("data")).get("reason"));
        FreeImageToVideoException.Kind kind;
        if (status == 401) kind = FreeImageToVideoException.Kind.AUTH;
        else if (code == -2 || code == -3) kind = FreeImageToVideoException.Kind.REJECTED;
        else if ("insufficient_credits".equals(reason)) kind = FreeImageToVideoException.Kind.QUOTA;
        else if ("plan_required".equals(reason)) kind = FreeImageToVideoException.Kind.PLAN;
        else if ("concurrency".equals(reason)) kind = FreeImageToVideoException.Kind.BUSY;
        else if (msg.toLowerCase().contains("no auth")) kind = FreeImageToVideoException.Kind.AUTH;
        else kind = FreeImageToVideoException.Kind.API;
        return new FreeImageToVideoException(kind, msg, status, code, reason, null, null, null);
    }

    private Object post(String path, Map<String, Object> body) {
        if (apiKey.isEmpty()) {
            throw new FreeImageToVideoException(FreeImageToVideoException.Kind.AUTH,
                    "set FREEIMAGETOVIDEO_API_KEY (create a key at " + KEYS_URL + ")");
        }
        Response r;
        try {
            r = transport.post(base + path, body == null ? null : Json.write(body), apiKey);
        } catch (IOException e) {
            throw new FreeImageToVideoException(FreeImageToVideoException.Kind.API, "could not reach freeimagetovideo.ai: " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FreeImageToVideoException(FreeImageToVideoException.Kind.API, "interrupted");
        }
        return parseEnvelope(r.status, r.body);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new HashMap<>();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
