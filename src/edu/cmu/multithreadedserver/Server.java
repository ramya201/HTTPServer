package edu.cmu.multithreadedserver;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Scanner;

public class Server {
	private ServerSocket serverSock;
	private Hashtable<InetAddress,Integer> activeConnections = new Hashtable<InetAddress,Integer>(); //Holds InetAddress and no of active connections to the particular InetAddress

	public Server(int portNo) {
		try {
			serverSock = new ServerSocket(portNo);
			System.out.println("Multithreaded Server is listening on Port " + portNo);
		} catch (IOException e) {
			System.err.println("Could not listen on Port " + portNo);
			System.exit(1);
		}
	}

	public ServerSocket getServerSock() {
		return serverSock;
	}

	public static void main(String[] args) {

		System.out.println("Enter port number");
		Scanner read = new Scanner(System.in);
		int port = read.nextInt();

		System.out.println("Enter complete path to the 'www' website root"); 
		System.out.println("e.g. /Users/ramya/Documents/Softwares/Ramya_Programs/Project2-Telecom");
		Scanner readPath = new Scanner(System.in);
		String www_root = readPath.nextLine();

		if (port > 65535 || port < 1024) {
			System.err.println("Port number must be in between 1024 and 65535");
			System.exit(1);
		}

		if (www_root.endsWith("/") || !(new File(www_root + "/www")).exists()){
			System.err.println("Invalid syntax or path!");
			System.exit(1);
		}
		System.out.println("\nEnter maximum number of concurrent connections"); 
		Scanner readMax = new Scanner(System.in);
		int maxConn = readMax.nextInt();

		System.out.println("\nEnter maximum number of concurrent connections per client"); 
		int maxConnClient = readMax.nextInt();

		Server server = new Server(port);
		boolean listening = true;

		while (listening) {
			try {
				Socket clientSock;
				clientSock = server.getServerSock().accept();

				synchronized (server.activeConnections) {	
					int totalConn = 0;

					//Get total active connections to all clients
					for (int conn:server.activeConnections.values()) {
						totalConn = totalConn + conn;
					}
					//Check if total connections is less than maximum limit
					if (totalConn < maxConn) {
						System.out.println("Total connections before accepting new connection: " + totalConn);
						if (!server.activeConnections.containsKey(clientSock.getInetAddress())) {
							server.activeConnections.put(clientSock.getInetAddress(), 1);
							Thread proxyThread = new Thread(new ProxyServer(clientSock,www_root,server.activeConnections));				
							proxyThread.start();
						} else {
							/*Check if total connections is less than maximum allowable limit per client.
							This is done to prevent DOS attacks so a particular client does not hog server resources.*/	
							if (server.activeConnections.get(clientSock.getInetAddress()) < maxConnClient) {
								int no = server.activeConnections.get(clientSock.getInetAddress());
								server.activeConnections.put(clientSock.getInetAddress(), ++no);
								Thread proxyThread = new Thread(new ProxyServer(clientSock,www_root,server.activeConnections));				
								proxyThread.start();
								System.out.println("Connection accepted!");
							} else {
								System.out.println("Connection refused!Connection limit per client exceeded");
								DataOutputStream outStream = new DataOutputStream(clientSock.getOutputStream());
								try {
									outStream.writeBytes("HTTP 503 - Service Unavailable\r\n");
									outStream.flush();

								} catch (IOException e) {
									outStream.writeBytes(" ");
								}
								if (outStream!=null) {
									outStream.close();
								}
							}
						} 
					}else {
						System.out.println("Connection refused!Total Connection limit exceeded!");
						DataOutputStream outStream = new DataOutputStream(clientSock.getOutputStream());
						try {
							outStream.writeBytes("HTTP 503 - Service Unavailable\r\n");
							outStream.flush();
						} catch (IOException e) {
							outStream.writeBytes(" ");
						}
						if (outStream!=null) {
							outStream.close();
						}
					}
				}
				System.out.println("Multithreaded Server continues listening for new connections..");

			} catch (IOException e) {
				e.printStackTrace();
			}	
		}
		try {
			server.serverSock.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
