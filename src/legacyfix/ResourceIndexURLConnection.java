package legacyfix;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import legacyfix.util.AssetIndexUtils;

public class ResourceIndexURLConnection extends HttpURLConnection {
	private boolean xmlMode;
	private InputStream stream;

	public ResourceIndexURLConnection(URL u, boolean xmlMode) {
		super(u);
		this.xmlMode = xmlMode;
	}

	@Override
	public void disconnect() {}

	@Override
	public boolean usingProxy() {
		return false;
	}

	@Override
	public void connect() throws IOException {
		// TODO: change this to support custom sounds (AssetIndexUtils)
		File indexFile;
		if (xmlMode) {
			indexFile = new File(AssetIndexUtils.namePathToHashPath.get("index.xml"));
		} else {
			indexFile = new File(AssetIndexUtils.namePathToHashPath.get("index.txt"));
		}

		stream = new ByteArrayInputStream(AssetIndexUtils.fileToBytes(indexFile));
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return this.stream;
	}

	@Override
	public int getResponseCode() {
		return 200;
	}
}
