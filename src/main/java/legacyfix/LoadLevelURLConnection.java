package legacyfix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class LoadLevelURLConnection extends HttpURLConnection {
	Exception exception;

	public LoadLevelURLConnection(URL url) {
		super(url);
	}

	@Override
	public void connect() throws IOException {}

	@Override
	public void disconnect() {
	}

	@Override
	public boolean usingProxy() {
		return false;
	}

	@SuppressWarnings("unused")
	@Override
	public InputStream getInputStream() throws IOException {
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		DataOutputStream output = new DataOutputStream(data);
		try {
			String classicLocalSaves = ListLevelsURLConnection.classicLocalSaves;
			if(classicLocalSaves == null) {
				throw new IOException("Save directory is not set");
			}
			Map<String, String> query = queryMap(url);
			if(!query.containsKey("id")) {
				throw new MalformedURLException("Query is missing \"id\" parameter");
			}
			int levelId = Integer.parseInt(query.get("id"));
			File levels = new File(classicLocalSaves);
			File level = new File(levels, "level" + levelId + ".dat");
			if(!level.exists()) {
				throw new FileNotFoundException("Level doesn't exist");
			}
			DataInputStream in = new DataInputStream(new FileInputStream(level));
			byte[] levelData = readStream(in);
			output.writeUTF("ok");
			output.write(levelData);
			output.close();
		} catch (Exception e) {
			exception = e;
		}
		if(exception != null) {
			try {
				Thread.sleep(100L); // Needs some sleep because it won't display error message if it's too fast
			} catch (InterruptedException e) {
			}
			ByteArrayOutputStream errorData = new ByteArrayOutputStream();
			DataOutputStream errorOutput = new DataOutputStream(errorData);
			errorOutput.writeUTF("error");
			errorOutput.writeUTF(exception.getMessage());
			errorOutput.close();
			return new ByteArrayInputStream(errorData.toByteArray());
		}
		return new ByteArrayInputStream(data.toByteArray());
	}

    public static byte[] readStream(InputStream stream) throws IOException {
    	ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    	int nRead;
    	byte[] data = new byte[16384];

    	while ((nRead = stream.read(data, 0, data.length)) != -1) {
    		buffer.write(data, 0, nRead);
    	}

    	return buffer.toByteArray();
    }

	public static Map<String, String> queryMap(URL url) throws UnsupportedEncodingException {
		Map<String, String> queryMap = new HashMap<String, String>();
		String query = url.getQuery();
		if(query != null) {
			String[] pairs = query.split("&");
			for (String pair : pairs) {
				final int idx = pair.indexOf("=");
				final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
				final String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : null;
				queryMap.put(key, value);
			}
		}
		return queryMap;
	}
}
