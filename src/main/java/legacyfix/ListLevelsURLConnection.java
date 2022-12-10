package legacyfix;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class ListLevelsURLConnection extends URLConnection {
	
	public static String classicLocalSaves = System.getProperty("lf.classicLocalSaves");
	public static final String EMPTY_LEVEL = "-";
	
	public ListLevelsURLConnection(URL url) {
		super(url);
	}

	@Override
	public void connect() throws IOException {}

	public InputStream getInputStream() throws IOException {
		String levels = "";
		for(int i = 0; i < 5; i++) {
			levels += EMPTY_LEVEL + ";";
		}
		if(classicLocalSaves == null) {
			return new ByteArrayInputStream(levels.getBytes());
		}
		File levelsFolder = new File(classicLocalSaves);
		File levelNames = new File(levelsFolder, "levels.txt");
		if(!levelNames.exists()) {
			return new ByteArrayInputStream(levels.getBytes());
		}
		return new FileInputStream(levelNames);
	}

}
