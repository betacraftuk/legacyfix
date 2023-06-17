package legacyfix.request;

public class Response {
	public int code = -1;
	public boolean err = false;
	public byte[] response = null;

	public void setErr() {
		this.err = true;
	}

	@Override
	public String toString() {
		if (this.response == null)
			return null;

		try {
			return new String(this.response, "UTF-8");
		} catch (Throwable t) {
			t.printStackTrace();
			return null;
		}
	}
}
