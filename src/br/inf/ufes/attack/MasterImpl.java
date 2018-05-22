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
	
	//criar classe para attack info?
	//clientinfo?
	//na spec o mestre retorna pro client a mensagem que o client vai salvar no pc
	
	private class AttackInfo{
		long indexNow;
		long indexEnd;
		int attackNumber;
		byte[] cypherText;
		byte[] knownWord;
		
		public AttackInfo(long indexNow, long indexEnd, int attackNumber, byte[] cypherText, byte[] knownWord) {
			this.indexNow = indexNow;
			this.indexEnd = indexEnd;
			this.cypherText = cypherText;
			this.knownWord = knownWord;
			this.attackNumber = attackNumber;
		}
	}
	
	private class SlaveInfo {
		Slave s; 		// referencia pro escravo
		boolean alive; // flag para verificar se o escravo esta vivo ou nao
		String name;
		List<AttackInfo> attacks;
		
		SlaveInfo(Slave s, String name) {
			this.s = s;
			this.name = name;
			this.attacks = new ArrayList<>();
			this.alive = true;
		}
	}
	
	public MasterImpl() {
		registeredSlaves = new HashMap<UUID, SlaveInfo>();
		guesses = new ArrayList<>();
		currentAttack = 0;
	}
	
	@Override
	public void addSlave(Slave s, String slaveName, UUID slavekey) throws RemoteException {
		synchronized (registeredSlaves) {
			System.out.println("addSlave request " + slavekey);
			//System.out.println(s);
			registeredSlaves.put(slavekey, 
					new SlaveInfo(s, slaveName));
		}
	}

	@Override
	public void removeSlave(UUID slaveKey) throws RemoteException {
		synchronized (registeredSlaves) {
			SlaveInfo toBe = registeredSlaves.remove(slaveKey);
			Random generator = new Random();
			SlaveInfo[] values = (SlaveInfo[]) registeredSlaves.values().toArray();
			SlaveInfo randomSlave = values[generator.nextInt(values.length)];
			toBe.attacks.forEach((i)-> requestAttack(randomSlave, i.cypherText, i.knownWord, i.indexNow, i.indexEnd, i.attackNumber));
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
		i.alive = true;
		i.attacks.get(attackNumber); //TODO: fazer e gerenciar uma lista dos indices usados e ja visitados
		System.out.println("Escravo: " + i.name + " | Indice Atual: " + currentindex + " | " + currentguess.getMessage());
		try (FileOutputStream fos = new FileOutputStream(currentguess.getKey() + ".msg")) {
		   fos.write(currentguess.getMessage());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}

	@Override
	public void checkpoint(UUID slaveKey, int attackNumber, long currentindex) throws RemoteException {
		synchronized (registeredSlaves) {
			SlaveInfo i = registeredSlaves.get(slaveKey);
			i.alive = true;
			AttackInfo ai = i.attacks.get(attackNumber);
			ai.indexNow = currentindex;
			if(ai.indexEnd == currentindex) {
				penddingSubAttackNum--;
				if(!(penddingSubAttackNum > 0)) {
					this.notify();
				}
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
			SlaveImpl s = (SlaveImpl) item.s;
			k = s.getUuid();
			System.out.println("Slave " + s.getUuid() + "start attack");
			s.startSubAttack(ciphertext, knowntext, initialwordindex, finalwordindex, attackNumber, this);
			item.attacks.add(attackNumber, new AttackInfo(initialwordindex, finalwordindex, attackNumber, ciphertext, knowntext));
			Timer timer = new Timer();
			timer.scheduleAtFixedRate(new CheckerTasker(k), 20000, 20000);
			penddingSubAttackNum++;
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
		int count = 1;
		int attackNumber = currentAttack++;
		for (SlaveInfo s : cpy) {
			requestAttack(s, ciphertext, knowntext, index_size*(count-1), index_size*count - 1, attackNumber);
			count++;
		}
		try {
			wait();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return (Guess[]) guesses.toArray();
	}
	
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
