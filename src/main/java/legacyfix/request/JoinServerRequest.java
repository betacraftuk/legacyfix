package legacyfix.request;

import legacyfix.request.RequestUtil.ReturnType;
import legacyfix.request.Response.BlankResponse;

public class JoinServerRequest extends Request {

	public String accessToken;
	public String selectedProfile;
	public String serverId;

	public JoinServerRequest(String sessionid, String uuid, String serverId) {
		this.REQUEST_URL = "https://sessionserver.mojang.com/session/minecraft/join";
		this.PROPERTIES.put("Content-Type", "application/json");
		this.serverId = serverId;
		this.accessToken = sessionid;
		this.selectedProfile = uuid;
	}

	@Override
	public Response perform() {
		int response = Integer.parseInt(new String(RequestUtil.performRawPOSTRequest(this, ReturnType.RESPONSE_CODE)));

		if (response == 204) {
			return new BlankResponse();
		} else {
			return null;
		}
	}
}