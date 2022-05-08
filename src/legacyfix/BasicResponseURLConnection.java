package legacyfix;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class BasicResponseURLConnection extends HttpURLConnection {
	final int responseCode;
	final String responseMessage;

	public BasicResponseURLConnection(URL url, int responseCode, String response) {
		super(url);
		this.responseCode = responseCode;
		this.responseMessage = response;
	}

	@Override
	public void connect() throws IOException {}

	@Override
	public InputStream getInputStream() throws IOException {
		return new ByteArrayInputStream(responseMessage.getBytes());
	}

	@Override
	public void disconnect() {}

	@Override
	public boolean usingProxy() {
		return false;
	}

	@Override
	public int getResponseCode() {
		return responseCode;
	}

	@Override
	public String getResponseMessage() {
		return responseMessage;
	}
}