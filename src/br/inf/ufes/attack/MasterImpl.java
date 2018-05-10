package br.inf.ufes.attack;

import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.inf.ufes.ppd.Guess;
import br.inf.ufes.ppd.Master;
import br.inf.ufes.ppd.Slave;

public class MasterImpl implements Master {
	
	private Map<UUID, Slave> registeredSlaves;
	private List<Guess> guesses;

	@Override
	public void addSlave(Slave s, String slaveName, UUID slavekey) throws RemoteException {
		// TODO tratar o resto da função
		synchronized (registeredSlaves) {
			registeredSlaves.put(slavekey, s);
		}
	}

	@Override
	public void removeSlave(UUID slaveKey) throws RemoteException {
		// TODO tratar o resto da função
		synchronized (registeredSlaves) {
			registeredSlaves.remove(slaveKey);
		}

	}

	@Override
	public void foundGuess(UUID slaveKey, int attackNumber, long currentindex, Guess currentguess)
			throws RemoteException {
		// TODO Auto-generated method stub

	}

	@Override
	public void checkpoint(UUID slaveKey, int attackNumber, long currentindex) throws RemoteException {
		// TODO Auto-generated method stub

	}

	@Override
	public Guess[] attack(byte[] ciphertext, byte[] knowntext) throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

}
