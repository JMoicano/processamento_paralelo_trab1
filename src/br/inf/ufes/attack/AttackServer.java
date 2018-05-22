package br.inf.ufes.attack;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

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
		
		if(!inFile.exists()) {
			if(size == 0) {
				Random r = new Random();
				size = r.nextInt(99000) + 1000;
			}
			
			byte[] bytes = new byte[size];
			try {
				SecureRandom.getInstanceStrong().nextBytes(bytes);
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			knownWord = bytes.toString().substring(0, 5);
			
		}
		byte[] criptedFile = null;
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

}
