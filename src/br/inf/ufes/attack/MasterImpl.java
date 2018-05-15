package br.inf.ufes.attack;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.inf.ufes.ppd.Guess;
import br.inf.ufes.ppd.Master;
import br.inf.ufes.ppd.Slave;

public class MasterImpl implements Master, Serializable {
	
	private Map<UUID, Slave> registeredSlaves;
	private List<Guess> guesses;

	public MasterImpl() {
		registeredSlaves = new HashMap<UUID, Slave>();
		guesses = new ArrayList<>();
	}
	
	@Override
	public void addSlave(Slave s, String slaveName, UUID slavekey) throws RemoteException {
		synchronized (registeredSlaves) {
			registeredSlaves.put(slavekey, s);
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

	}

	@Override
	public void checkpoint(UUID slaveKey, int attackNumber, long currentindex) throws RemoteException {
		// TODO Auto-generated method stub

	}

	@Override
	public Guess[] attack(byte[] ciphertext, byte[] knowntext) throws RemoteException {
		Collection<Slave> cpy;
		synchronized (registeredSlaves) {
			cpy = registeredSlaves.values();
		}
		
		for(Slave s: cpy) {
			try {
				s.startSubAttack(ciphertext, knowntext, 0, 0, 0, this);	
			} catch (RemoteException e) {
				
			}
			
		}
		return null;
	}

}
