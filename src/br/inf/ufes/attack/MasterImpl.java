package br.inf.ufes.attack;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import br.inf.ufes.ppd.Guess;
import br.inf.ufes.ppd.Master;
import br.inf.ufes.ppd.Slave;

public class MasterImpl implements Master, Serializable {
	
	private Map<UUID, SlaveInfo> registeredSlaves;
	private List<Guess> guesses;

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
			registeredSlaves.remove(slaveKey); //TODO: gerenciar os ataques desse escravo e os indices de ataque dele
		}
	}

	@Override
	public void foundGuess(UUID slaveKey, int attackNumber, long currentindex, Guess currentguess)
			throws RemoteException {
		synchronized (registeredSlaves) {
			SlaveInfo i = registeredSlaves.get(slaveKey);
			i.alive = true;
			i.attacks.get(attackNumber); //TODO: fazer e gerenciar uma lista dos indices usados e ja visitados	
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
		HashMap<UUID, SlaveInfo> cpy;
		synchronized (registeredSlaves) {
			cpy = new HashMap<UUID, SlaveInfo>(registeredSlaves);
		}
		
		cpy.forEach((k, s) -> requestAttack(k, s, ciphertext, knowntext, 0, 0, 0)); //TODO: gerenciar os indices e o attacknumber
			
		return null;
	}
	
	//TODO main
	/*
	 * main fica passivo esperando o client pedir o ataque
	 * divide o numero de palavras no dict pelo total de escravo
	 * loop para fazer chamada para os escravos
	 * pode fazer um método diferente, podemos usar o mesmo metodo para quando um escravo morrer e fazer a redistribuição do trabalho 
	 * entre os escravos
	 * 
	 * 
	 * enquanto passivo esperando o attack ele tem que verificar o hashmap para verificar quem não está vivo
	 * 
	 * esse loop tem que rodar de 20 em 20 sec - nao conseguir implementar os bagulhos de timer, por conta de sync/static method 
	 * e os carai que eu me perco
	 * for (SlaveInfo slave: registeredSlaves) {
	 * 	if (!slave.alive) {
	 * 		//alive == false entao remove o slave
	 * 		//pega as informações dos indices que o escravo estava trabalhando
	 * 		//verificar se o checkpoint/foundguess é diff de -1 e pode retornar só ele e o 
	 * 	} else {
	 * 		//seta alive para falso, o slave tem que se re-registrar e isso torna a flag para true
	 *		//os metodos checkpoint e foundguess tambem tornam a flag para true 
	 *  }
	 * }
	 * 
	 * 
	 * 
	 * */
	
}
