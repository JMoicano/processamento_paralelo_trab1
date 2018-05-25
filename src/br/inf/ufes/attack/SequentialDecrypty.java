package br.inf.ufes.attack;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class SequentialDecrypty {

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

	private static void saveFile(String filename, byte[] data) throws IOException {

		FileOutputStream out = new FileOutputStream(filename);
		out.write(data);
		out.close();

	}


	public static void main(String[] args) {
		// args[0] arquivo dicionario
		// args[1] e o nome do arquivo de entrada
		// args[2] texto conhecido
		File f = new File(args[0]);
		File inFile = new File(args[1]);
		String known_text = args[2];
		List<String> _dict = null;
		try(FileReader fileReader = new FileReader(f);
			BufferedReader b = new BufferedReader(fileReader)) {
			_dict = new ArrayList<String>();
			String readLine = "";
			while ((readLine = b.readLine()) != null) {
				_dict.add(readLine);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		byte[] ciphertext = null;
		
		try {
			ciphertext = Files.readAllBytes(inFile.toPath());
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		long startTime = System.nanoTime();		
		for (int aux = 0; aux < _dict.size(); ++aux) {
		
			byte[] key = _dict.get((int)aux).getBytes();
			try {
				SecretKeySpec keySpec = new SecretKeySpec(key, "Blowfish");
	
				Cipher cipher = Cipher.getInstance("Blowfish");
				cipher.init(Cipher.DECRYPT_MODE, keySpec);
	
				byte[] message = ciphertext;
	
				byte[] decrypted = cipher.doFinal(message);
				String decryptedStr = new String(decrypted);
				if (decryptedStr.contains(known_text)) {
					saveFile(new String(key) +".msg", decrypted);
				}
			} catch (javax.crypto.BadPaddingException | InvalidKeyException | IllegalBlockSizeException | NoSuchAlgorithmException | NoSuchPaddingException | IOException e) { }
			
			
		}
		double time_diff = (System.nanoTime() - startTime)/1000000;
		System.out.println(time_diff);

		try {

			byte[] key = args[0].getBytes();
			SecretKeySpec keySpec = new SecretKeySpec(key, "Blowfish");

			Cipher cipher = Cipher.getInstance("Blowfish");
			cipher.init(Cipher.DECRYPT_MODE, keySpec);

			byte[] message = readFile(args[1]);
			System.out.println("message size (bytes) = "+ message.length);

			byte[] decrypted = cipher.doFinal(message);

			

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
