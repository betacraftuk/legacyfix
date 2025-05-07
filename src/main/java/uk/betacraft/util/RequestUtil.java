package uk.betacraft.util;

import org.json.JSONObject;
import uk.betacraft.legacyfix.LFLogger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLStreamHandler;

public class RequestUtil {

    public static String webDataToString(WebData data) {
        if (data.getData() != null) {
            try {
                return new String(data.getData(), "UTF-8");
            } catch (Throwable t) {
                LFLogger.error("webDataToString", t);
            }
        }
        return null;
    }

    public static String performPOSTRequest(Request req) {
        WebData data = performRawPOSTRequest(req);
        return webDataToString(data);
    }

    public static WebData performRawPOSTRequest(Request req) {
        try {
            URL url = RequestUtil.createDirectURL(req.REQUEST_URL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            con.setRequestMethod("POST");
            con.setReadTimeout(15000);
            con.setConnectTimeout(15000);
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setUseCaches(false);

            for (String key : req.PROPERTIES.keySet()) {
                con.addRequestProperty(key, req.PROPERTIES.get(key));
            }

            // Send POST
            DataOutputStream out = new DataOutputStream(con.getOutputStream());
            if (req.POST_DATA != null) {
                out.write(req.POST_DATA.getBytes("UTF-8"));
            }
            out.flush();
            out.close();

            // Read response
            int http = con.getResponseCode();
            byte[] data;

            if (http >= 400 && http < 600) {
                data = readInputStream(con.getErrorStream());
            } else {
                data = readInputStream(con.getInputStream());
            }

            return new WebData(data, http);
        } catch (javax.net.ssl.SSLHandshakeException e) {
            LFLogger.error("performRawPOSTRequest", e);
            return new WebData(null, -2);
        } catch (Throwable t) {
            LFLogger.error("performRawPOSTRequest", t);
            return new WebData(null, -1);
        }
    }

    public static String performGETRequest(Request req) {
        WebData data = performRawGETRequest(req);
        return webDataToString(data);
    }

    public static WebData performRawGETRequest(Request req) {
        try {
            URL url = RequestUtil.createDirectURL(req.REQUEST_URL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            con.setRequestMethod("GET");
            con.setReadTimeout(15000);
            con.setConnectTimeout(15000);
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setUseCaches(false);
            con.addRequestProperty("User-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36");

            for (String key : req.PROPERTIES.keySet()) {
                con.addRequestProperty(key, req.PROPERTIES.get(key));
            }

            // Read response
            int http = con.getResponseCode();
            byte[] data;

            if (http >= 400 && http < 600) {
                data = readInputStream(con.getErrorStream());
            } else {
                data = readInputStream(con.getInputStream());
            }

            return new WebData(data, http);
        } catch (javax.net.ssl.SSLHandshakeException e) {
            LFLogger.error("performRawGETRequest", e);
            return new WebData(null, -2);
        } catch (Throwable t) {
            LFLogger.error("performRawGETRequest", t);
            return new WebData(null, -1);
        }
    }

    public static boolean download(Request req, File destination) {
        try {
            URL url = RequestUtil.createDirectURL(req.REQUEST_URL);

            BufferedInputStream bin = new BufferedInputStream(url.openStream());
            FileOutputStream fos = new FileOutputStream(destination);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = bin.read(buffer, 0, 1024)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            bin.close();
            fos.close();

            return true;
        } catch (Throwable t) {
            LFLogger.error("download", t);
            return false;
        }
    }

    public static WebData performJoinServer(String uuid, String sessionId, String serverId) {
        String accessToken;
        if (sessionId.contains(":"))
            accessToken = sessionId.split(":")[1];
        else
            accessToken = sessionId;

        return RequestUtil.performRawPOSTRequest(
                new Request()
                        .setUrl("https://sessionserver.mojang.com/session/minecraft/join")
                        .setHeader("Content-Type", "application/json")
                        .setPayload(
                                new JSONObject()
                                        .put("serverId", serverId)
                                        .put("accessToken", accessToken)
                                        .put("selectedProfile", uuid)
                        )
        );
    }

    public static String getIPFromAmazon() {
        try {
            URL amazonUrl = RequestUtil.createDirectURL("http://checkip.amazonaws.com");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(amazonUrl.openStream()));
            return bufferedReader.readLine();
        } catch (Throwable t) {
            LFLogger.error("Failed to get IP from checkip.amazonaws.com, user is probably offline");
            LFLogger.error("getIPFromAmazon", t);
        }
        return null;
    }

    public static URL createDirectURL(String url) throws MalformedURLException {
        return new URL(null, url, getProtocolForURL(url));
    }

    public static URLStreamHandler getProtocolForURL(String url) {
        if (url.startsWith("https")) {
            return new sun.net.www.protocol.https.Handler();
        } else if (url.startsWith("http")) {
            return new sun.net.www.protocol.http.Handler();
        } else {
            return null;
        }
    }

    public static byte[] readInputStream(InputStream in) {
        try {
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int count;
            while ((count = in.read(buffer)) > 0) {
                baos.write(buffer, 0, count);
            }
            return baos.toByteArray();
        } catch (Throwable t) {
            LFLogger.error("readInputStream", t);
            return null;
        }
    }
}