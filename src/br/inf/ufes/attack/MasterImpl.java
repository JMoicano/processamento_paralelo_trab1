package br.inf.ufes.attack;

import java.io.File;
import java.io.FileNotFoundException;
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
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.swing.plaf.FileChooserUI;

import br.inf.ufes.ppd.Guess;
import br.inf.ufes.ppd.Master;
import br.inf.ufes.ppd.Slave;

public class MasterImpl implements Master, Serializable {
	
	private Map<UUID, SlaveInfo> registeredSlaves;
	private List<Guess> guesses;
	private int dict_size;
	
	public void setDictSize(int v) {
		this.dict_size = v;
	}

	//criar classe para attack info?
	//clientinfo?
	//na spec o mestre retorna pro client a mensagem que o client vai salvar no pc
	
	private class AttackInfo{
		long indexNow;
		long indexEnd;
		
		public AttackInfo(long indexNow, long indexEnd) {
			this.indexNow = indexNow;
			this.indexEnd = indexEnd;
		}
	}
	
	private class SlaveInfo {
		Slave s; // referencia pro escravo
		boolean alive; // flag para verificar se o escravo esta vivo ou nao
		String name;
		Map<Integer, AttackInfo> attacks;
		
		SlaveInfo(Slave s, String name) {
			this.s = s;
			this.name = name;
			this.attacks = new HashMap<>();
			this.alive = true;
		}
	}
	
	public MasterImpl() {
		registeredSlaves = new HashMap<UUID, SlaveInfo>();
		guesses = new ArrayList<>();
		dict_size = -1;
	}
	
	@Override
	public void addSlave(Slave s, String slaveName, UUID slavekey) throws RemoteException {
		if (dict_size == -1) {
			SlaveImpl tmp = (SlaveImpl) s;
			this.setDictSize(tmp.sizeDict());
		}
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
			registeredSlaves.remove(slaveKey); //TODO: gerenciar os ataques desse escravo e os indices de ataque dele
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
			i.attacks.get(attackNumber); //TODO: fazer e gerenciar uma lista dos indices usados e ja visitados
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
	
	private void requestAttack(UUID k, SlaveInfo item, byte[] ciphertext, byte[] knowntext,
			long initialwordindex, long finalwordindex,	int attackNumber) {
		try {
			SlaveImpl s = (SlaveImpl) item.s;
			System.out.println("Slave " + s.getUuid() + "start attack");
			s.startSubAttack(ciphertext, knowntext, initialwordindex, finalwordindex, attackNumber, this);
			item.attacks.put(attackNumber, new AttackInfo(initialwordindex, finalwordindex));
			Timer timer = new Timer();
			timer.scheduleAtFixedRate(new CheckerTasker(k), 20000, 20000);
		} catch (RemoteException e) {
			synchronized (registeredSlaves) {
				registeredSlaves.remove(k);
			}
		}
	}
	
	@Override
	public Guess[] attack(byte[] ciphertext, byte[] knowntext) throws RemoteException {
		//HashMap<UUID, SlaveInfo> cpy;
		ArrayList<SlaveInfo> cpy;
		synchronized (registeredSlaves) {
			//cpy = new HashMap<UUID, SlaveInfo>(registeredSlaves);
			cpy = new ArrayList<SlaveInfo>(registeredSlaves.values());
		}
		
		int index_size = this.dict_size/cpy.size();
		int count = 1;
		
		for (SlaveInfo s : cpy) {
			SlaveImpl tmp = (SlaveImpl) s.s;
			
			requestAttack(tmp.getUuid(), s, ciphertext, knowntext, index_size*(count-1), index_size*count, 0);
			count++;
		}
		return null;
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
