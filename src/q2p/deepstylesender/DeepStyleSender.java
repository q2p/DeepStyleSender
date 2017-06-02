package q2p.deepstylesender;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import javax.net.ssl.HttpsURLConnection;
import javax.swing.JFileChooser;

public final class DeepStyleSender implements Runnable {
	private static final String postURL = "https://dreamscopeapp.com/api/images/";
	private static final String stylesURL = "https://dreamscopeapp.com/api/deepstyle";
		
	public static final void main(final String[] args) throws Exception {
		Frame.init();
	}
	
	private static final String receive(final String method, final String url, final byte[] data) throws IOException {
		final HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
		
		final StringBuilder builder = new StringBuilder(64);
		
		try {
		connection.setRequestMethod(method);

		connection.setRequestProperty("Host", "dreamscopeapp.com");
		connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:50.0) Gecko/20100101 Firefox/50.0");
		if(data != null)
			connection.setRequestProperty("Content-Length", ""+data.length);
		connection.setRequestProperty("DNT", "1");
		connection.setRequestProperty("Connection", "keep-alive");
		
		if(data != null) {
			connection.setDoOutput(true);
			
			final OutputStream out = connection.getOutputStream();
			out.write(data);
			out.flush();
			out.close();
		}
		
		connection.connect();
		
		if(connection.getResponseCode() != 200)
			return null;
		
		final BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		String response;
		while((response = br.readLine()) != null)
			builder.append(response);
		} catch(final IOException e) {
			throw e;
		} finally {
			connection.disconnect();
		}
		
		return builder.toString();
	}
	
	private static final boolean writeReceivedBytes(final String url, final String uuid) {
		final File localFile = getSaveFile(url, uuid);
		if(localFile == null)
			return true;

		InputStream in = null;
		FileOutputStream out = null;
		
		try {
			final HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
			
			connection.setRequestMethod("GET");
	
			connection.setRequestProperty("Host", "dreamscopeapp.com");
			connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:50.0) Gecko/20100101 Firefox/50.0");
			connection.setRequestProperty("DNT", "1");
			connection.setRequestProperty("Connection", "keep-alive");
			
			connection.connect();
			
			in = connection.getInputStream();
			
			out = new FileOutputStream(localFile);
			
			int bytesRead;
			final byte[] buffer = new byte[256*4096];
			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
				out.flush();
			}
		} catch(final IOException e) {
			return false;
		} finally {
			try {
				out.close();
			} catch(final Exception e) {}
			try {
				in.close();
			} catch(final Exception e) {}
		}
		return true;
	}
	
	private static final String jsonStringProp(final String source, String prop) {
		prop = "\""+prop+"\"";
		int pos = 0;
		while(true) {
			int idx1 = source.indexOf(prop, pos);
			
			if(idx1 == -1)
				return null;
			
			idx1 += prop.length();
			
			int idx2 = source.indexOf(':', idx1);
			
			if(idx2 == -1)
				return null;
			
			if(source.substring(idx1, idx2).trim().length() != 0) {
				pos = idx1;
				continue;
			}
			
			idx1 = source.indexOf('"', idx2 + 1) + 1;
			
			if(idx1 == 0)
				return null;
			
			idx2 = source.indexOf('"', idx1);
			
			if(idx2 == -1)
				return null;
			
			return source.substring(idx1, idx2);
		}
	}

	private final String styleMime, styleData, sourceMime, sourceData;
	
	DeepStyleSender(final String styleMime, final String styleData, final String sourceMime, final String sourceData) {
		this.styleMime = styleMime;
		this.styleData = styleData;
		this.sourceMime = sourceMime;
		this.sourceData = sourceData;
		
		new Thread(this).start();
	}
	
	public final void run() {
		final String styleName = sendStyle();
		
		if(styleName == null) {
			Frame.error(false, "Failed to send style image.");
			return;
		}
		
		if(!sendSource(styleName))
			Frame.error(false, "Failed to send source image.");
	}

	private final boolean sendSource(final String style) {
		final byte[] body = postSourceBody(style);

		String rec = receiveAttempt("POST", postURL, body);
		
		if(rec == null)
			return false;
		
		final String uuid = postURL + jsonStringProp(rec, "uuid");
		
		final String filePath = jsonStringProp(rec, "img_final_cf_url");
		
		while(rec.contains("\"processing_status\": 0")) {
			try {
				Thread.sleep(1000);
			} catch(final InterruptedException e) {}
			
			rec = receiveAttempt("GET", uuid, null);
			
			if(rec == null)
				return false;
		}

		if(!writeReceivedBytes(filePath, uuid)) {
			Frame.error(false, "Failed to save final image.");
		}
		
		return true;
	}
	
	private static File getSaveFile(String path, final String uuid) throws IndexOutOfBoundsException {
		path = uuid+'_'+path.substring(path.lastIndexOf('/')+1);
		
		synchronized(Frame.fileChooser) {
			Frame.fileChooser.setDialogTitle("Select save destination");
			final String ap = Frame.fileChooser.getSelectedFile().getAbsolutePath();
			final int idx = ap.lastIndexOf('/')+1;
			Frame.fileChooser.setSelectedFile(new File(idx==0?path:(ap.substring(0, idx)+path)));
			if(JFileChooser.APPROVE_OPTION != Frame.fileChooser.showSaveDialog(null)) {
				return null;
			}
			return Frame.fileChooser.getSelectedFile();
		}
	}

	private final String receiveAttempt(final String method, final String url, final byte[] body) {
		for(int j = 8; j != 0; j--) {
			try {
				return receive(method, url, body);
			} catch(final IOException e) {}
		}
		return null;
	}
	
	private final byte[] postSourceBody(final String style) {
		// TODO: retain color
		final StringBuilder builder = new StringBuilder("{\"private\":false,\"retain_color\":\"0\",\"processing_status\":2,\"image_base64\":\"data:image/");
		builder.append(sourceMime);
		builder.append(";base64,");
		builder.append(sourceData);
		builder.append("\",\"filter\":\"");
		builder.append(style);
		builder.append("\"}");
		
		return builder.toString().getBytes(StandardCharsets.US_ASCII);
	}

	private final String sendStyle() {
		String rec = null;
		for(int i = 8; i != 0; i--) {
			final byte[] body = postStyleBody();
			
			for(int j = 8;; j--) {
				try {
					rec = receive("POST", stylesURL, body);
					break;
				} catch(final IOException e) {
					if(j == 0)
						return null;
				}
			}
			
			if(rec != null)
				return jsonStringProp(rec, "name");
		}
		return null;
	}

	private static final Random random = new Random();
	private static final String valid = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-";
	private static final int length = 48;
	private final byte[] postStyleBody() {
		StringBuilder builder = new StringBuilder(length);
		
		synchronized(random) {
			for(int i = valid.length(); i != 0; i--)
				builder.append(valid.charAt(random.nextInt(valid.length())));
		}
		
		final String name = builder.toString();
		
		builder = new StringBuilder("{\"name\":\"");
		builder.append(name);
		builder.append("\",\"safe_name\":\"");
		builder.append(name);
		builder.append("\",\"priority\":0,\"user_generated\":true,\"style_img_data_url\":\"data:image/");
		builder.append(styleMime);
		builder.append(";base64,");
		builder.append(styleData);
		builder.append("\",\"parameters\":{}}");
		
		return builder.toString().getBytes(StandardCharsets.US_ASCII);
	}
}