package br.inf.ufes.attack;

import br.inf.ufes.ppd.Slave;

public class SlaveClient {

	public static void main(String[] args) {
		
		Slave s = new SlaveImpl(args[1], args[2]);

	}

}
