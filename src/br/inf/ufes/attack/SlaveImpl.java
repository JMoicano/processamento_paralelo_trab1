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
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import br.inf.ufes.ppd.Guess;
import br.inf.ufes.ppd.Master;
import br.inf.ufes.ppd.Slave;
import br.inf.ufes.ppd.SlaveManager;

public class SlaveImpl implements Slave, Serializable {

	private Master mestre;
	private UUID uuid;
	private Object currentLock;
	private long initialindex;
	private long finalindex;
	private ArrayList<String> _dict;
	
	public void printDict() {
		for(int i = 0; i < this._dict.size(); i++) System.out.println("[" + i + "] " + this._dict.get(i));
	}

	public SlaveImpl(String host, String dicPath) {
		this.initialindex = -1;
		this.finalindex = -1;
		try {
			Registry registry = LocateRegistry.getRegistry(host);
			mestre = (Master) registry.lookup("mestre");
			uuid = UUID.randomUUID();

			mestre.addSlave(this, "qualquerCoisa", uuid);
			System.out.println("Slave uuid: " + uuid);
			
		} catch (RemoteException e) {
			System.err.println("1Server exception: " + e.toString()); 
			e.printStackTrace();
		} catch (NotBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new RegisterTask(this), 30000, 30000);
		
		// TODO remove hard coded dict path
		File f = new File(dicPath);
		FileReader fileReader;
		try {
			fileReader = new FileReader(f);
			BufferedReader b = new BufferedReader(fileReader);
			this._dict = new ArrayList<String>();
			String readLine = "";
			System.out.println("Reading file using Buffered Reader");
			while ((readLine = b.readLine()) != null) {
				this._dict.add(readLine);
			}
//			for (int i = 0; i < this._dict.size(); i++) {
//				System.out.println(i + ": " + this._dict.get(i));
//			}
			b.close();
			System.out.println();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		

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
			long aux;
			synchronized (s.currentLock) {
				aux = s.initialindex;
			}
			try {
				s.mestre.checkpoint(s.getUuid(), attackNumber, aux);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private class RegisterTask extends TimerTask{
		SlaveImpl s;
	
		
		public RegisterTask(SlaveImpl s) {
			super();
			this.s = s;
		}

		public void run() {
			try {
				//System.out.println("Running... " + ++count);
				s.mestre.addSlave(s, "qualquerCoisa", s.getUuid());
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}
	
	@Override
	public void startSubAttack(byte[] ciphertext, byte[] knowntext, long initialwordindex, long finalwordindex,
			int attackNumber, SlaveManager callbackinterface) throws RemoteException {
		System.out.println("Starting new attack.");
		this.initialindex = initialwordindex;
		this.finalindex = finalwordindex;
		//callbackinterface.foundGuess(uuid, attackNumber, 0, null);

		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new CheckpointTask(this, attackNumber), 10000, 10000);
		
		long aux;
		while (this.initialindex <= this.finalindex) {
			synchronized (this.currentLock) {
				aux = this.initialindex++;				
			}

			try {

				String known_text = knowntext.toString();
				byte[] key = this._dict.get((int)aux).getBytes();
				SecretKeySpec keySpec = new SecretKeySpec(key, "Blowfish");

				Cipher cipher = Cipher.getInstance("Blowfish");
				cipher.init(Cipher.DECRYPT_MODE, keySpec);

				byte[] message = ciphertext;
				System.out.println("message size (bytes) = "+ message.length);

				byte[] decrypted = cipher.doFinal(message);
				
				if (decrypted.toString().contains(known_text)) {
					Guess g = new Guess();
					g.setKey(key.toString());
					g.setMessage(decrypted);
					callbackinterface.foundGuess(this.uuid, attackNumber, this.initialindex, g);
				}
				
				//TODO timer para checkpoint
				
				
			//	saveFile(args[0]+".msg", decrypted);

			} catch (javax.crypto.BadPaddingException e) {
				// essa excecao e jogada quando a senha esta incorreta
				// porem nao quer dizer que a senha esta correta se nao jogar essa excecao
				System.out.println("Senha invalida.");

			} catch (Exception e) {
				//dont try this at home
				e.printStackTrace();
			}
			
		}
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
// Slave não tem que salvar para arquivo a solucao, so retornar a chave se correta
//	private static void saveFile(String filename, byte[] data) throws IOException {
//
//		FileOutputStream out = new FileOutputStream(filename);
//		out.write(data);
//		out.close();
//
//	}


	public static void main(String[] args) throws IOException{
		// args[0] e a chave a ser usada
		// args[1] e o nome do arquivo de entrada
//		String[] dict = null; // carregar dicionario
//		try {
//			File f = new File("dict.txt");
//			BufferedReader b = new BufferedReader(new FileReader(f));
//			String readLine = "";
//			System.out.println("Reading file using Buffered Reader");
//			int i = 0;
//			while ((readLine = b.readLine()) != null) {
//				dict[i] = readLine;
//			}
//		} catch (IOException e) {
//			// TODO: handle exception
//		}
		
		
	}

}
