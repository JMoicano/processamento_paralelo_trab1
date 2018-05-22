package br.inf.ufes.attack;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import br.inf.ufes.ppd.Master;

public class AttackServer {
	public static void main(String args[]) {
		String inFilePath = args[1];
		String knownWord = args[2];
		int size = 0;
		if(args.length == 4) {
			size = Integer.parseInt(args[3]);
		}
		
		File inFile = new File(inFilePath);
		byte[] bytes;

		byte[] criptedFile = null;
		if(!inFile.exists()) {
			if(size == 0) {
				Random r = new Random();
				size = r.nextInt(99000) + 1000;
			}
			
			bytes = new byte[size];
			try {
				SecureRandom.getInstanceStrong().nextBytes(bytes);
				knownWord = bytes.toString().substring(0, 5);
				saveFile(knownWord + ".ori", bytes);
				criptedFile = encryptMsg("madman", bytes);
				saveFile("encryptFile.cipher", criptedFile);
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();	
			}
		}
		try {
			criptedFile = Files.readAllBytes(inFile.toPath());
			
		} catch (IOException e1) {
			
			e1.printStackTrace();
		}
		Registry registry;
		
		try {
			registry = LocateRegistry.getRegistry("127.0.0.1");
			Master mestre = (Master) registry.lookup("mestre");
			
			mestre.attack(criptedFile, knownWord.getBytes());
		} catch (RemoteException | NotBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		
	}
	
	private static void saveFile(String filename, byte[] data) throws IOException {

		FileOutputStream out = new FileOutputStream(filename);
		out.write(data);
		out.close();

	}
	
	private static byte[] encryptMsg(String k, byte[] message) {
		try {
			byte[] key = k.getBytes();
			SecretKeySpec keySpec = new SecretKeySpec(key, "Blowfish");

			Cipher cipher = Cipher.getInstance("Blowfish");
			cipher.init(Cipher.ENCRYPT_MODE, keySpec);

			System.out.println("message size (bytes) = "+message.length);

			byte[] encrypted = cipher.doFinal(message);

			return encrypted;

		} catch (Exception e) {
			// don't try this at home
			e.printStackTrace();
		}
		return message;
	}

}
