package br.inf.ufes.attack;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import br.inf.ufes.ppd.Guess;
import br.inf.ufes.ppd.Master;
import br.inf.ufes.ppd.Slave;
import br.inf.ufes.ppd.SlaveManager;

public class SlaveImpl implements Slave, Serializable {

	private Master mestre;
	private UUID uuid;
	private long initialindex;
	private long finalindex;
	private ArrayList<String> _dict;
	private String hostname;
	private String name;
	
	public void printDict() {
		for(int i = 0; i < this._dict.size(); i++) System.out.println("[" + i + "] " + this._dict.get(i));
	}

	public SlaveImpl(String host, String dicPath, String name) {
		this.hostname = host;
		this.name = name;
		uuid = UUID.randomUUID();
		this.initialindex = -1;
		this.finalindex = -1;

		File f = new File(dicPath);
		 
		try(FileReader fileReader = new FileReader(f);
			BufferedReader b = new BufferedReader(fileReader)) {
			this._dict = new ArrayList<String>();
			String readLine = "";
			while ((readLine = b.readLine()) != null) {
				this._dict.add(readLine);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new RegisterTask(this), 0, 30000);
				
		

	}

	public void setInitialIndex(long i) {
		this.initialindex = i;
	}

	public long getInitialIndex() {
		return this.initialindex;
	}

	public void setFinalIndex(long i) {
		this.finalindex = i;
	}

	public long getFinalIndex() {
		return this.finalindex;
	}
	
	public int sizeDict() {
		return this._dict.size();
	}

	public UUID getUuid() {
		return uuid;
	}
	
	private class CheckpointTask extends TimerTask{
		SlaveImpl s;
		int attackNumber;
		
		
		
		public CheckpointTask(SlaveImpl s, int attackNumber) {
			super();
			this.s = s;
			this.attackNumber = attackNumber;
		}

		public void run() {
			try {
				s.mestre.checkpoint(s.uuid, attackNumber, s.initialindex);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}

	private class RegisterTask extends TimerTask{
		Slave s;
	
		
		public RegisterTask(Slave s) {
			super();
			try {
				this.s = (Slave) UnicastRemoteObject.exportObject(s, 0);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

		public void run() {
			try {
				Registry registry = LocateRegistry.getRegistry(hostname);
				mestre = (Master) registry.lookup("mestre");
				mestre.addSlave(s, name, uuid);
			} catch (RemoteException | NotBoundException e) {
				e.printStackTrace();
			}
		}

	}
	
	
	
	private class AttackThread extends Thread{
		byte[] ciphertext;
		byte[] knowntext;
		int attackNumber;
		SlaveManager callbackinterface;
		Timer timer;
		
		public AttackThread (byte[] ciphertext, byte[] knowntext,
			int attackNumber, SlaveManager callbackinterface, Timer timer) {
			this.ciphertext = ciphertext;
			this.knowntext = knowntext;
			this.attackNumber = attackNumber;
			this.callbackinterface = callbackinterface;
			this.timer = timer;
		}
		public void run() {
			timer.scheduleAtFixedRate(new CheckpointTask(SlaveImpl.this, attackNumber), 10000, 10000);
			long aux;
			while (initialindex <= finalindex) {
				aux = initialindex++;				
			
				String known_text = new String(knowntext);
				byte[] key = _dict.get((int)aux).getBytes();
				try {
					SecretKeySpec keySpec = new SecretKeySpec(key, "Blowfish");
		
					Cipher cipher = Cipher.getInstance("Blowfish");
					cipher.init(Cipher.DECRYPT_MODE, keySpec);
		
					byte[] message = ciphertext;
		
//					byte[] decrypted = cipher.doFinal(message);
//					String decryptedStr = new String(decrypted);
//					if (decryptedStr.contains(known_text)) {
//						Guess g = new Guess();
//						g.setKey(new String(key));
//						g.setMessage(decrypted);
//						callbackinterface.foundGuess(uuid, attackNumber, initialindex, g);
//					}
				} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException e) { }
				
				
			}
			try {
				System.out.println("Slave " + name + " ended subattack #" + attackNumber);
				mestre.checkpoint(uuid, attackNumber, initialindex);
				timer.cancel();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void startSubAttack(byte[] ciphertext, byte[] knowntext, long initialwordindex, long finalwordindex,
			int attackNumber, SlaveManager callbackinterface) throws RemoteException {
		this.initialindex = initialwordindex;
		this.finalindex = finalwordindex;

		Timer timer = new Timer();
		
		new AttackThread(ciphertext, knowntext, attackNumber, callbackinterface, timer).start();
		
		
	}
			

	private static byte[] readFile(String filename) throws IOException {

		File file = new File(filename);
		InputStream is = new FileInputStream(file);
		long length = file.length();

		//creates array (assumes file length<Integer.MAX_VALUE)
		byte[] data = new byte[(int)length];

		int offset = 0;
		int count = 0;

		while((offset < data.length) && (count = is.read(data, offset, data.length-offset)) >= 0 ){
			offset += count;
		}
		is.close();
		return data;
	}


	public static void main(String[] args) {
		
		Slave s = new SlaveImpl(args[0], args[1], args[2]);

	}

}
