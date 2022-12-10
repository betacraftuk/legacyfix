package legacyfix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class SaveLevelURLConnection extends HttpURLConnection {

	ByteArrayOutputStream levelOutput = new ByteArrayOutputStream();
	Exception exception;
	
	public SaveLevelURLConnection(URL url) {
		super(url);
	}

	@Override
	public void connect() {
	}

	@Override
	public void disconnect() {
	}

	@Override
	public boolean usingProxy() {
		return false;
	}

	@SuppressWarnings("unused")
	@Override
	public InputStream getInputStream() throws IOException {
		try {
			String classicLocalSaves = ListLevelsURLConnection.classicLocalSaves;
			if(classicLocalSaves == null) {
				throw new IOException("Save directory is not set");
			}
			File levels = new File(classicLocalSaves);
			byte[] data = levelOutput.toByteArray();
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
			String username = in.readUTF();
			String sessionid = in.readUTF();
			String levelName = in.readUTF();
			byte levelId = in.readByte();
			int length = in.readInt();
			byte[] levelData = new byte[length];
			in.read(levelData);
			in.close();
			File level = new File(levels, "level" + levelId + ".dat");
			File levelNames = new File(levels, "levels.txt");
			int maxLevels = 5;
			String[] lvlNames = new String[maxLevels];
			for(int i = 0; i < maxLevels; i++) {
				lvlNames[i] = ListLevelsURLConnection.EMPTY_LEVEL;
			}
			if(levelNames.exists()) {
				FileInputStream levelNamesStream = new FileInputStream(levelNames);
				lvlNames = new String(LoadLevelURLConnection.readStream(levelNamesStream)).split(";");
				levelNamesStream.close();
			}
			lvlNames[levelId] = levelName;
			if(levelName.equals("---")) {
				level.delete();
				lvlNames[levelId] = ListLevelsURLConnection.EMPTY_LEVEL;
			}
			else {
				if(!levels.exists()) {
					levels.mkdirs();
				}
				OutputStream fileOutput = new FileOutputStream(level);		
				fileOutput.write(levelData);
				fileOutput.close();
			}
			FileOutputStream outputNames = new FileOutputStream(levelNames);
			String lvls = "";
			for(int i = 0; i < maxLevels; i++) {
				lvls += lvlNames[i] + ";";
			}
			outputNames.write(lvls.getBytes());
			outputNames.close();
		} catch (Exception e) {
			exception = e;
		}
		if(exception != null) {
			try {
				Thread.sleep(100L); // Needs some sleep because it won't display error message if it's too fast
			} catch (InterruptedException e) {
			}
			String err = "error\n" + exception.getMessage();
			exception.printStackTrace();
			return new ByteArrayInputStream(err.getBytes());
		}
		return new ByteArrayInputStream("ok".getBytes());
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		return levelOutput;
	}
}
