package legacyfix.request;

import java.util.HashMap;
import java.util.Map;

public abstract class Request {

	public transient String REQUEST_URL;
	public transient byte[] POST_DATA;
	public transient Map<String, String> PROPERTIES = new HashMap<String, String>();

	public abstract Response perform();
}
