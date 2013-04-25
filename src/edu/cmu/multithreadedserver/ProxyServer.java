/**
 * @file: ProxyServer.java
 * 
 * @author: Ramya Balaraman <rbalaram@andrew.cmu.edu> 
 * @author	Venkatesh Sriram<vsriram@andrew.cmu.edu> 
 * 
 * @date: Mar 19, 2013 4:10:36 PM EST
 * 
 */
package edu.cmu.multithreadedserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Scanner;

public class ProxyServer implements Runnable {
	private Socket proxySock;
	private String www_root;
	private Hashtable<String,String> httpDetails = new Hashtable<String,String>();
	private Hashtable<InetAddress,Integer> activeConnections = new Hashtable<InetAddress,Integer>();
	private BufferedReader inStream = null;
	private DataOutputStream outStream = null;
	private boolean hasErrors = false;

	public ProxyServer(Socket proxySock, String www_root, Hashtable<InetAddress,Integer> activeConnections) {
		this.proxySock = proxySock;
		this.www_root = www_root;
		this.activeConnections = activeConnections;
		System.out.println("ProxyServer " + Thread.currentThread().getName() + " accepted connection from " 
				+ proxySock.getInetAddress() + ":"
				+ proxySock.getPort());
	}

	@Override
	public void run() {
		try {
			inStream = new BufferedReader(new InputStreamReader(proxySock.getInputStream()));
			outStream = new DataOutputStream(proxySock.getOutputStream());

			parseRequest();
			checkErrors();
			if (hasErrors == false) {
				sendHeader(200, "OK");	
				if (httpDetails.get("Request Type").equals("GET")) {
					sendResponse();
				}
			}


			if (inStream != null) {
				inStream.close();
			}
			if (outStream != null) {
				outStream.close();
			}
			if (proxySock != null) {
				proxySock.close();
			}			
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				Thread.sleep(10000); // Purely for testing purposes;to check connection constraints
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			synchronized(activeConnections) {
				int no = activeConnections.get(proxySock.getInetAddress());
				if (no > 1) {
					activeConnections.put(proxySock.getInetAddress(),--no);
				} else if (no == 1) {
					activeConnections.remove(proxySock.getInetAddress());
				}
				System.out.println("Connection to " + proxySock.getInetAddress() + " closed. No of active connections to this client = " + activeConnections.get(proxySock.getInetAddress()));
			}
		}
	}
	/**
	 * Method to parse the HTTP Request received from the client. 
	 * A Hashtable is populated with information from the HTTP Request.
	 * Additional parameters such as Mime Type and Full path to the resource which are 
	 * not explicitly present in the HTTP Request are also determined and stored in the Hashtable.
	 * Handles malformed HTTP request by responding with 400 Bad Request
	 *
	 */
	public void parseRequest() {
		String buffer = null;
		try {
			int i = 1;
			while ((buffer = inStream.readLine()) != null) {
				if (buffer.isEmpty()) {
					break;
				}
				if ( i == 1) {
					String[] headerLine = buffer.split(" ");
					httpDetails.put("Request Type", headerLine[0]);
					httpDetails.put("Resource", headerLine[1]);
					httpDetails.put("Version", headerLine[2]);
					i++;
				} else {
					if (buffer.contains(":")) {
						String[] body = buffer.split(":");
						httpDetails.put(body[0], body[1]);
					}
				}
				System.out.println("Read from client "
						+ proxySock.getInetAddress() + ":"
						+ proxySock.getPort() + " " + buffer);	
			}

			httpDetails.put("Full path", www_root + "/www" +httpDetails.get("Resource"));
			if ((httpDetails.get("Resource").equals(null) || httpDetails.get("Resource").equals("/"))  && new File(www_root + "/www/index.html").exists()) {
				httpDetails.put("Full path", www_root + "/www/index.html");
			}
			String mimeType = GetMime.getMimeType(httpDetails.get("Full path"));

			if (mimeType != null) {
				httpDetails.put("Mime Type",mimeType);
			} else {
				if (httpDetails.get("Resource").endsWith(".css")) {
					httpDetails.put("Mime Type", "text/css");
				} 
			}
			System.out.println("HTTP Request Details:");
			System.out.println(httpDetails.toString());
		} catch (IOException e) {
			sendHeader(500,"Internal Server Error");
			hasErrors = true;
		} catch (Exception e) {
			sendHeader(400,"Bad Request");
			hasErrors = true;
		}
	}
	/**
	 * Method to return entity body. 
	 * The requested resource is read and returned back to the client.
	 * If the request is for a folder itself, or '/' the root, an 'index.html' file is opened if it exists and
	 * its contents are returned as an entity-body in a valid response
	 *
	 */
	public void sendResponse() {
		try {
			if (httpDetails.get("Mime Type").startsWith("image/")) {
				File imageFile = new File(httpDetails.get("Full path"));
				FileInputStream fs = new FileInputStream(imageFile);
				byte[] data = new byte[(int)imageFile.length()];
				fs.read(data, 0, (int)imageFile.length());
				fs.close();
				outStream.write(data);				
				outStream.flush();
			} else {
				FileReader file = new FileReader(httpDetails.get("Full path"));
				BufferedReader buff = new BufferedReader(file);
				Scanner s = new Scanner(buff);

				while (s.hasNext()) {
					outStream.writeBytes(s.nextLine());
				}
				outStream.flush();
				System.out.println("Response Sent");
			}

		} catch (IOException e) {
			sendHeader(500,"Internal Server Error");
			hasErrors = true;
		} catch (Exception e) {
			try {
				outStream.writeBytes("");
				sendHeader(500,"Internal Server Error");
				hasErrors = true;
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}
	/**
	 * Method to check for errors. 
	 * For now, the method checks for 404, 500, 501 & 505 and sends corresponding status messages.
	 * @return boolean true indicates presence of errors
	 *
	 */
	public void checkErrors(){
		if (hasErrors != true) {
			if (!new File(httpDetails.get("Full path")).exists()) {
				hasErrors = true;
				sendHeader(404,"Not Found");
				try {
					outStream.writeBytes("<html><head><title>Page not found</title></head><body><b>HTTP 404 - The page was not found.</b></body></html>\r\n");
					outStream.flush();
				} catch (IOException e) {
					hasErrors = true;
				}
			} else if (new File(httpDetails.get("Full path")).length() == 0) {
				hasErrors = true;
				sendHeader(500,"Internal Server Error");
				try {
					outStream.writeBytes("<html><head><title>Internal Server Error</title></head><body><b>HTTP 500 - Internal Server Error</b></body></html>\r\n");
					outStream.flush();
				} catch (IOException e) {
					hasErrors = true;
				}
			}
			if (!(httpDetails.get("Version").equals("HTTP/1.0") || httpDetails.get("Version").equals("HTTP/1.1"))) {
				hasErrors = true;
				sendHeader(505, "HTTP Version Not Supported");
				try {
					outStream.writeBytes("<html><head><title>HTTP Version Not Supported</title></head><body><b>HTTP 505 - HTTP Version Not Supported</b></body></html>\r\n");
					outStream.flush();
				} catch (IOException e) {
					hasErrors = true;
				}
			}
			if (!(httpDetails.get("Request Type").equals("GET") || httpDetails.get("Request Type").equals("HEAD"))) {
				hasErrors = true;
				sendHeader(501,"Not Implemented");
				try {
					outStream.writeBytes("<html><head><title>Not Implemented</title></head><body><b>HTTP 501 - Not Implemented</b></body></html>\r\n");
					outStream.flush();
				} catch (IOException e) {
					hasErrors = true;
				}
			}
		}
	}
	/**
	 * Method to construct and send HTTP headers.
	 * @param statusCode The HTTP Status Code
	 * @param statusMessage The Status message corresponding to the status code.
	 *
	 */
	public void sendHeader(int statusCode, String statusMessage) {
		try {
			outStream.writeBytes(httpDetails.get("Version") + " " + statusCode + " " + statusMessage + "\r\n");
			outStream.writeBytes("Server: Simple/1.0\r\n");
			outStream.writeBytes("Content-Type: " + httpDetails.get("Mime Type") + "\r\n");
			outStream.writeBytes("\r\n");
			outStream.flush();
		} catch (IOException e) {
			try {
				outStream.writeBytes("");
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

	}
}
