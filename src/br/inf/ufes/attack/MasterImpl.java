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
	private static final int dict_size = 1;//80368;
	private int penddingSubAttackNum;
	private int currentAttack;
	private Object waiter;
	
	private class AttackInfo{
		long indexNow;
		long indexEnd;
		byte[] cypherText;
		byte[] knownWord;
		Timer timer;
		
		public AttackInfo(long indexNow, long indexEnd, int attackNumber, byte[] cypherText, byte[] knownWord, Timer timer) {
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
			System.out.println("addSlave request " + slaveName);
			//System.out.println(s);
			registeredSlaves.put(slavekey, 
					new SlaveInfo(s, slaveName,slavekey));
		}
	}

	@Override
	public void removeSlave(UUID slaveKey) throws RemoteException {
		synchronized (registeredSlaves) {
			SlaveInfo toBe = registeredSlaves.remove(slaveKey);
			System.out.println("removeSlave " + slaveKey);
			Random generator = new Random();
			SlaveInfo[] values = (SlaveInfo[]) registeredSlaves.values().toArray();
			SlaveInfo randomSlave = values[generator.nextInt(values.length)];
			toBe.attacks.forEach((a, i)-> requestAttack(randomSlave, i.cypherText, i.knownWord, i.indexNow, i.indexEnd, a));
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
		System.out.println("Escravo: " + i.name + " | Indice Atual: " + currentindex + " | " + new String(currentguess.getKey()));
		checkpoint(slaveKey, attackNumber, currentindex);		
	}

	@Override
	public void checkpoint(UUID slaveKey, int attackNumber, long currentindex) throws RemoteException {
		synchronized (registeredSlaves) {
			SlaveInfo i = registeredSlaves.get(slaveKey);
			i.alive = true;
			AttackInfo ai = i.attacks.get(attackNumber);
			ai.indexNow = currentindex;
			if(!(ai.indexEnd > currentindex)) {
				ai.timer.cancel();
				--penddingSubAttackNum;
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
			if(registeredSlaves.get(key).alive) {
				registeredSlaves.get(key).alive = false;
			}else {
				try {
					synchronized (registeredSlaves) {
						removeSlave(key);	
					}
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
			System.out.println("Slave " + k + "start attack");
			Timer timer = new Timer();
			item.attacks.put(attackNumber, new AttackInfo(initialwordindex, finalwordindex, attackNumber, ciphertext, knowntext, timer));
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
			//cpy = new HashMap<UUID, SlaveInfo>(registeredSlaves);
			cpy = new ArrayList<SlaveInfo>(registeredSlaves.values());
		}
		
		penddingSubAttackNum = 0;
		int index_size = this.dict_size/cpy.size();
		System.out.println("size: " +index_size);
		int count = 0;
		int attackNumber = currentAttack++;
		for (SlaveInfo s : cpy) {
			requestAttack(s, ciphertext, knowntext, index_size*count, index_size*(count+1) - 1, attackNumber);
			count++;
		}

		while(penddingSubAttackNum > 0);

		return guesses.toArray(new Guess[0]);
	}
	
	public static void main(String args[]) {
		try {
			Master mestre = new MasterImpl();
			Master mestreref = (Master) UnicastRemoteObject.exportObject(mestre, 2000);
			Registry registry = LocateRegistry.getRegistry("127.0.0.1"); // opcional: host
			registry.rebind("mestre", mestreref);
		    System.err.println("Server ready");
		} catch (Exception e) {
			System.err.println("Server exception: " + e.toString()); 
			e.printStackTrace();
		}
	}	
}
