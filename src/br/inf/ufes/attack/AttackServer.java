package br.inf.ufes.attack;


import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import br.inf.ufes.ppd.Guess;
import br.inf.ufes.ppd.Master;

public class AttackServer {
	public static void main(String args[]) {
		String host = args[0];
		String dicPath = args[1];
		String inFilePath = args[2];
		String knownWord = args[3];
		ArrayList<String> _dict;
		String cryptKey = null;
		int size = 0;
		if(args.length == 5) {
			size = Integer.parseInt(args[4]);
		} 
			
		File inFile = new File(inFilePath);
		byte[] bytes;

		File f = new File(dicPath);
		 
		try(FileReader fileReader = new FileReader(f);
			BufferedReader b = new BufferedReader(fileReader)) {
			_dict = new ArrayList<String>();
			String readLine = "";
			while ((readLine = b.readLine()) != null) {
				_dict.add(readLine);
			}
			cryptKey = _dict.get(new Random().nextInt(_dict.size()));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		byte[] criptedFile = null;
		if(!inFile.exists()) {
			if(size == 0) {
				Random r = new Random();
				size = r.nextInt(99000) + 1000 - knownWord.length();
			}
			
			bytes = new byte[size];
			try (FileOutputStream fos = new FileOutputStream(inFile)){
				new Random().nextBytes(bytes);
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
				for(byte b : knownWord.getBytes()) {
					System.out.println(b);
				}
				outputStream.write(knownWord.getBytes());
				outputStream.write(bytes);
				System.out.println(knownWord.getBytes());
				
				bytes = outputStream.toByteArray();
				System.out.println(bytes);
				if (cryptKey == null) cryptKey = "madman";
				saveFile("./" + cryptKey + ".ori", bytes);
				criptedFile = encryptMsg(cryptKey, bytes);
				fos.write(criptedFile);
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
			registry = LocateRegistry.getRegistry(host);
			Master mestre = (Master) registry.lookup("mestre");
			
			Guess[] guesses = mestre.attack(criptedFile, knownWord.getBytes());
			for(Guess g : guesses) {
				saveFile(g.getKey() + ".msg", g.getMessage());
			}
		} catch (NotBoundException | IOException e) {
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
