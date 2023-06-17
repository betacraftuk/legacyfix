package legacyfix;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import legacyfix.request.JoinServerRequest;
import legacyfix.request.Response;

public class JoinServerURLConnection extends HttpURLConnection {
	public JoinServerURLConnection(URL url) {
		super(url);
	}

	@Override
	public void disconnect() {

	}

	@Override
	public boolean usingProxy() {
		return false;
	}

	private String response = "Bad login";

	@Override
	public void connect() throws IOException {

	}

	@Override
	public InputStream getInputStream() throws IOException {
		String serverId = this.url.toString().substring(this.url.toString().indexOf("&serverId=") + 10);
		String sessionId = this.url.toString().substring(this.url.toString().indexOf("&sessionId=") + 11, this.url.toString().indexOf("&serverId="));

		if (sessionId.startsWith("token:")) {
			sessionId = sessionId.split(":")[1];
		}

		Response r = new JoinServerRequest(
				sessionId,
				System.getProperty("minecraft.user.uuid"),
				serverId
				).perform();

		if (!r.err) {
			response = "ok";
		}

		return new ByteArrayInputStream(response.getBytes());
	}

	@Override
	public int getResponseCode() {
		return 200;
	}

	@Override
	public String getResponseMessage() {
		return "ok";
	}
}