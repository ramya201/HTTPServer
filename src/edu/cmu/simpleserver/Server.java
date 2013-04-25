package edu.cmu.simpleserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Scanner;

import edu.cmu.multithreadedserver.GetMime;


public class Server {
	private static ServerSocket srvSock;
	private String www_root;
	private Hashtable<String,String> httpDetails = new Hashtable<String,String>();
	private BufferedReader inStream = null;
	private DataOutputStream outStream = null;

	public Server(int portNo) {
		try {
			srvSock = new ServerSocket(portNo);
			System.out.println("Simple Server is listening on Port " + portNo);
		} catch (IOException e) {
			System.err.println("Could not listen on Port " + portNo);
			System.exit(1);
		}
	}

	public static ServerSocket getSrvSock() {
		return srvSock;
	}

	public static void main(String args[]) {
		System.out.println("Enter port number");
		Scanner read = new Scanner(System.in);
		int port = read.nextInt();

		if (port > 65535 || port < 1024) {
			System.err.println("Port number must be in between 1024 and 65535");
			System.exit(1);
		}

		Server server = new Server(port);
		
		System.out.println("Enter complete path to the 'www' website root"); 
		System.out.println("e.g. /Users/ramya/Documents/Softwares/Ramya_Programs/Project2-Telecom");
		Scanner readPath = new Scanner(System.in);
		server.www_root = readPath.nextLine();
		
		if (server.www_root.endsWith("/") || !(new File(server.www_root + "/www")).exists()){
			System.err.println("Invalid syntax or path!");
			System.exit(1);
		}

		while (true) {
			Socket clientSock;
			try {
				clientSock = srvSock.accept();
				System.out.println("Accepted new connection from "
						+ clientSock.getInetAddress() + ":"
						+ clientSock.getPort());
			} catch (IOException e) {
				continue;
			}
			server.parseRequest(clientSock);
			
			if (server.hasErrors() == false) {
				server.sendHeader(200,"OK");
				if (server.httpDetails.get("Request Type").equals("GET")) {
					server.sendResponse(clientSock);
				}
			}
			
			try {
				if (server.inStream != null) {
					server.inStream.close();
				}
				if (server.outStream != null) {
					server.outStream.close();
				}
				if (clientSock != null) {
					clientSock.close();
				}

			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}
	public void sendHeader(int statusCode, String statusMessage) {
		try {
			outStream.writeBytes(httpDetails.get("Version") + " " + statusCode + " " + statusMessage + "\r\n");
			outStream.writeBytes("Server: Simple/1.0\r\n");
			outStream.writeBytes("Content-Type: " + httpDetails.get("Mime Type") + "\r\n");
			outStream.writeBytes("\r\n");
			outStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}

	private boolean hasErrors() {
		boolean errors = false;
		if (!new File(httpDetails.get("Full path")).exists()) {
			errors = true;
			sendHeader(404,"Not Found");
			try {
				outStream.writeBytes("<html><head><title>Page not found</title></head><body><b>HTTP 404 - The page was not found.</b></body></html>\r\n");
				outStream.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (new File(httpDetails.get("Full path")).length() == 0) {
			errors = true;
			sendHeader(500,"Internal Server Error");
			try {
				outStream.writeBytes("<html><head><title>Internal Server Error</title></head><body><b>HTTP 500 - Internal Server Error</b></body></html>\r\n");
				outStream.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (!(httpDetails.get("Version").equals("HTTP/1.0") || httpDetails.get("Version").equals("HTTP/1.1"))) {
			errors = true;
			sendHeader(505, "HTTP Version Not Supported");
			try {
				outStream.writeBytes("<html><head><title>HTTP Version Not Supported</title></head><body><b>HTTP 505 - HTTP Version Not Supported</b></body></html>\r\n");
				outStream.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (!(httpDetails.get("Request Type").equals("GET") || httpDetails.get("Request Type").equals("HEAD"))) {
			errors = true;
			sendHeader(501,"Not Implemented");
			try {
				outStream.writeBytes("<html><head><title>Not Implemented</title></head><body><b>HTTP 501 - Not Implemented</b></body></html>\r\n");
				outStream.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return errors;

	}

	public void parseRequest(Socket clientSock) {
		String buffer = null;

		try {
			inStream = new BufferedReader(new InputStreamReader(clientSock.getInputStream()));
			outStream = new DataOutputStream(clientSock.getOutputStream());

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
						+ clientSock.getInetAddress() + ":"
						+ clientSock.getPort() + " " + buffer);	
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
			e.printStackTrace();
		}
	}

	public void sendResponse(Socket clientSock) {

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
			e.printStackTrace();
		} catch (Exception e) {
			try {
				outStream.writeBytes("");
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

}
