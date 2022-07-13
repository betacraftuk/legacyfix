package legacyfix.request;

import java.util.Map;

import legacyfix.request.RequestUtil.ReturnType;

public class CustomRequest extends Request {
	private RequestType type;

	public CustomRequest(String url) {
		this(url, null, null, RequestType.GET);
	}

	public CustomRequest(String url, byte[] post_data, Map<String, String> properties, RequestType type) {
		this.type = type;
		this.REQUEST_URL = url;
		if (post_data != null) this.POST_DATA = post_data;
		if (properties != null) this.PROPERTIES = properties;
	}

	public CustomResponse perform() {
		byte[] response = null;
		if (this.type == RequestType.POST) {
			response = RequestUtil.performRawPOSTRequest(this, ReturnType.DATA);
		} else {
			response = RequestUtil.performRawGETRequest(this, ReturnType.DATA);
		}

		return new CustomResponse(response);
	}

	public enum RequestType {
		POST,
		GET;
	}
}
