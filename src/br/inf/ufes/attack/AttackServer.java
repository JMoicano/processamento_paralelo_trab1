package br.inf.ufes.attack;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
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
			//TODO: implementar um gerador de entrada e salvar no arquivo
		}
		byte[] criptedFile;
		try {
			criptedFile = Files.readAllBytes(inFile.toPath());
			
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		try {
			Master mestre = new MasterImpl();
			Master mestreref = (Master) UnicastRemoteObject.exportObject(mestre, 2000);
			Registry registry = LocateRegistry.getRegistry("127.0.0.1"); // opcional: host
			registry.bind("mestre", mestreref);
		    System.err.println("Server ready");
		} catch (Exception e) {
			System.err.println("Server exception: " + e.toString()); 
			e.printStackTrace();
		}
		//TODO: fazer o ataque
	}

}
