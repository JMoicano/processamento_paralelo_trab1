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

	private Master mestre; //reference to master
	private UUID uuid; //uuid key
	private long initialindex; //initial index to attack
	private long finalindex; //final index to attack
	private ArrayList<String> _dict; //dictionary words
	private String hostname; //address to masters host
	private String name; //name of the slave
	
	public SlaveImpl(String host, String dicPath, String name) {
		this.hostname = host;
		this.name = name;
		uuid = UUID.randomUUID();

		File f = new File(dicPath);
		 
		//Initialize dictionary
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

		//Start a timer task to register in master every 30 seconds
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new RegisterTask(this), 0, 30000);
				
		

	}
	private static class KPM {
	    /**
	     * Search the data byte array for the first occurrence of the byte array pattern within given boundaries.
	     * @param data
	     * @param start First index in data
	     * @param stop Last index in data so that stop-start = length
	     * @param pattern What is being searched. '*' can be used as wildcard for "ANY character"
	     * @return
	     */
	    public static int indexOf( byte[] data, int start, int stop, byte[] pattern) {
	        if( data == null || pattern == null) return -1;

	        int[] failure = computeFailure(pattern);

	        int j = 0;

	        for( int i = start; i < stop; i++) {
	            while (j > 0 && ( pattern[j] != '*' && pattern[j] != data[i])) {
	                j = failure[j - 1];
	            }
	            if (pattern[j] == '*' || pattern[j] == data[i]) {
	                j++;
	            }
	            if (j == pattern.length) {
	                return i - pattern.length + 1;
	            }
	        }
	        return -1;
	    }

	    /**
	     * Computes the failure function using a boot-strapping process,
	     * where the pattern is matched against itself.
	     */
	    private static int[] computeFailure(byte[] pattern) {
	        int[] failure = new int[pattern.length];

	        int j = 0;
	        for (int i = 1; i < pattern.length; i++) {
	            while (j>0 && pattern[j] != pattern[i]) {
	                j = failure[j - 1];
	            }
	            if (pattern[j] == pattern[i]) {
	                j++;
	            }
	            failure[i] = j;
	        }

	        return failure;
	    }
	}
	
	
	//Timer task to send master a checkpoint
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

	
	//Timer task to register itself into master again
	private class RegisterTask extends TimerTask{
		Slave s;
	
		
		public RegisterTask(Slave s) {
			super();
			try {
				//Saving reference to slave to pass out to master
				this.s = (Slave) UnicastRemoteObject.exportObject(s, 0);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

		public void run() {
			try {
				//Locate master in registry and register to it
				Registry registry = LocateRegistry.getRegistry(hostname);
				mestre = (Master) registry.lookup("mestre");
				mestre.addSlave(s, name, uuid);
			} catch (RemoteException | NotBoundException e) {
				System.out.println("Mestre nao encontrado");
			}
		}

	}
	
	
	//Thread to run attack non-blocking
	private class AttackThread extends Thread{
		byte[] ciphertext;				//
		byte[] knowntext;				//Nedded information to attack
		int attackNumber;				//
		SlaveManager callbackinterface;	//****************************
		Timer timer;					//Timer to send a checkpoint of attack
		
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
			
				byte[] key = _dict.get((int)aux).getBytes();
				//Try to decrypt at every dictionary word in set indexes 
				try {
					SecretKeySpec keySpec = new SecretKeySpec(key, "Blowfish");
		
					Cipher cipher = Cipher.getInstance("Blowfish");
					cipher.init(Cipher.DECRYPT_MODE, keySpec);
		
					byte[] message = ciphertext;
		
					byte[] decrypted = cipher.doFinal(message);
					
					int found = KPM.indexOf(decrypted, 0, decrypted.length, knowntext);
					if (found > 0) {
						Guess g = new Guess();
						g.setKey(new String(key));
						g.setMessage(decrypted);
						//if a candidate word was found, send a callback to master
						callbackinterface.foundGuess(uuid, attackNumber, initialindex, g);
					}
				} catch (javax.crypto.BadPaddingException | InvalidKeyException | IllegalBlockSizeException | NoSuchAlgorithmException | NoSuchPaddingException | RemoteException e) { }
				
				
			}
			try {
				System.out.println("Slave " + name + " ended subattack #" + attackNumber);
				//Send the final checkpoint and cancel the checkpoint timer
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
		//Just start the attack thread
		new AttackThread(ciphertext, knowntext, attackNumber, callbackinterface, timer).start();
		
		
	}
			

	public static void main(String[] args) {
		
		//Slave cliente to run a slave given the host, path to dictionary and slave name
		Slave s = new SlaveImpl(args[0], args[1], args[2]);

	}

}
