package legacyfix.request;

public class CustomResponse extends Response {

	public byte[] response;

	public CustomResponse(byte[] response) {
		this.response = response;
	}
}
