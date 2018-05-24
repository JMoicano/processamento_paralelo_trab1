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
	private int penddingSubAttackNum;
	private int currentAttack;
	private Object waiter;
	
	private class AttackInfo{
		long indexNow;
		long indexEnd;
		byte[] cypherText;
		byte[] knownWord;
		Timer timer;
		
		public AttackInfo(long indexNow, long indexEnd, byte[] cypherText, byte[] knownWord, Timer timer) {
			this.indexNow = indexNow;
			this.indexEnd = indexEnd;
			this.cypherText = cypherText;
			this.knownWord = knownWord;
			this.timer = timer;
		}
	}
	
	private class SlaveInfo {
		Slave s; 		// referencia pro escravo
		boolean alive; // flag para verificar se o escravo esta vivo ou nao
		String name;
		Map<Integer, AttackInfo> attacks;
		UUID uuid;
		
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
			registeredSlaves.put(slavekey, 
					new SlaveInfo(s, slaveName,slavekey));
		}
	}

	@Override
	public void removeSlave(UUID slaveKey) throws RemoteException {
		synchronized (registeredSlaves) {
			SlaveInfo toBe = registeredSlaves.remove(slaveKey);
			System.out.println("Removing Slave " + slaveKey);
			Random generator = new Random();
			SlaveInfo[] values = registeredSlaves.values().toArray(new SlaveInfo[0]);
			SlaveInfo randomSlave = values[generator.nextInt(values.length)];
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

		ai = i.attacks.get(attackNumber);
		if(ai != null) {
			ai.indexNow = currentindex;
	
			if(!(currentindex < ai.indexEnd)) {
				--penddingSubAttackNum;
				ai.timer.cancel();
				
			}
		}else {
			--penddingSubAttackNum;
		}
		if(penddingSubAttackNum <= 0) {
			synchronized (waiter) {
				waiter.notify();						
			}
		}
		
	}

	private class CheckerTasker extends TimerTask{
		UUID key;

		public CheckerTasker(UUID key) {
			super();
			this.key = key;
		}

		@Override
		public void run() {
			System.out.println(" Checking slave " + registeredSlaves.get(key).name + ":");
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
			long initialwordindex, long finalwordindex,	int attackNumber) {
		UUID k = null;
		try {
			Slave s = item.s;
			k = item.uuid;
			System.out.println("Slave " + item.name + " started Attack #" + attackNumber);
			Timer timer = new Timer();
			synchronized (item.attacks) {
				item.attacks.put(attackNumber, new AttackInfo(initialwordindex, finalwordindex, ciphertext, knowntext, timer));				
			}
			timer.scheduleAtFixedRate(new CheckerTasker(k), 20000, 20000);
			penddingSubAttackNum++;
			s.startSubAttack(ciphertext, knowntext, initialwordindex, finalwordindex, attackNumber, this);
		} catch (RemoteException e) {
			synchronized (registeredSlaves) {
				registeredSlaves.remove(k);
			}
		}
	}
	
	@Override
	public Guess[] attack(byte[] ciphertext, byte[] knowntext) throws RemoteException {
		ArrayList<SlaveInfo> cpy;
		synchronized (registeredSlaves) {
			cpy = new ArrayList<SlaveInfo>(registeredSlaves.values());
		}
		
		penddingSubAttackNum = 0;
		int index_size = dict_size/cpy.size();
		int count = 0;
		int attackNumber = currentAttack++;
		System.out.println("Attack #" + attackNumber + " started");
		for (SlaveInfo s : cpy) {
			requestAttack(s, ciphertext, knowntext, index_size*count, index_size*(count+1) - 1, attackNumber);
			count++;
		}

		
		try {
			synchronized (waiter) {
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
			Master mestre = new MasterImpl();
			Master mestreref = (Master) UnicastRemoteObject.exportObject(mestre, 2000);
			Registry registry = LocateRegistry.getRegistry(); // opcional: host
			registry.rebind("mestre", mestreref);
		    System.err.println("Master online");
		} catch (Exception e) {
			System.err.println("Server exception: " + e.toString()); 
			e.printStackTrace();
		}
	}	
}
