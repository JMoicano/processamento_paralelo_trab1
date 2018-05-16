package br.inf.ufes.attack;

import java.io.Serializable;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.management.timer.TimerMBean;

import br.inf.ufes.ppd.Master;
import br.inf.ufes.ppd.Slave;
import br.inf.ufes.ppd.SlaveManager;

public class SlaveImpl implements Slave, Serializable {

	private Master mestre;
	private UUID uuid;
	private long initialindex;
	private long finalindex;
	
	public SlaveImpl() {
		this.initialindex = -1;
		this.finalindex = -1;
		try {
			Registry registry = LocateRegistry.getRegistry("127.0.0.1"); // opcional: host
			mestre = (Master) registry.lookup("mestre");
		    if(mestre == null) System.err.println("Server ready");
		    uuid = UUID.randomUUID();
			
			mestre.addSlave(this, "qualquerCoisa", uuid);
			System.out.println("Slave uuid: " + uuid);
		} catch (RemoteException e) {
			System.err.println("Server exception: " + e.toString()); 
			e.printStackTrace();
		} catch (NotBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Timer timer = new Timer();
		RegisterTask runn = new RegisterTask();
		runn.setSlave(this);
		timer.scheduleAtFixedRate(runn, 30000, 30000);
		
	}
	
	public void setInitialIndex(long i) {
		this.initialindex = i;
	}
	
	public long getInitialIndex() {
		return this.initialindex;
	}
	
	public void setFinalIndex(long i) {
		this.finalindex = i;
	}
	
	public long getFinalIndex() {
		return this.finalindex;
	}
	
	public Master getMestre() {
		return mestre;
	}

	public UUID getUuid() {
		return uuid;
	}

	@Override
	public void startSubAttack(byte[] ciphertext, byte[] knowntext, long initialwordindex, long finalwordindex,
			int attackNumber, SlaveManager callbackinterface) throws RemoteException {
		// TODO Auto-generated method stub
		System.out.println("Starting new attack.");
		//callbackinterface.foundGuess(uuid, attackNumber, 0, null);
		

	}
	
	
	private static class RegisterTask extends TimerTask{
		static SlaveImpl s;
		static int count = 0;
		
		public void setSlave(SlaveImpl s) {
			this.s = s;
		}
			
		public void run() {
			try {
				s.getMestre().addSlave(s, "qualquerCoisa", s.getUuid());
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    }

	}

}
