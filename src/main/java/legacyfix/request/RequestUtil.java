package legacyfix.request;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class RequestUtil {
	private static boolean debug = false;

	public static Response performPOSTRequest(Request req) {
		HttpURLConnection con = null;
		Response r = new Response();
		try {
			URL url = new URL(req.REQUEST_URL);
			con = (HttpURLConnection) url.openConnection();

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
			if (req.POST_DATA == null) {
				Gson gson = new GsonBuilder().disableHtmlEscaping().create();
				String s = gson.toJson(req);
				if (debug) System.out.println("OUTGOING: " + s);
				out.write(s.getBytes("UTF-8"));
			} else {
				if (debug) System.out.println("OUTGOING: " + new String(req.POST_DATA, "UTF-8"));
				out.write(req.POST_DATA);
			}
			out.flush();
			out.close();
			
			r.code = con.getResponseCode();
			r.response = readInputStream(con.getInputStream());

			return r;
		} catch (Throwable t) {
			t.printStackTrace();

			r.setErr();
			if (con != null)
				r.response = readInputStream(con.getErrorStream());

			return r;
		}
	}

	public static Response performGETRequest(Request req) {
		HttpURLConnection con = null;
		Response r = new Response();
		try {
			if (debug) System.out.println("OUTCOME TO: " + req.REQUEST_URL);
			URL url = new URL(req.REQUEST_URL);
			con = (HttpURLConnection) url.openConnection();

			con.setRequestMethod("GET");
			con.setReadTimeout(15000);
			con.setConnectTimeout(15000);
			con.setDoInput(true);
			con.setDoOutput(true);
			con.setUseCaches(false);
			for (String key : req.PROPERTIES.keySet()) {
				con.addRequestProperty(key, req.PROPERTIES.get(key));
			}

			r.code = con.getResponseCode();
			r.response = readInputStream(con.getInputStream());

			return r;
		} catch (Throwable t) {
			t.printStackTrace();

			r.setErr();
			if (con != null)
				r.response = readInputStream(con.getErrorStream());

			return r;
		}
	}

	public static byte[] readInputStream(InputStream in) {
		try {
			byte[] buffer = new byte[4096];
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int count = in.available();
			while ((count = in.read(buffer)) > 0) {
				baos.write(buffer, 0, count);
			}
			byte[] data = baos.toByteArray();
			return data;
		} catch (Throwable t) {
			t.printStackTrace();
			return null;
		}
	}
}
