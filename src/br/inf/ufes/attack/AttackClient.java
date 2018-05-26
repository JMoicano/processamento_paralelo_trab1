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

public class AttackClient {
	public static void main(String args[]) {
		String host = args[0]; //adress to master host
		String dicPath = args[1]; //path to dctionary
		String inFilePath = args[2]; //path to encrypted file
		String knownWord = args[3]; //know word in the file
		ArrayList<String> _dict;
		String cryptKey = null;
		int size = 0;
		if(args.length == 5) {
			size = Integer.parseInt(args[4]); //size in bytes of the encrypted file
		} 
			
		File inFile = new File(inFilePath);
		byte[] bytes;

		File f = new File(dicPath);
		
		//Load dictionary
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
		
		//if encrypted file doesn't exists, generate a random one 
		if(!inFile.exists()) {
			if(size == 0) {
				Random r = new Random();
				//random size between 1000 and 100000
				size = r.nextInt(99000) + 1000 - knownWord.length();
			}
			
			bytes = new byte[size];
			try (FileOutputStream fos = new FileOutputStream(inFile)){
				new Random().nextBytes(bytes);
				
				//Concatenate generated file with known word
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
				outputStream.write(knownWord.getBytes());
				outputStream.write(bytes);

				bytes = outputStream.toByteArray();
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
			long startTime = System.nanoTime();
			Guess[] guesses = mestre.attack(criptedFile, knownWord.getBytes());
			double time_diff = (System.nanoTime() - startTime)/1000000;
			System.out.println(time_diff);
			for(Guess g : guesses) {
				saveFile(g.getKey() + ".msg", g.getMessage());
			}
		} catch (NotBoundException | IOException e) {
			e.printStackTrace();
		}

		
	}
	
	//Save a byte array into a file
	private static void saveFile(String filename, byte[] data) throws IOException {

		FileOutputStream out = new FileOutputStream(filename);
		out.write(data);
		out.close();

	}
	
	//encrypt a message (to use when generating a random file
	private static byte[] encryptMsg(String k, byte[] message) {
		try {
			byte[] key = k.getBytes();
			SecretKeySpec keySpec = new SecretKeySpec(key, "Blowfish");

			Cipher cipher = Cipher.getInstance("Blowfish");
			cipher.init(Cipher.ENCRYPT_MODE, keySpec);

			//System.out.println("message size (bytes) = "+message.length);

			byte[] encrypted = cipher.doFinal(message);

			return encrypted;

		} catch (Exception e) {
			e.printStackTrace();
		}
		return message;
	}

}
