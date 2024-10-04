package uk.betacraft.util;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class Request {
    public String REQUEST_URL;
    public String POST_DATA;
    public Map<String, String> PROPERTIES = new HashMap<String, String>();

    public Request setUrl(String url) {
        this.REQUEST_URL = url;
        return this;
    }

    public Request setPayload(JSONObject object) {
        this.POST_DATA = object.toString();
        return this;
    }

    public Request setHeader(String key, String value) {
        this.PROPERTIES.put(key, value);
        return this;
    }
}
