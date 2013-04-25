package edu.cmu.simpleserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Client {
	public static void main(String[] args) {
		Socket sock;
		int port = 8080;
		InetAddress addr = null;
		BufferedReader inStream = null;
		DataOutputStream outStream = null;
		String buffer = null;

		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		
		System.out.println("Enter Server IP");
		Scanner read = new Scanner(System.in);
		try {
			addr = InetAddress.getByName(read.next());
		} catch (UnknownHostException e1) {
			System.err.println("Invalid address provided for server");
			System.exit(1);
		}

		System.out.println("Enter Port");
		port = read.nextInt();


		if (port > 65535 || port < 1024) {
			System.err.println("Port number must be in between 1024 and 65535");
			System.exit(1);
		}
		// To check total no of connections concurrently handled by server
		for (int i = 0; i< 10;i++) {
			try {
				buffer = "Testing...";
				sock = new Socket(addr, port);
			} catch (IOException e) {
				System.err.println("Unable to reach server");
				continue;
			}
			try {
				inStream = new BufferedReader(new InputStreamReader(
						sock.getInputStream()));
				outStream = new DataOutputStream(sock.getOutputStream());
				outStream.writeChars(buffer.toString());
				outStream.writeChar('\n');
				outStream.flush();
				buffer = inStream.readLine();
				System.out.println("Received : " + buffer.toString());
				sock.close();
			} catch (IOException e) {
				continue;
			}
		}

	}
}
