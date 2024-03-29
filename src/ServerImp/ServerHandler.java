package ServerImp;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Client.Certificate;
import Client.ClientInfo;
import Client.SystemMsgForCertificate;
import Client.SystemMsgForNotify;
import SecurityAlgorithm.CaesarAlgorithm;

/**
 * This class implements Runnable interface to handle many clients requests. And
 * help to transfer massages among clients in secret way.
 * 
 * @author Zhichao Wang 2019/05/24
 * @version 1.0
 */
public class ServerHandler implements Runnable {
	private Map<String, String> clientLog = new HashMap<>();// 存储系统的合法用户
	private Map<String, Socket> clientSockets = new HashMap<>();// this obj is  used to  maintain the  information  between  server and  clients.
	private Map<String, Certificate> certificateList = new HashMap<>();// To  store  the  client 's RSA public Key
	private Map<String, Integer> curClientIndex = new HashMap<>();
	private Integer userIndex;
	private Socket socket;
	private  InputStream in = null;
	private  OutputStream out = null;
	private  OutputStream selfout = null;
	private  ObjectInputStream ois = null;
	private  ObjectOutputStream oos = null;
	private  ObjectOutputStream alloos = null;
	private String curClient = null;
	private String clientAsMode=null;
	 Map<String, ObjectOutputStream> clientOut=new HashMap<>();
	 Map<String, ObjectInputStream> clientIn=new HashMap<>();
/**
 * initiate server static data
 * @param socket
 * @param clientList
 * @param count
 * @param certificateList
 * @param clientLog2
 * @param clientIndex
 * @param clientIn
 * @param clientOut
 */
	public ServerHandler(Socket socket, Map<String, Socket> clientList, int count,
			Map<String, Certificate> certificateList, Map<String, String> clientLog2,
			Map<String, Integer> clientIndex,Map<String, ObjectInputStream> clientIn, Map<String, ObjectOutputStream> clientOut) {
		this.socket = socket;
		this.userIndex = count;
		this.certificateList = certificateList;
		this.curClientIndex = clientIndex;
		this.clientLog = clientLog2;
		
		this.clientSockets = clientList;
		this.clientIn=clientIn;
		this.clientOut=clientOut;
	}

	@Override
	public void run() {
		try {
			selfout = socket.getOutputStream();
			oos = new ObjectOutputStream(selfout);
			in = socket.getInputStream();
			ois = new ObjectInputStream(in);
			// receive log request from client
			ClientInfo logReq = null;
			while (true) {
				Thread.sleep(100);
				logReq = (ClientInfo) ois.readObject();
				if (clientLog.containsKey(logReq.getUsrname())) {
					if (clientLog.get(logReq.getUsrname()).equals(logReq.getMD5())) {
						// store client information
						clientSockets.put(logReq.getUsrname(), socket);
						curClientIndex.put(logReq.getUsrname(), userIndex);
						curClient = logReq.getUsrname();
						SystemMsgForNotify failInfo = new SystemMsgForNotify();
						String msg = new CaesarAlgorithm("10").encryptMsg("Welcome to the chat room!");
						failInfo.setMsg(msg);
						failInfo.setType(0);
						failInfo.setSender("System");
						failInfo.setIndex(userIndex);
						oos.writeObject(failInfo);
						oos.flush();
						clientIn.put(curClient, ois);
						clientOut.put(curClient, oos);
						break;
					}
				}
				// log fail.Send failed information to client
				SystemMsgForNotify failInfo = new SystemMsgForNotify();
				String msg = new CaesarAlgorithm("10").encryptMsg("Please check your account!");
				failInfo.setMsg(msg);
				failInfo.setSender("System");
				oos.writeObject(failInfo);
				oos.flush();
			}
			//receive client's certificate
			Certificate certificate = (Certificate) ois.readObject();
			certificateList.put(curClient, certificate);
			clientAsMode=certificate.getAsMode();
			// Broadcast client list and send online notice to every client
			SystemMsgForNotify broadcast = new SystemMsgForNotify();
			broadcast.setReceiver("everyone");
			broadcast.setSender("server");
			broadcast.setType(1);
			broadcast.setMsg(new CaesarAlgorithm("10").encryptMsg("Client " + curClient + " is online!"));
			List<String> list=new ArrayList<>();
			for (String string : curClientIndex.keySet()) {
				list.add(string);
			}
			for (ObjectOutputStream boradcastout : clientOut.values()) {
				boradcastout.writeObject(list);
				for (String a : list) {
				System.err.println(a);	
				}
				boradcastout.flush();
				boradcastout.writeObject(broadcast);
				boradcastout.flush();
				boradcastout=null;
			}

			/**
			 * New thread to handle the constant receiving from client.
			 */
			while (true) {
				try {
					try {
						if (socket.isClosed()) {
							offlineNotify();
							return;
						}
						Thread.sleep(1000);
						Object object = ois.readObject();
						if (object instanceof SystemMsgForNotify) {
							//handler client's request
							SystemMsgForNotify systemMsgForNotify=(SystemMsgForNotify)object;
							//System.err.println("request"+systemMsgForNotify.getSender());
							SystemMsgForCertificate systemMsgForCertificate=new SystemMsgForCertificate();
							Socket to=clientSockets.get(systemMsgForNotify.getReceiver());
							systemMsgForCertificate.setHost(to.getInetAddress().getHostName());
							systemMsgForCertificate.setPort(curClientIndex.get(systemMsgForNotify.getReceiver()));
							//System.err.println(systemMsgForNotify.getReceiver()+" 回应  "+systemMsgForCertificate.getPort());
							systemMsgForCertificate.setCertificate(certificateList.get(systemMsgForNotify.getReceiver()));
							clientOut.get(systemMsgForNotify.getSender()).writeObject(systemMsgForCertificate);
							clientOut.get(systemMsgForNotify.getSender()).flush();
						}
						
					} catch (EOFException e) {
						e.printStackTrace();
						offlineNotify();
						return;
					}
				}catch (SocketException e) {
					e.printStackTrace();
					ois.close();
					oos.close();
					socket.close();
					offlineNotify();
					System.err.println("clinet logout");
					return;
				} 
				catch (IOException e) {
					e.printStackTrace();
					try {
						ois.close();
						oos.close();
						socket.close();
						break;
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				} 
			}
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}

	public void offlineNotify() {
		System.err.println(curClient + " exit");
		curClientIndex.remove(curClient);
		clientSockets.remove(curClient);
		certificateList.remove(curClient);
		clientOut.remove(curClient);
		clientIn.remove(curClient);
		SystemMsgForNotify broadcast = new SystemMsgForNotify();
		broadcast.setReceiver("everyone");
		broadcast.setSender("server");
		broadcast.setType(1);
		broadcast.setMsg(new CaesarAlgorithm("10").encryptMsg("Client " + curClient + " is offline."));
		try {
			List<String> list=new ArrayList<>();
			for (String string : curClientIndex.keySet()) {
				list.add(string);
			}
			for (ObjectOutputStream boradcastout : clientOut.values()) {
				boradcastout.writeObject(list);
				for (String a : list) {
				System.err.println(a);	
				}
				boradcastout.flush();
				boradcastout.writeObject(broadcast);
				boradcastout.flush();
				boradcastout=null;
			}
		} catch (Exception e1) {
			e1.printStackTrace();
		}

	}
}
