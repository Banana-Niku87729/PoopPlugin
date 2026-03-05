package com.example.poopplugin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Ko-Fi の Webhook を受け取るための組み込み HTTP サーバー。
 *
 * Ko-Fi の Webhook 設定画面で以下のように設定してください:
 *   URL: http://<サーバーIP>:<port>/kofi
 *
 * ポートは config.yml の kofi-webhook-port で設定（デフォルト: 8765）。
 *
 * Ko-Fi は POST リクエストで data=<URLエンコードされたJSON> を送信します。
 */
public class KofiWebhookListener {

    private final PoopPlugin plugin;
    private final Logger logger;
    private HttpServer httpServer;

    public KofiWebhookListener(PoopPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /** HTTP サーバーを起動する */
    public void start() {
        int port = plugin.getConfig().getInt("kofi-webhook-port", 8765);
        String secret = plugin.getConfig().getString("kofi-webhook-secret", "");

        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext("/kofi", new KofiHandler(secret));
            httpServer.setExecutor(null); // デフォルトエグゼキュータ
            httpServer.start();
            logger.info("[KofiWebhook] HTTPサーバー起動: ポート " + port);
        } catch (IOException e) {
            logger.severe("[KofiWebhook] HTTPサーバーの起動に失敗しました: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** HTTP サーバーを停止する */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            logger.info("[KofiWebhook] HTTPサーバーを停止しました。");
        }
    }

    // ─────────────────────────────────────────────
    //  Webhook ハンドラ
    // ─────────────────────────────────────────────

    private class KofiHandler implements HttpHandler {

        private final String secret;

        KofiHandler(String secret) {
            this.secret = secret;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            // リクエストボディを読み取る
            String body;
            try (InputStream is = exchange.getRequestBody();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                body = sb.toString();
            }

            // Ko-Fi は data=<URLエンコードJSON> 形式で送信
            String jsonPayload = null;
            if (body.startsWith("data=")) {
                String encoded = body.substring(5);
                jsonPayload = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            } else {
                // 直接 JSON の場合もある
                jsonPayload = body;
            }

            // Webhook シークレット検証（設定されている場合）
            if (!secret.isEmpty()) {
                String receivedToken = RankManager.extractJsonString(jsonPayload, "verification_token");
                if (!secret.equals(receivedToken)) {
                    logger.warning("[KofiWebhook] 不正なWebhookトークンを受信しました。");
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }
            }

            logger.info("[KofiWebhook] Webhook受信: " + jsonPayload);

            // RankManager に処理を委譲
            plugin.getRankManager().processWebhookPayload(jsonPayload);

            sendResponse(exchange, 200, "OK");
        }

        private void sendResponse(HttpExchange exchange, int code, String message) throws IOException {
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}