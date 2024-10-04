package uk.betacraft.legacyfix.protocol.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public abstract class HandlerBase extends HttpURLConnection {
    protected InputStream stream;

    protected HandlerBase(URL u, Pattern patternUsed) {
        super(u);
    }

    public void disconnect() {}

    public boolean usingProxy() {
        return false;
    }

    public void connect() throws IOException {}

    public InputStream getInputStream() throws IOException {
        return this.stream;
    }

    public int getResponseCode() {
        return this.stream == null ? 404 : 200;
    }

    public String getURLString() {
        try {
            return this.url.toURI().toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public static List<Pattern> regexPatterns() {
        return Collections.emptyList();
    }
}
