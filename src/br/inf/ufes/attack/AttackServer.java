package br.inf.ufes.attack;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import br.inf.ufes.ppd.Master;

public class AttackServer {
	public static void main(String args[]) {
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
	}

}
