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
	public void connect() throws IOException {}

	@Override
	public InputStream getInputStream() throws IOException {
		AssetIndexUtils.asURIs();
		// TODO: change this to support custom sounds (AssetIndexUtils)
		File indexFile;
		if (xmlMode) {
			indexFile = new File(System.getProperty("xmlLocation"));
		} else {
			indexFile = new File(System.getProperty("txtLocation"));
		}

		stream = new ByteArrayInputStream(AssetIndexUtils.fileToBytes(indexFile));
		
		return this.stream;
	}

	@Override
	public int getResponseCode() {
		return 200;
	}
}
