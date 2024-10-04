package uk.betacraft.util;

import uk.betacraft.legacyfix.LFLogger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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
            URL url = new URL(req.REQUEST_URL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            for (String key : req.PROPERTIES.keySet()) {
                con.addRequestProperty(key, req.PROPERTIES.get(key));
            }
            con.setRequestMethod("POST");
            con.setReadTimeout(15000);
            con.setConnectTimeout(15000);
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setUseCaches(false);

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
            URL url = new URL(req.REQUEST_URL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            con.setRequestMethod("GET");
            con.setReadTimeout(15000);
            con.setConnectTimeout(15000);
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setUseCaches(false);
            // i'm a browser C:
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