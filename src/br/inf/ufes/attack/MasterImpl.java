package br.inf.ufes.attack;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import br.inf.ufes.ppd.Guess;
import br.inf.ufes.ppd.Master;
import br.inf.ufes.ppd.Slave;

public class MasterImpl implements Master, Serializable {
	
	private Map<UUID, SlaveInfo> registeredSlaves;
	private List<Guess> guesses;
	private static final int dict_size = 80368;
	private Integer penddingSubAttackNum;
	private int currentAttack;
	private Object waiter;
	
	//Auxiliar class to manage subattack infos
	private class AttackInfo{
		long indexNow; //current index of subatttack
		long indexEnd; //final index of subattack
		byte[] cypherText; //encrypted text
		byte[] knownWord; //known word
		Timer timer; //timer to check if slave is alive
		
		public AttackInfo(long indexNow, long indexEnd, byte[] cypherText, byte[] knownWord, Timer timer) {
			this.indexNow = indexNow;
			this.indexEnd = indexEnd;
			this.cypherText = cypherText;
			this.knownWord = knownWord;
			this.timer = timer;
		}
	}
	
	
	//Auxiliar class to manage slave infos
	private class SlaveInfo {
		Slave s; 		// referencia pro escravo
		boolean alive; // flag para verificar se o escravo esta vivo ou nao
		String name; //slave name
		Map<Integer, AttackInfo> attacks; //map of its attacks
		UUID uuid; //its key
		
		SlaveInfo(Slave s, String name, UUID uuid) {
			this.s = s;
			this.name = name;
			this.uuid = uuid;
			this.attacks = new HashMap<>();
			this.alive = true;
		}
	}
	
	public MasterImpl() {
		registeredSlaves = new HashMap<UUID, SlaveInfo>();
		guesses = new ArrayList<>();
		waiter = new Object();
		currentAttack = 0;
	}
	
	@Override
	public void addSlave(Slave s, String slaveName, UUID slavekey) throws RemoteException {
		synchronized (registeredSlaves) {
			System.out.println("Slave " + slaveName + " added");
			//If is a new key, add to map, if not, just check it to alive
			if(!registeredSlaves.containsKey(slavekey)) {
				registeredSlaves.put(slavekey, 
						new SlaveInfo(s, slaveName,slavekey));
			}else {
				registeredSlaves.get(slavekey).alive = true;
			}
		}
	}

	@Override
	public void removeSlave(UUID slaveKey) throws RemoteException {
		synchronized (registeredSlaves) {
			SlaveInfo toBe = registeredSlaves.remove(slaveKey);
			System.out.println("Removing Slave " + slaveKey);
			//Select a random slave to receive all attacks of the to be removed slave
			Random generator = new Random();
			SlaveInfo[] values = registeredSlaves.values().toArray(new SlaveInfo[0]);
			SlaveInfo randomSlave = values[generator.nextInt(values.length)];
			//Assign all subattacks to other slave and cancel old checker
			toBe.attacks.forEach((a, i)-> {
				requestAttack(randomSlave, i.cypherText, i.knownWord, i.indexNow, i.indexEnd, a);
				i.timer.cancel();
			});
			penddingSubAttackNum--;
		}
	}

	@Override
	public void foundGuess(UUID slaveKey, int attackNumber, long currentindex, Guess currentguess)
			throws RemoteException {
		SlaveInfo i;
		

		synchronized (registeredSlaves) {
			i = registeredSlaves.get(slaveKey);
		}
		synchronized (guesses) {
			guesses.add(currentguess);
		}
		System.out.println("Found Gues:\n\t-> Slave: " + i.name + " | Current Index: " + currentindex + " | Candidate key: " + new String(currentguess.getKey()));
		//Send a checkpoint to update index and set slave to alive when find a guess
		checkpoint(slaveKey, attackNumber, currentindex);		
	}

	@Override
	public void checkpoint(UUID slaveKey, int attackNumber, long currentindex) throws RemoteException {

		AttackInfo ai;
		SlaveInfo i;
		synchronized (registeredSlaves) {
			i = registeredSlaves.get(slaveKey);
		}
		System.out.println("Checkpoint:\n\t-> Slave: " + i.name + " | Current Index: " + currentindex);
		i.alive = true;

		//Updates index of the atack info
		ai = i.attacks.get(attackNumber);
		ai.indexNow = currentindex;

		//Checks if subattack ended
		if(!(currentindex < ai.indexEnd)) {
			System.out.println(--penddingSubAttackNum + " Attacks remaning");
			//cancel checker timer
			ai.timer.cancel();
			
		}
		//Checking if all subattacks ended
		if(penddingSubAttackNum <= 0) {
			synchronized (waiter) {
				//notify into attack, that it ended
				waiter.notify();						
			}
		}
		
	}

	//Timer task to check if a attacking slave is alive
	private class CheckerTasker extends TimerTask{
		UUID key;

		public CheckerTasker(UUID key) {
			super();
			this.key = key;
		}

		@Override
		public void run() {
			System.out.println(" Checking slave " + registeredSlaves.get(key).name + ":");
			//if is alive, set to dead (if it not change to alive until next check time, he is removed)
			if(registeredSlaves.get(key).alive) {
				System.out.println("\t->Alive");
				registeredSlaves.get(key).alive = false;
			}else {
				System.out.println("\t->Dead");
				try {
					removeSlave(key);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		}
		
	}
	
	private void requestAttack(SlaveInfo item, byte[] ciphertext, byte[] knowntext,
			long initialwordindex, long finalwordindex,	int attackNumber) throws RemoteException {
		UUID k = null;
		
			Slave s = item.s;
			k = item.uuid;
			System.out.println("Slave " + item.name + " started Attack #" + attackNumber);
			Timer timer = new Timer();
			//Put attack info into current slave
			synchronized (item.attacks) {
				item.attacks.put(attackNumber, new AttackInfo(initialwordindex, finalwordindex, ciphertext, knowntext, timer));
			}
			//Timer to check if a slave is alive every 20 seconds
			timer.scheduleAtFixedRate(new CheckerTasker(k), 20000, 20000);
			//Increment number of pending subattacks
			penddingSubAttackNum++;
		try {
			s.startSubAttack(ciphertext, knowntext, initialwordindex, finalwordindex, attackNumber, this);
		} catch (RemoteException e) {
			synchronized (registeredSlaves) {
				removeSlave(k);
			}
		}
	}
	
	@Override
	public Guess[] attack(byte[] ciphertext, byte[] knowntext) throws RemoteException {
		ArrayList<SlaveInfo> cpy;
		//Copy a list of registered slaves
		synchronized (registeredSlaves) {
			cpy = new ArrayList<SlaveInfo>(registeredSlaves.values());
		}
		
		//Set the number of subattacks remaining to end a attack to 0 (none subattack requested)
		penddingSubAttackNum = 0;
		int index_size = dict_size/cpy.size(); //size of each subattack
		int count = 0;
		int attackNumber = currentAttack++;
		System.out.println("Attack #" + attackNumber + " started");
		for (SlaveInfo s : cpy) {
			requestAttack(s, ciphertext, knowntext, index_size*count, index_size*(count+1) - 1, attackNumber);
			count++;
		}

		
		try {
			synchronized (waiter) {
				//wait until all subattacks end
				waiter.wait();				
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return guesses.toArray(new Guess[0]);
	}
	
	public static void main(String args[]) {
		try {
			//Create an instance and register a reference to master in registry with name "master"
			Master mestre = new MasterImpl();
			Master mestreref = (Master) UnicastRemoteObject.exportObject(mestre, 0);
			Registry registry = LocateRegistry.getRegistry(args[0]); // opcional: host
			registry.rebind("mestre", mestreref);
		    System.err.println("Master online");
		} catch (Exception e) {
			System.err.println("Server exception: " + e.toString()); 
			e.printStackTrace();
		}
	}	
}
