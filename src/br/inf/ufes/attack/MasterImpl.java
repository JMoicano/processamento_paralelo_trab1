package br.inf.ufes.attack;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
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
	
	public class SlaveInfo {
		Slave s; // referencia pro escravo
		long indexNow; // pode usar como indice atual que esta - inicia com o que o mestre mandar e é atualizado nos foundguess/checkpoint/reregistro
		long indexEnd; // ultimo indice a ser testado pelo escravo
		boolean alive; // flag para verificar se o escravo esta vivo ou nao
		
		SlaveInfo(Slave s, long start, long end, boolean flag) {
			this.s = s;
			this.indexNow = start;
			this.indexEnd = end;
			this.alive = flag;
		}
	}
	
	public MasterImpl() {
		registeredSlaves = new HashMap<UUID, SlaveInfo>();
		guesses = new ArrayList<>();
	}
	
	@Override
	public void addSlave(Slave s, String slaveName, UUID slavekey) throws RemoteException {
		synchronized (registeredSlaves) {
			//TODO verificar se o slavekey ja e cadastrada, e só mexer na flag
			System.out.println("addSlave request " + slavekey);
			//System.out.println(s);
			registeredSlaves.put(slavekey, 
					new SlaveInfo(s, // slaveref
							((SlaveImpl) s).getInitialIndex(), //initindex 
							((SlaveImpl) s).getFinalIndex(), //finalindex
							true)); //alive_flag
		}
	}

	@Override
	public void removeSlave(UUID slaveKey) throws RemoteException {
		synchronized (registeredSlaves) {
			registeredSlaves.remove(slaveKey);
		}
	}

	@Override
	public void foundGuess(UUID slaveKey, int attackNumber, long currentindex, Guess currentguess)
			throws RemoteException {
		// TODO Auto-generated method stub
		// ping?pong!
		registeredSlaves.get(slaveKey).alive = true;
	}

	@Override
	public void checkpoint(UUID slaveKey, int attackNumber, long currentindex) throws RemoteException {
		// TODO Auto-generated method stub
		// ping?pong!
		registeredSlaves.get(slaveKey).alive = true;

	}

	@Override
	public Guess[] attack(byte[] ciphertext, byte[] knowntext) throws RemoteException {
		Collection<SlaveInfo> cpy;
		synchronized (registeredSlaves) {
			cpy = registeredSlaves.values();
		}
		
		for(SlaveInfo item: cpy) {
			try {
				// nao sei se cria um novo slave ou se pega apenas a referencia para começar o ataque
				SlaveImpl s = new SlaveImpl();
				s = (SlaveImpl) item.s;
				System.out.println("Slave " + s.getUuid() + "start attack");
				s.startSubAttack(ciphertext, knowntext, 0, 0, 0, this);	
			} catch (RemoteException e) {
				
			}
			
		}
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
