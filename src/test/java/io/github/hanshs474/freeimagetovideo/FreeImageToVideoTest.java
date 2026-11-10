package io.github.hanshs474.freeimagetovideo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Plain-main tests so the build needs no test framework. Response bodies are the service's real envelope shapes. */
public final class FreeImageToVideoTest {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    static final String OK_SUBMIT = "{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"t1\",\"status\":\"pending\"}}";

    static FreeImageToVideo client(List<String> urls, List<String> bodies, String... polls) {
        int[] i = {0};
        FreeImageToVideo.Transport t = (url, json, key) -> {
            check(key.equals("sk-test"), "bearer key");
            urls.add(url);
            bodies.add(json);
            if (url.endsWith("/api/ai/generate")) return new FreeImageToVideo.Response(200, OK_SUBMIT);
            if (url.endsWith("/api/user/get-user-credits"))
                return new FreeImageToVideo.Response(200, "{\"code\":0,\"message\":\"ok\",\"data\":{\"remainingCredits\":42}}");
            String p = polls[Math.min(i[0]++, polls.length - 1)];
            return new FreeImageToVideo.Response(200, "{\"code\":0,\"message\":\"ok\",\"data\":" + p + "}");
        };
        return new FreeImageToVideo("sk-test", "https://t", t).pollEvery(Duration.ZERO);
    }

    static FreeImageToVideoException.Kind kindOf(int status, String body) {
        try {
            FreeImageToVideo.parseEnvelope(status, body);
        } catch (FreeImageToVideoException e) {
            return e.kind();
        }
        throw new AssertionError("no error for " + body);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        List<String> urls = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        FreeImageToVideo c = client(urls, bodies, "{\"id\":\"t1\",\"status\":\"processing\"}",
                "{\"id\":\"t1\",\"status\":\"success\",\"videos\":[\"https://cdn/x.mp4\"]}");
        String url = c.generateVideo("a fox", null, Map.of("duration", 6));
        check(url.equals("https://cdn/x.mp4"), "video url");
        Map<String, Object> sent = (Map<String, Object>) Json.parse(bodies.get(0));
        check("minimax-h3".equals(sent.get("model")) && "text-to-video".equals(sent.get("scene"))
                && "auto".equals(sent.get("provider")) && "video".equals(sent.get("mediaType")), "generate body " + sent);
        Map<String, Object> opts = (Map<String, Object>) sent.get("options");
        check(Boolean.TRUE.equals(opts.get("sound")) && ((Number) opts.get("duration")).intValue() == 6, "options " + opts);
        check(bodies.get(1).equals("{\"taskId\":\"t1\"}"), "query body " + bodies.get(1));
        check(urls.get(1).equals("https://t/api/ai/query"), "query url");
        check(c.credits() == 42, "credits");
        check(bodies.get(bodies.size() - 1) == null, "credits body empty");

        bodies.clear();
        client(urls, bodies, "{\"id\":\"t1\",\"status\":\"success\",\"videos\":[\"https://cdn/y.mp4\"]}")
                .generateVideo("orbit", "https://e.com/a.jpg", null);
        sent = (Map<String, Object>) Json.parse(bodies.get(0));
        check("image-to-video".equals(sent.get("scene")), "i2v scene");
        check(((Map<String, Object>) sent.get("options")).get("image_input").toString().equals("[https://e.com/a.jpg]"), "image_input");

        try {
            client(urls, bodies, "{\"id\":\"t1\",\"status\":\"failed\",\"errorMessage\":\"provider down\",\"failureKind\":\"upstream\"}").generateVideo("x");
            throw new AssertionError("failed run returned");
        } catch (FreeImageToVideoException e) {
            check(e.kind() == FreeImageToVideoException.Kind.GENERATION && "provider down".equals(e.errorMessage())
                    && "upstream".equals(e.failureKind()) && "t1".equals(e.taskId()), "failed run " + e);
        }
        try {
            client(urls, bodies, "{\"id\":\"t1\",\"status\":\"canceled\"}").generateVideo("x");
            throw new AssertionError("canceled run returned");
        } catch (FreeImageToVideoException e) {
            check(e.kind() == FreeImageToVideoException.Kind.GENERATION, "canceled");
        }
        try {
            client(urls, bodies, "{\"id\":\"t1\",\"status\":\"processing\",\"queued\":true,\"queueEta\":60}").timeout(Duration.ZERO).generateVideo("x");
            throw new AssertionError("timeout returned");
        } catch (FreeImageToVideoException e) {
            check(e.kind() == FreeImageToVideoException.Kind.TIMEOUT && "t1".equals(e.taskId()), "timeout");
        }

        FreeImageToVideoException.Kind A = FreeImageToVideoException.Kind.AUTH;
        check(kindOf(401, "{\"code\":-1,\"message\":\"Invalid API key. Create one at https://freeimagetovideo.ai/settings/apikeys\",\"data\":{\"reason\":\"invalid_api_key\"}}") == A, "401");
        check(kindOf(401, "<html>unauthorized</html>") == A, "401 html");
        check(kindOf(200, "{\"code\":-1,\"message\":\"insufficient credits\",\"data\":{\"reason\":\"insufficient_credits\"}}") == FreeImageToVideoException.Kind.QUOTA, "quota");
        check(kindOf(200, "{\"code\":-1,\"message\":\"This model or quality needs a plan.\",\"data\":{\"reason\":\"plan_required\"}}") == FreeImageToVideoException.Kind.PLAN, "plan");
        check(kindOf(200, "{\"code\":-1,\"message\":\"busy\",\"data\":{\"reason\":\"concurrency\"}}") == FreeImageToVideoException.Kind.BUSY, "busy");
        check(kindOf(200, "{\"code\":-2,\"message\":\"blocked\"}") == FreeImageToVideoException.Kind.REJECTED, "-2");
        check(kindOf(200, "{\"code\":-3,\"message\":\"cooldown\"}") == FreeImageToVideoException.Kind.REJECTED, "-3");
        check(kindOf(200, "{\"code\":-1,\"message\":\"no auth, please sign in\"}") == A, "no auth");
        check(kindOf(200, "{\"code\":-1,\"message\":\"generation failed, please try again\"}") == FreeImageToVideoException.Kind.API, "api");
        check(kindOf(502, "<html>bad gateway</html>") == FreeImageToVideoException.Kind.API, "502");

        try {
            new FreeImageToVideo("", "https://t", null).credits();
            throw new AssertionError("no key accepted");
        } catch (FreeImageToVideoException e) {
            check(e.kind() == A, "missing key");
        }
        System.out.println("FreeImageToVideoTest: all checks passed");
    }
}
