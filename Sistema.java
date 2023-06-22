import java.util.*;
import java.util.concurrent.Semaphore;



public class Sistema {
	
	// -------------------------------------------------------------------------------------------------------
	// --------------------- H A R D W A R E - definicoes de HW ---------------------------------------------- 
	public Semaphore semaforo_escalonador = new Semaphore(1);
	public Semaphore semaforo_cpu = new Semaphore(0);
	public Semaphore semaforo_traduzir = new Semaphore(1);
	public Semaphore semaforo_io = new Semaphore(1);
	public Semaphore semaforo_io_var = new Semaphore(0);
	public Semaphore semaforo_io_controle = new Semaphore(0);
	
	public Semaphore semaforo_listaRodando = new Semaphore(1);
	public Semaphore semaforo_listaPronto = new Semaphore(1);
	public Semaphore semaforo_listaBloqueados = new Semaphore(1);
	public Semaphore semaforo_fila_console = new Semaphore(1);
	
	public int limite_rep = 10000;
    public int tamanhoMemoria = 1024;
	public int top_mem = tamanhoMemoria - 1;
	public int temp_delta = 5;
	
	// -------------------------------------------------------------------------------------------------------
	// --------------------- M E M O R I A -  definicoes de opcode e palavra de memoria ---------------------- 
	
	public class Word { 	// cada posicao da memoria tem uma instrucao (ou um dado)
		public Opcode opc; 	//
		public int r1; 		// indice do primeiro registrador da operacao (Rs ou Rd cfe opcode na tabela)
		public int r2; 		// indice do segundo registrador da operacao (Rc ou Rs cfe operacao)
		public int p; 		// parametro para instrucao (k ou A cfe operacao), ou o dado, se opcode = DADO

		public Word(Opcode _opc, int _r1, int _r2, int _p) {  
			opc = _opc;   r1 = _r1;    r2 = _r2;	p = _p;
		}
	}
    // -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
    // --------------------- C P U  -  definicoes da CPU ----------------------------------------------------- 

		
	public enum Opcode {
		DATA, ___,		    // se memoria nesta posicao tem um dado, usa DATA, se nao usada ee NULO ___
		JMP, JMPI, JMPIG, JMPIL, JMPIE,  JMPIM, JMPIGM, JMPILM, JMPIEM, STOP,   // desvios e parada
		ADDI, SUBI,  ADD, SUB, MULT,         // matematicos
		LDI, LDD, STD, LDX, STX, SWAP, TRAP;        // movimentacao
	}

	public class CPU extends Thread{
		
		private int pc; 			// program counter,
		private Word ir; 			// instruction register,
		private int[] reg;       	// registradores da CPU
		private int interrupcao = -1;
		public boolean interrupcao_io = false;
		
		public boolean controle = false;
		private Word[] m;   // CPU acessa MEMORIA, guarda referencia 'm' a ela. memoria nao muda. É sempre a mesma.
		public int cont = 0;

		private List<Integer> paginasPrograma; 
		int id;
		private GM gerenteMemoria = new GM();
			
		public CPU(Word[] _m) {     // ref a MEMORIA e interrupt handler passada na criacao da CPU
			m = _m; 				// usa o atributo 'm' para acessar a memoria.
			reg = new int[10]; 		// aloca o espaço dos registradores
		}

		public void setContext(int _pc, ArrayList<Integer> paginas, int _id, int[] _reg) {  
			pc = _pc; // limite e pc deve ser zero
			paginasPrograma = paginas;
			id = _id;
			reg = _reg;                                              
		}
	
		public int getReg(int regNum){
			int valorReg=0;
			valorReg=reg[regNum];
			return valorReg;
		}
		
		public void setMem(int memNum, int valor){
			m[traduzir(memNum)].p=valor;
		}
		
		public int getMem(int pos){
				int valorMem=0;
				valorMem=m[traduzir(pos)].p;
				return valorMem;
		}

		public void run() { 		// execucao da CPU supoe que o contexto da CPU, visto acima, esteja setado corretamente
			
			while (true) { 			// ciclo de instrucoes. acaba cfe instrucao, olhar cada case.
				// FETCH
				try{semaforo_cpu.acquire();
				}catch(InterruptedException ie){}
					
					controle=false;
				if (cont!=temp_delta){
					try{Thread.sleep(500); // usado para que possamos ver a execução, se quiser que vá mais rápido só comentar
					}catch(InterruptedException ie){}
					
					cont ++;
					ir = m[traduzir(pc)]; 	// busca posicao da memoria apontada por pc, guarda em ir. 
					
				// EXECUTA INSTRUCAO NO ir
					switch (ir.opc) { // para cada opcode, sua execução

						case LDI: // Rd <- k
							reg[ir.r1] = ir.p;
							pc++;
							break;

						case STD: // [A] <- Rs
							if (ir.p < 0 || ir.p > top_mem || traduzir(ir.p)==-1) interrupcao = 1;
							else{
								m[traduzir(ir.p)].opc = Opcode.DATA;
							    m[traduzir(ir.p)].p = reg[ir.r1];
							    pc++;
							}
						break;

						case ADD: // Rd <- Rd + Rs
							if(reg[ir.r1] + reg[ir.r2] > limite_rep || reg[ir.r1] + reg[ir.r2] < -limite_rep) interrupcao = 3;
							else{
								reg[ir.r1] = reg[ir.r1] + reg[ir.r2];
								pc++;
							}
							break;

						case MULT: // Rd <- Rd * Rs
							if(reg[ir.r1] * reg[ir.r2] > limite_rep || reg[ir.r1] * reg[ir.r2] < -limite_rep) interrupcao = 3;
							else{
								reg[ir.r1] = reg[ir.r1] * reg[ir.r2];
								pc++;
							}
							break;

						case ADDI: // Rd<- Rd + k
							if(reg[ir.r1] + reg[ir.p] > limite_rep || reg[ir.r1] + reg[ir.p] < -limite_rep) interrupcao = 3;
							else{
								reg[ir.r1] = reg[ir.r1] + ir.p;
								pc++;
							}
							break;

						case STX: // [Rd] <- Rs
                  			if (reg[ir.r1] < 0 || reg[ir.r1] > top_mem ||traduzir(reg[ir.r1])==-1) interrupcao = 1;
							else{
								m[traduzir(reg[ir.r1])].opc = Opcode.DATA;      
							    m[traduzir(reg[ir.r1])].p = reg[ir.r2];          
								pc++;
							}
							break;

						case SUB: // Rd <- Rd - Rs
							if(reg[ir.r1] - reg[ir.r2] > limite_rep || reg[ir.r1] - reg[ir.r2] < -limite_rep) interrupcao = 3;
							else{
								reg[ir.r1] = reg[ir.r1] - reg[ir.r2];
								pc++;
							}
							break;
              
						case SUBI: // Rd <- Rd - k
              				if(reg[ir.r1] - reg[ir.p] > limite_rep || reg[ir.r1] - reg[ir.p] < -limite_rep) interrupcao = 3;
							else{
								reg[ir.r1] = reg[ir.r1] - ir.p;
								pc++;
							}
							break;

						case JMP: //  PC <- k
							pc = ir.p;
						    break;
						
						case JMPIG: // If Rc > 0 Then PC <- Rs Else PC <- PC +1
							if (reg[ir.r2] > 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;

						case JMPIE: // If Rc = 0 Then PC <- Rs Else PC <- PC +1
							if (reg[ir.r2] == 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;
            
						case JMPI: //  PC <- Rs
							pc = reg[ir.r1];
							break;

						case JMPIL: // If Rc < 0 Then PC <- Rs Else PC <- PC +1
							if (reg[ir.r2] < 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;

						case JMPIM: //  PC <- [A]
							if (ir.p < 0 || ir.p > top_mem || traduzir(ir.p)==-1) interrupcao = 1;
							else{
								m[traduzir(ir.p)].opc = Opcode.DATA;
								pc = m[traduzir(ir.p)].p;
							}
							break;

						case LDX:
							if (reg[ir.r2] < 0 || reg[ir.r2] > top_mem ||traduzir(reg[ir.r2])==-1) interrupcao = 1;
							else{
								m[traduzir(reg[ir.r2])].opc = Opcode.DATA;
								reg[ir.r1] = m[traduzir(reg[ir.r2])].p;
								pc++;
							}
							break;

						case SWAP: // T <- Ra   Ra <- Rb   Rb <- T
								int T;
								T = reg[ir.r1];
								reg[ir.r1] = reg[ir.r2];
								reg[ir.r2] = T;
								pc++;
								break; 
								
						case JMPIGM:
              				if (ir.p < 0 || ir.p > top_mem ||traduzir(ir.p)==-1) interrupcao = 1;
							else{
								if (reg[ir.r2] > 0) {
									pc = m[traduzir(ir.p)].p;
								} else {
									pc++;
								}
							}
							break;
							
						case JMPILM:
              				if (ir.p < 0 || ir.p > top_mem || traduzir(ir.p)==-1) interrupcao = 1;
							else{
								if (reg[ir.r2] < 0) {
									pc = m[traduzir(ir.p)].p;
								} else {
									pc++;
								}
							}
							break;

						case JMPIEM:
              				if (ir.p < 0 || ir.p > top_mem || traduzir(ir.p)==-1) interrupcao = 1;
							else{
								if (reg[ir.r2] == 0) {
									pc = m[traduzir(ir.p)].p;
								} else {
									pc++;
								}
							}
							break;

						case LDD:
              				if (ir.p < 0 || ir.p > top_mem || traduzir(ir.p)==-1) interrupcao = 1;
							else{
								reg[ir.r1] = m[traduzir(ir.p)].p;
								pc++;
							}
							break;
						
						case TRAP:
							if(traduzir(reg[9])==-1) interrupcao = 1;
							else{
								pc++;
								gp.fila_rodando.get(0).pc=pc;
								controle = true;
								monitor.chamada_sis(); // bota o processo atual na fila de bloqueados
							}
							break;
							
						case STOP:
							interrupcao=4;
							break;
						
						default:
							interrupcao = 2;
					}
			}
			else if (cont==5){ interrupcao = 6; // processo que estava rodando, vai para fila de espera e vem outro no lugar
				cont=0;
			}
			
			if(controle == false){  // se nao teve chamada de sistema, continua normal
			gp.fila_rodando.get(0).pc=pc;
				semaforo_cpu.release();
			}
	
			if (interrupcao!=-1){ // se houve interrupção, vai resolver
					monitor.dec_interrupcao(interrupcao);
					interrupcao = -1;
				}
							
		}
	}

		private int traduzir(int pc){
			boolean validar = true;
			int tamPag = gerenteMemoria.tamPag;
			int indice = pc / tamPag;
			int offset = pc % tamPag;
			int pc_traduzido = -1;
			
			try {
				paginasPrograma.get(indice);
			} 
			catch (Exception e) {
				interrupcao = 1;
				validar = false;
			}
	
			if(validar==true){
				pc_traduzido = (paginasPrograma.get(indice) * tamPag) + offset;
			}
			
			return pc_traduzido;
		}
		
	}
    // ------------------ C P U - fim ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	
    // ------------------- V M  - constituida de CPU e MEMORIA -----------------------------------------------
	
	public class VM {
		public int tamMem;    
        public Word[] m;     
        public CPU cpu;    

        public VM(){    
	     // memória
  		 	 tamMem = tamanhoMemoria;
			 m = new Word[tamMem]; // m e a memoria
			 for (int i=0; i<tamMem; i++) { m[i] = new Word(Opcode.___,-1,-1,-1); };
	  	 // cpu
			 cpu = new CPU(m);   // cpu acessa memória
	    }	
	}
    // ------------------- V M  - fim ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

    // --------------------H A R D W A R E - fim -------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// ------------------- S O F T W A R E - inicio ----------------------------------------------------------

		// ---------------- M O N I T O R ---------------------------------------------------------------------
	public class Monitor{
			public void cria(Word[] p){
					gp.criarProcesso(p);
			}

			public void dump(Word w) {
				System.out.print("[ "); 
				System.out.print(w.opc); System.out.print(", ");
				System.out.print(w.r1);  System.out.print(", ");
				System.out.print(w.r2);  System.out.print(", ");
				System.out.print(w.p);  System.out.println("  ] ");
			}
			
			public void dump(int ini, int fim) {
				for (int i = ini; i <= fim; i++) {
					System.out.print(i); System.out.print(":  ");  dump(vm.m[i]);
				}
			}

            public void dumpGeral(int id){
                int indice = gp.getIndiceProcesso(id);
                if(indice != -1){
					System.out.println("\n*******Inicio Dump*******");
					System.out.println("  Processo ID: " + gp.fila_prontos.get(indice).id);
					System.out.println("	pc: " + gp.fila_prontos.get(indice).pc);
					for(int i=0; i< (gp.fila_prontos.get(indice).reg.length); i++){
						System.out.println("	 reg["+i+"] = "+gp.fila_prontos.get(indice).reg[i]);
					}
					
					ArrayList<Integer> paginasProcesso = (gp.fila_prontos.get(indice).getPaginasAlocadas());
					System.out.print("   	Frames alocados: ");
					for(int i=0; i<paginasProcesso.size(); i++){
							System.out.print(" "+paginasProcesso.get(i));
					}
					System.out.println("\n 	Quantidade: " + paginasProcesso.size()+"\n");
					for(int i=0; i < paginasProcesso.size(); i++){
						int inicio = paginasProcesso.get(i)*(gm.tamFrame);
						int fim = (((paginasProcesso.get(i)+1)*(gm.tamFrame))-1);
						System.out.println("inicio " + inicio + " fim " + fim);
						dump(inicio,fim);
					}
					System.out.println("*******Final Dump*******");
                }
            }
            
            public void listarProcessos(){
				System.out.println("Processos: ");
					for(int i=0; i<gp.fila_prontos.size(); i++){
						System.out.println("	ID: " + gp.fila_prontos.get(i).id);
					}
					
					System.out.println("Processos em execucao: ");
					for(int i=0; i<gp.fila_rodando.size(); i++){
						System.out.println("	ID: " + gp.fila_rodando.get(i).id);
					}
					
					System.out.println("Processos bloqueados: ");
					for(int i=0; i<gp.fila_bloqueados.size(); i++){
						System.out.println("	ID: " + gp.fila_bloqueados.get(i).id);
					}
					
			}
				
			public void dec_interrupcao(int trat_inter) {
				
				switch (trat_inter) {
					case 1: //endereço inválido
					 
					 try{semaforo_cpu.acquire();
						}catch(InterruptedException ie){}
						try{semaforo_listaRodando.acquire();
						}catch(InterruptedException ie){}
						gm.desalocar(gp.fila_rodando.get(0).paginasAlocadas);
						gp.fila_rodando.remove(0);
						System.out.println("Interrupção detectada: Endereço inválido ");
						semaforo_listaRodando.release();
						semaforo_escalonador.release();
					 
						
						break;
					case 2: //instrução inválida
						
						try{semaforo_cpu.acquire();
						}catch(InterruptedException ie){}
						try{semaforo_listaRodando.acquire();
						}catch(InterruptedException ie){}
						gm.desalocar(gp.fila_rodando.get(0).paginasAlocadas);
						gp.fila_rodando.remove(0);
						System.out.println("Interrupção detectada: Instrução inválida");
						semaforo_listaRodando.release();
						semaforo_escalonador.release();
						
						break;
					case 3: //overflow
					  
					   try{semaforo_cpu.acquire();
						}catch(InterruptedException ie){}
						try{semaforo_listaRodando.acquire();
						}catch(InterruptedException ie){}
						gm.desalocar(gp.fila_rodando.get(0).paginasAlocadas);
						gp.fila_rodando.remove(0);
						System.out.println("Interrupção detectada: Overflow em operação matemática");
						semaforo_listaRodando.release();
						semaforo_escalonador.release();
					  
						break;
					case 4: //stop
					  
					  try{semaforo_cpu.acquire();
						}catch(InterruptedException ie){}
						try{semaforo_listaRodando.acquire();
						}catch(InterruptedException ie){}
						gm.desalocar(gp.fila_rodando.get(0).paginasAlocadas);
						gp.fila_rodando.remove(0);
						System.out.println("Interrupção detectada: Fim de programa");
						semaforo_listaRodando.release();
						semaforo_escalonador.release();
						
						break;
					case 5: //chamada de sistema errada   
					
					try{semaforo_cpu.acquire();
						}catch(InterruptedException ie){}
						try{semaforo_listaRodando.acquire();
						}catch(InterruptedException ie){}
						gm.desalocar(gp.fila_rodando.get(0).paginasAlocadas);
						gp.fila_rodando.remove(0);
						System.out.println("Interrupção detectada: Chamada de sistema invalida   ");
						semaforo_listaRodando.release();
						semaforo_escalonador.release();
					
					case 6: //escalonar
						try{semaforo_cpu.acquire();
						}catch(InterruptedException ie){}
						semaforo_escalonador.release();
						break;        
					}	
			}
			
			public void depuracao_IO(){ // funcao que realiza a remoção do elemento da fila de bloqueados
				
				try{semaforo_escalonador.acquire();
				}catch(InterruptedException ie){}
				try{semaforo_listaPronto.acquire();
				}catch(InterruptedException ie){}
				
				try{semaforo_listaBloqueados.acquire();
				}catch(InterruptedException ie){}
				
				
				gp.fila_prontos.add(gp.fila_bloqueados.get(0));
				gp.fila_bloqueados.remove(0);
				vm.cpu.interrupcao_io=false;
				
				semaforo_escalonador.release();
				semaforo_listaBloqueados.release();
				semaforo_listaPronto.release();
				semaforo_io.release();
			}
				
			public void chamada_sis(){ // adiciona processo na fila de bloqueados quando houver chama de sistema
				try{semaforo_listaBloqueados.acquire();
				}catch(InterruptedException ie){}
				try{semaforo_fila_console.acquire();
				}catch(InterruptedException ie){}
				
				gp.fila_bloqueados.add(gp.fila_rodando.get(0));
				gp.fila_console.add(gp.fila_rodando.get(0));
				gp.fila_rodando.remove(0);
				semaforo_io_controle.release();
				semaforo_listaBloqueados.release();
				semaforo_fila_console.release();
				semaforo_escalonador.release();			
			}
	}

	// -------------------------- FIM MONITOR   --------------------
	public class GM{
		public int tamMemoria = tamanhoMemoria;
		public int tamPag = 16;
		//public int nroPaginas = tamMemoria/tamPag;
		public int tamFrame = tamPag;
		public int nroFrames = tamMemoria/tamFrame;
		public boolean[] framesLivres;

		public GM(){ 
			framesLivres = iniciaFramesLivres();
			
		}

		private boolean[] iniciaFramesLivres() {
			framesLivres = new boolean[nroFrames];
			for (int i = 0; i < framesLivres.length; i++) {
				framesLivres[i] = true;
			}
			return framesLivres;
		}
	
		public boolean consegueAlocar(int numeroPalavras) {
			int framesNecessarios = 0;
	
			if (numeroPalavras % tamFrame == 0) {
				framesNecessarios = ((numeroPalavras / tamFrame));
			}
			else {
				framesNecessarios = ((numeroPalavras / tamFrame) + 1);
			}
	
			int qtdFramesLivres = 0;
			for (int i = 0; i < framesLivres.length; i++) {
				if (framesLivres[i]) {
					qtdFramesLivres++;
				}
			}
			System.out.println("frames necessarios: " + framesNecessarios + ", frames livres: " + qtdFramesLivres);
			return (framesNecessarios <= qtdFramesLivres);
		}
	
		public ArrayList<Integer> alocar(Word[] p) {
			int framesNecessarios = 0;
	
			if (p.length % tamFrame == 0) {
				framesNecessarios = ((p.length / tamFrame));
			}
			else {
				framesNecessarios = ((p.length / tamFrame) + 1);
			}
			
			int qtdNovosFramesOcupados = 0;
			ArrayList<Integer> paginas = new ArrayList<>();
			
			for (int f = 0; f < framesLivres.length; f++) {
                if(qtdNovosFramesOcupados == framesNecessarios) break; 
				if (framesLivres[f] == true) {
					System.out.println("frame utilizado: "+f);
					framesLivres[f] = false;
					qtdNovosFramesOcupados++;
					paginas.add(f);
				}
			}
			return paginas;
		}
		
		public void limpar(ArrayList<Integer> paginasAlocadas){
			for (int j = 0; j<paginasAlocadas.size(); j++){
                for (int k = (paginasAlocadas.get(j)*tamFrame); k < ((paginasAlocadas.get(j)*tamFrame)+tamFrame) ; k++){
                        vm.m[k] = new Word(Opcode.___, -1, -1, -1);
                }
            }
			
		}

		public void desalocar(ArrayList<Integer> paginasAlocadas) {
            for (int j = 0; j<paginasAlocadas.size(); j++){
                framesLivres[paginasAlocadas.get(j)]=true;
            }
        }
        
        public boolean alocarPagina(ArrayList<Integer> alocados){
			boolean livre=false;
			int frame=0;		
			for(int i=0; i<framesLivres.length; i++){
				if(framesLivres[i] == true){
						livre = true;
						framesLivres[i] = false;
						frame=i;
						break;
				}
			}
			
			if(livre==true) alocados.add(frame);
			
			return livre;
			
		}
	}

	public class PCB{
		public int id;
		public int pc=0;
		public int reg[];
		public ArrayList<Integer> paginasAlocadas;

		public PCB(int identificador, ArrayList<Integer> pagAlocadas) {
			paginasAlocadas = pagAlocadas;
			id = identificador;
			reg = new int[10];
		}

		public ArrayList<Integer> getPaginasAlocadas() {
			return paginasAlocadas;
		}
	}

	public class GP{
		public ArrayList<PCB> fila_prontos; //lista prontos
		public ArrayList<PCB> fila_rodando;
		public ArrayList<PCB> fila_bloqueados;
		public ArrayList<PCB> fila_console;
		public int idProcesso = 0;

		public GP() {
			fila_prontos = new ArrayList<>();
			fila_rodando = new ArrayList<>();
			fila_bloqueados = new ArrayList<>();
			fila_console = new ArrayList<>();
		}

		public boolean criarProcesso(Word[] p) {            
			PCB controleProcesso;
			if (gm.consegueAlocar(p.length)) {
                ArrayList<Integer> paginas = gm.alocar(p);
				controleProcesso = new PCB(idProcesso, paginas);
				idProcesso++;
                carga(p, paginas);
                
                try{semaforo_listaPronto.acquire();
				}catch(InterruptedException ie){}
				fila_prontos.add(controleProcesso);
				semaforo_listaPronto.release();
			
			} else {
				System.out.println("Sem espaço na memória para criar o processo de ID:" + idProcesso);
				return false;
			}
			System.out.println("Processo criado com sucesso, ID: " + (idProcesso-1));
			return true;
		}

		public int getIndiceProcesso(int identificador) {
			for(int i=0; i<fila_prontos.size(); i++){
                if(fila_prontos.get(i).id==identificador){
                    return i;
                }
            }
            
            System.out.println("Processo de ID: "+identificador+" nao encontrado");
            return -1;
		}

        public void carga(Word[] p, ArrayList<Integer> pagAlocadas) {    // significa ler "p" de memoria secundaria e colocar na principal "m"
            int posicao=0;
            gm.limpar(pagAlocadas);
            for (int j = 0; j<pagAlocadas.size(); j++){
                for (int k = (pagAlocadas.get(j)*gm.tamFrame); k < ((pagAlocadas.get(j)*gm.tamFrame)+gm.tamFrame) ; k++){
                    if(posicao < p.length){
                        vm.m[k].opc = p[posicao].opc;
                        vm.m[k].r1 = p[posicao].r1;
                        vm.m[k].r2 = p[posicao].r2;
                        vm.m[k].p = p[posicao].p;
                        posicao ++;
                    }
                    else break;
                }
            }
		}

	}
	
	public class Escalonador extends Thread{
	
		public Escalonador(){}
		
		public void run(){
			
			while(true){ // seção critica
				try{semaforo_escalonador.acquire();
				}catch(InterruptedException ie){}

				try{semaforo_listaPronto.acquire();
				}catch(InterruptedException ie){}
				try{semaforo_listaRodando.acquire();
				}catch(InterruptedException ie){}

				try{semaforo_listaBloqueados.acquire();
				}catch(InterruptedException ie){}

				if(gp.fila_prontos.size()>0 && gp.fila_rodando.size()>0){ // se tiver um elemento rodando e mais de um elemento na fila de prontos
					
					gp.fila_prontos.add(gp.fila_rodando.get(0));
					gp.fila_rodando.remove(0);
					
					gp.fila_rodando.add(gp.fila_prontos.get(0));
					gp.fila_prontos.remove(0);
					
					PCB rodar = gp.fila_rodando.get(0);
					vm.cpu.cont=0;
					vm.cpu.setContext(rodar.pc, rodar.paginasAlocadas, rodar.id, rodar.reg);
					semaforo_cpu.release();
					
				}
				
				else if(gp.fila_rodando.size()==0 && gp.fila_prontos.size()>0){ // se tiver nenhum elemento rodando e mais de um na fila de prontos

					gp.fila_rodando.add(gp.fila_prontos.get(0));
					gp.fila_prontos.remove(0);
					
					
					PCB rodar = gp.fila_rodando.get(0);
					vm.cpu.cont=0;
					vm.cpu.setContext(rodar.pc, rodar.paginasAlocadas, rodar.id, rodar.reg);
					semaforo_cpu.release();
					
				}
				
				else if(gp.fila_rodando.size()>0 && gp.fila_prontos.size()==0){ 	// se nao tiver nenhum pronto e um rodando 
					semaforo_cpu.release();
				}
				
				else if(gp.fila_rodando.size()==0 && gp.fila_prontos.size()==0){ // se tiver nada rodando e nenhum pronto
					semaforo_escalonador.release();
				}
				semaforo_listaPronto.release();
				semaforo_listaBloqueados.release();
				semaforo_listaRodando.release();
				// fim seção critica
			}
		}
	}
	
	public class Console extends Thread{
		public int var_io = -1;
		public Console(){
			var_io = -1;
		}
		
		public void run(){ // IO
			while(true){
				
				try{semaforo_io_controle.acquire();
				}catch(InterruptedException ie){}
				
				if(gp.fila_console.size()>0){
					Scanner leitura=new Scanner(System.in);
					//int var;
					int reg8 = gp.fila_console.get(0).reg[8];	
					
					
					if (reg8 == 1) { // QUANDO HOVUER IN
						int reg9 = gp.fila_console.get(0).reg[9];
						System.out.println("\n Processo de id: " + gp.fila_bloqueados.get(0).id + " aguardando dado");
						try{semaforo_io_var.acquire();
						}catch(InterruptedException ie){}
						vm.m[traduzir_con(reg9)].p=var_io;
						var_io=-1;
						gp.fila_console.remove(0);
						try{semaforo_io.acquire();
						}catch(InterruptedException ie){}
						vm.cpu.interrupcao_io=true;
						monitor.depuracao_IO();
					}
					else if (reg8 == 2) { // QUANDO HOUVER OUT
						int reg9 = gp.fila_console.get(0).reg[9];;
						int mem=vm.m[traduzir_con(reg9)].p;
						System.out.println("Trap do processo " + gp.fila_bloqueados.get(0).id + ": "+mem);
						gp.fila_console.remove(0);
						try{semaforo_io.acquire();
						}catch(InterruptedException ie){}
						vm.cpu.interrupcao_io=true;
						monitor.depuracao_IO();
					}
					else if(reg8 == 3){
						boolean resultado = gm.alocarPagina(gp.fila_console.get(0).paginasAlocadas);
						System.out.println("Trap Encerrado");
						if(resultado == true){
							gp.fila_console.get(0).reg[9]=1;
						} 
						else gp.fila_console.get(0).reg[9]=-1;
						
						gp.fila_console.remove(0);
						try{semaforo_io.acquire();
						}catch(InterruptedException ie){}
						vm.cpu.interrupcao_io=true;
						monitor.depuracao_IO();						
					}
				}
				
			}
		
		}
		
		private int traduzir_con(int pc){ // DMA
			int tamPag = gm.tamPag;
			int indice = pc / tamPag;
			int offset = pc % tamPag;
			int pc_traduzido = -1;
			
				pc_traduzido = (gp.fila_console.get(0).paginasAlocadas.get(indice) * tamPag) + offset;
			
			return pc_traduzido;
		}
	
	
	} 
		
	   // -------------------------------------------  
		


	// -------------------------------------------------------------------------------------------------------
    // -------------------  S I S T E M A --------------------------------------------------------------------

	public VM vm;
	public Monitor monitor;
	public static Programas progs;
	public GP gp;
	public GM gm;
	public Escalonador escalonador;
	public Console console;

    public Sistema(){
		 vm = new VM();
		 monitor = new Monitor();
		 progs = new Programas();
         gp = new GP();
         gm = new GM();
         escalonador = new Escalonador();
         console = new Console();
	}
	
	public void initSis(ArrayList<Word[]> listaProgramas){
		int op = 0;
		int tmp = 0;
		Scanner leitura=new Scanner(System.in);
		while(op!=-1){
				
				System.out.println("\n ******************************************");
				System.out.println("Selecione a operacao desejada:");
				System.out.println("	1 - Criar processo");
				System.out.println("	2 - Dump processo");
				System.out.println("	3 - Dum memoria");
				System.out.println("	4 - Executar processo");
				System.out.println("	5 - Desalocar processo");
				System.out.println("	6 - Listar processos");
				System.out.println("	7 - Inserir dado TRAP");
				System.out.println("	8 - Inserir todos os Processos");
				System.out.println("	9 - Sair");
				System.out.print("Operacao: ");
				
				op = leitura.nextInt();
		
			switch (op) {
						case 1:
							 boolean validar = true;
							 System.out.println("Selecine o programa para criar o processo: ");
							 System.out.println("	0 - fatorial");
							 System.out.println("	1 - progMinimo");
							 System.out.println("	2 - fibonacci10");
							 System.out.println("	3 - fatorialTRAP");
							 System.out.println("	4 - fibonacciTRAP");
							 System.out.println("	5 - PB");
							 System.out.println("	6 - PC");
							 System.out.println("	7 - Malloc");
							 System.out.println("	8 - STOP");
							 System.out.println("	9 - Chamada Sist. Inválida");
								tmp = leitura.nextInt();
							 
							try {
								listaProgramas.get(tmp);
							} 
							catch (Exception e) {
								validar = false;
								System.out.println("Programa invalido");
							}
							
							if(validar == true){
								monitor.cria(listaProgramas.get(tmp));
							} 
						
							break;
						case 2:
							monitor.listarProcessos();
							System.out.print("\nID processo para dump: ");
								tmp = leitura.nextInt();
							monitor.dumpGeral(tmp);
							break;
						case 3:
						  System.out.println("\n*******Dump memoria*******");
						  monitor.dump(0,(tamanhoMemoria-1));
						  System.out.println("**************************");
							break;
						case 4:
							System.out.print("INDISPONÍVEL");
							break;
						case 5:
							System.out.print("INDISPONÍVEL");
							break;
						case 6:  
							monitor.listarProcessos();
							break;
						case 7:
							try{semaforo_fila_console.acquire();
							}catch(InterruptedException ie){}
							if(gp.fila_console.size()>0){
							System.out.println("Insira o dado para o trap: ");
							console.var_io = leitura.nextInt();
							semaforo_io_var.release();
							semaforo_fila_console.release();
							}
							else System.out.println("Sem processos esperando dado");
						break;     
						case 8:
							for(int i=0;i<listaProgramas.size();i++){
								monitor.cria(listaProgramas.get(i));
							}
			}
		}                                                                                                                                                               
	}

    // -------------------  S I S T E M A - fim --------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------

    // -------------------------------------------------------------------------------------------------------
    // ------------------- instancia e testa sistema
	public static void main(String args[]) {
		ArrayList<Word[]> Lista_Prog = new ArrayList<>();
		Sistema s = new Sistema();
		s.vm.cpu.start();
		s.console.start();
		s.escalonador.start();
        System.out.println("Todos sistemas inicializados");
		Lista_Prog.add(progs.fatorial);
		Lista_Prog.add(progs.progMinimo);
		Lista_Prog.add(progs.fibonacci10);
		Lista_Prog.add(progs.fatorialTRAP);
		Lista_Prog.add(progs.fibonacciTRAP);
		Lista_Prog.add(progs.PB);
		Lista_Prog.add(progs.PC);
		Lista_Prog.add(progs.malloc);
		Lista_Prog.add(progs.inteStop);
		Lista_Prog.add(progs.inteChamSistInvalida);
		s.initSis(Lista_Prog);
	}
    // -------------------------------------------------------------------------------------------------------
    // --------------- TUDO ABAIXO DE MAIN É AUXILIAR PARA FUNCIONAMENTO DO SISTEMA 

   //  -------------------------------------------- programas a disposicao para copiar na memoria
   public class Programas {
	public Word[] fatorial = new Word[] {
				 // este fatorial so aceita valores positivos.   nao pode ser zero
											   // linha   coment
		  new Word(Opcode.LDI, 0, -1, 4),      // 0   	r0 é valor a calcular fatorial
		  new Word(Opcode.LDI, 1, -1, 1),      // 1   	r1 é 1 para multiplicar (por r0)
		  new Word(Opcode.LDI, 6, -1, 1),      // 2   	r6 é 1 para ser o decremento
		  new Word(Opcode.LDI, 7, -1, 8),      // 3   	r7 tem posicao de stop do programa = 8
		  new Word(Opcode.JMPIE, 7, 0, 0),     // 4   	se r0=0 pula para r7(=8)
		 new Word(Opcode.MULT, 1, 0, -1),     // 5   	r1 = r1 * r0
		  new Word(Opcode.SUB, 0, 6, -1),      // 6   	decrementa r0 1 
		  new Word(Opcode.JMP, -1, -1, 4),     // 7   	vai p posicao 4
		  new Word(Opcode.STD, 1, -1, 10),     // 8   	coloca valor de r1 na posição 10
		  new Word(Opcode.STOP, -1, -1, -1),   // 9   	stop
		  new Word(Opcode.DATA, -1, -1, -1) }; // 10   ao final o valor do fatorial estará na posição 10 da memória                                    
	 
	public Word[] progMinimo = new Word[] {
		 new Word(Opcode.LDI, 0, -1, 999), 		
		 new Word(Opcode.STD, 0, -1, 10), 
		 new Word(Opcode.STD, 0, -1, 11), 
		 new Word(Opcode.STD, 0, -1, 12), 
		 new Word(Opcode.STD, 0, -1, 13), 
		 new Word(Opcode.STD, 0, -1, 14), 
		 new Word(Opcode.STOP, -1, -1, -1) };

	public Word[] fibonacci10 = new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
		 new Word(Opcode.LDI, 1, -1, 0), 
		 new Word(Opcode.STD, 1, -1, 20),   
		 new Word(Opcode.LDI, 2, -1, 1),
		 new Word(Opcode.STD, 2, -1, 21),  
		 new Word(Opcode.LDI, 0, -1, 22),  
		 new Word(Opcode.LDI, 6, -1, 6),
		 new Word(Opcode.LDI, 7, -1, 31),  
		 new Word(Opcode.LDI, 3, -1, 0), 
		 new Word(Opcode.ADD, 3, 1, -1),
		 new Word(Opcode.LDI, 1, -1, 0), 
		 new Word(Opcode.ADD, 1, 2, -1), 
		 new Word(Opcode.ADD, 2, 3, -1),
		 new Word(Opcode.STX, 0, 2, -1), 
		 new Word(Opcode.ADDI, 0, -1, 1), 
		 new Word(Opcode.SUB, 7, 0, -1),
		 new Word(Opcode.JMPIG, 6, 7, -1), 
		 new Word(Opcode.STOP, -1, -1, -1), 
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),   // POS 20
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1) }; // ate aqui - serie de fibonacci ficara armazenada
	 
	public Word[] fatorialTRAP = new Word[] {
		new Word(Opcode.LDI, 0, -1, 7),// numero para colocar na memoria
		new Word(Opcode.STD, 0, -1, 50),
		new Word(Opcode.LDD, 0, -1, 50),
		new Word(Opcode.LDI, 1, -1, -1),
		new Word(Opcode.LDI, 2, -1, 13),// SALVAR POS STOP
		new Word(Opcode.JMPIL, 2, 0, -1),// caso negativo pula pro STD
		new Word(Opcode.LDI, 1, -1, 1),
		new Word(Opcode.LDI, 6, -1, 1),
		new Word(Opcode.LDI, 7, -1, 13),
		new Word(Opcode.JMPIE, 7, 0, 0), //POS 9 pula pra STD (Stop-1)
		new Word(Opcode.MULT, 1, 0, -1),
		new Word(Opcode.SUB, 0, 6, -1),
		new Word(Opcode.JMP, -1, -1, 9),// pula para o JMPIE
		new Word(Opcode.STD, 1, -1, 18),
		new Word(Opcode.LDI, 8, -1, 2),// escrita
		new Word(Opcode.LDI, 9, -1, 18),//endereco com valor a escrever
		new Word(Opcode.TRAP, -1, -1, -1),
		new Word(Opcode.STOP, -1, -1, -1), // POS 17
		new Word(Opcode.DATA, -1, -1, -1)  };//POS 18	
		
		public Word[] fibonacciTRAP = new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
		 new Word(Opcode.LDI, 8, -1, 1),// leitura
		 new Word(Opcode.LDI, 9, -1, 100),//endereco a guardar
		 new Word(Opcode.TRAP, -1, -1, -1),
		 new Word(Opcode.LDD, 7, -1, 100),// numero do tamanho do fib
		 new Word(Opcode.LDI, 3, -1, 0),
		 new Word(Opcode.ADD, 3, 7, -1),
		 new Word(Opcode.LDI, 4, -1, 36),//posicao para qual ira pular (stop) *
		 new Word(Opcode.LDI, 1, -1, -1),// caso negativo
		 new Word(Opcode.STD, 1, -1, 41),
		 new Word(Opcode.JMPIL, 4, 7, -1),//pula pra stop caso negativo *
		 new Word(Opcode.JMPIE, 4, 7, -1),//pula pra stop caso 0
		 new Word(Opcode.ADDI, 7, -1, 41),// fibonacci + posição do stop
		 new Word(Opcode.LDI, 1, -1, 0),
		 new Word(Opcode.STD, 1, -1, 41),    // 25 posicao de memoria onde inicia a serie de fibonacci gerada
		 new Word(Opcode.SUBI, 3, -1, 1),// se 1 pula pro stop
		 new Word(Opcode.JMPIE, 4, 3, -1),
		 new Word(Opcode.ADDI, 3, -1, 1),
		 new Word(Opcode.LDI, 2, -1, 1),
		 new Word(Opcode.STD, 2, -1, 42),
		 new Word(Opcode.SUBI, 3, -1, 2),// se 2 pula pro stop
		 new Word(Opcode.JMPIE, 4, 3, -1),
		 new Word(Opcode.LDI, 0, -1, 43),
		 new Word(Opcode.LDI, 6, -1, 25),// salva posição de retorno do loop
		 new Word(Opcode.LDI, 5, -1, 0),//salva tamanho
		 new Word(Opcode.ADD, 5, 7, -1),
		 new Word(Opcode.LDI, 7, -1, 0),//zera (inicio do loop)
		 new Word(Opcode.ADD, 7, 5, -1),//recarrega tamanho
		 new Word(Opcode.LDI, 3, -1, 0),
		 new Word(Opcode.ADD, 3, 1, -1),
		 new Word(Opcode.LDI, 1, -1, 0),
		 new Word(Opcode.ADD, 1, 2, -1),
		 new Word(Opcode.ADD, 2, 3, -1),
		 new Word(Opcode.STX, 0, 2, -1),
		 new Word(Opcode.ADDI, 0, -1, 1),
		 new Word(Opcode.SUB, 7, 0, -1),
		 new Word(Opcode.JMPIG, 6, 7, -1),//volta para o inicio do loop
		 new Word(Opcode.STOP, -1, -1, -1),   // POS 36
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),   // POS 41
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1),
		 new Word(Opcode.DATA, -1, -1, -1)
 };

 public Word[] PB = new Word[] {
	 //dado um inteiro em alguma posição de memória,
	 // se for negativo armazena -1 na saída; se for positivo responde o fatorial do número na saída
	 new Word(Opcode.LDI, 0, -1, 7),// numero para colocar na memoria
	 new Word(Opcode.STD, 0, -1, 50),
	 new Word(Opcode.LDD, 0, -1, 50),
	 new Word(Opcode.LDI, 1, -1, -1),
	 new Word(Opcode.LDI, 2, -1, 13),// SALVAR POS STOP
	 new Word(Opcode.JMPIL, 2, 0, -1),// caso negativo pula pro STD
	 new Word(Opcode.LDI, 1, -1, 1),
	 new Word(Opcode.LDI, 6, -1, 1),
	 new Word(Opcode.LDI, 7, -1, 13),
	 new Word(Opcode.JMPIE, 7, 0, 0), //POS 9 pula pra STD (Stop-1)
	 new Word(Opcode.MULT, 1, 0, -1),
	 new Word(Opcode.SUB, 0, 6, -1),
	 new Word(Opcode.JMP, -1, -1, 9),// pula para o JMPIE
	 new Word(Opcode.STD, 1, -1, 15),
	 new Word(Opcode.STOP, -1, -1, -1), // POS 14
	 new Word(Opcode.DATA, -1, -1, -1)}; //POS 15

public Word[] PC = new Word[] {
	 //Para um N definido (10 por exemplo)
	 //o programa ordena um vetor de N números em alguma posição de memória;
	 //ordena usando bubble sort
	 //loop ate que não swap nada
	 //passando pelos N valores
	 //faz swap de vizinhos se da esquerda maior que da direita
	 new Word(Opcode.LDI, 7, -1, 5),// TAMANHO DO BUBBLE SORT (N)
	 new Word(Opcode.LDI, 6, -1, 5),//aux N
	 new Word(Opcode.LDI, 5, -1, 46),//LOCAL DA MEMORIA
	 new Word(Opcode.LDI, 4, -1, 47),//aux local memoria
	 new Word(Opcode.LDI, 0, -1, 4),//colocando valores na memoria
	 new Word(Opcode.STD, 0, -1, 46),
	 new Word(Opcode.LDI, 0, -1, 3),
	 new Word(Opcode.STD, 0, -1, 47),
	 new Word(Opcode.LDI, 0, -1, 5),
	 new Word(Opcode.STD, 0, -1, 48),
	 new Word(Opcode.LDI, 0, -1, 1),
	 new Word(Opcode.STD, 0, -1, 49),
	 new Word(Opcode.LDI, 0, -1, 2),
	 new Word(Opcode.STD, 0, -1, 50),//colocando valores na memoria até aqui - POS 13
	 new Word(Opcode.LDI, 3, -1, 25),// Posicao para pulo CHAVE 1
	 new Word(Opcode.STD, 3, -1, 99),
	 new Word(Opcode.LDI, 3, -1, 22),// Posicao para pulo CHAVE 2
	 new Word(Opcode.STD, 3, -1, 98),
	 new Word(Opcode.LDI, 3, -1, 38),// Posicao para pulo CHAVE 3
	 new Word(Opcode.STD, 3, -1, 97),
	 new Word(Opcode.LDI, 3, -1, 25),// Posicao para pulo CHAVE 4 (não usada)
	 new Word(Opcode.STD, 3, -1, 96),
	 new Word(Opcode.LDI, 6, -1, 0),//r6 = r7 - 1 POS 22
	 new Word(Opcode.ADD, 6, 7, -1),
	 new Word(Opcode.SUBI, 6, -1, 1),//ate aqui
	 new Word(Opcode.JMPIEM, -1, 6, 97),//CHAVE 3 para pular quando r7 for 1 e r6 0 para interomper o loop de vez do programa
	 new Word(Opcode.LDX, 0, 5, -1),//r0 e r1 pegando valores das posições da memoria POS 26
	 new Word(Opcode.LDX, 1, 4, -1),
	 new Word(Opcode.LDI, 2, -1, 0),
	 new Word(Opcode.ADD, 2, 0, -1),
	 new Word(Opcode.SUB, 2, 1, -1),
	 new Word(Opcode.ADDI, 4, -1, 1),
	 new Word(Opcode.SUBI, 6, -1, 1),
	 new Word(Opcode.JMPILM, -1, 2, 99),//LOOP chave 1 caso neg procura prox
	 new Word(Opcode.STX, 5, 1, -1),
	 new Word(Opcode.SUBI, 4, -1, 1),
	 new Word(Opcode.STX, 4, 0, -1),
	 new Word(Opcode.ADDI, 4, -1, 1),
	 new Word(Opcode.JMPIGM, -1, 6, 99),//LOOP chave 1 POS 38
	 new Word(Opcode.ADDI, 5, -1, 1),
	 new Word(Opcode.SUBI, 7, -1, 1),
	 new Word(Opcode.LDI, 4, -1, 0),//r4 = r5 + 1 POS 41
	 new Word(Opcode.ADD, 4, 5, -1),
	 new Word(Opcode.ADDI, 4, -1, 1),//ate aqui
	 new Word(Opcode.JMPIGM, -1, 7, 98),//LOOP chave 2
	 new Word(Opcode.STOP, -1, -1, -1), // POS 45
	 new Word(Opcode.DATA, -1, -1, -1),
	 new Word(Opcode.DATA, -1, -1, -1),
	 new Word(Opcode.DATA, -1, -1, -1),
	 new Word(Opcode.DATA, -1, -1, -1),
	 new Word(Opcode.DATA, -1, -1, -1),
	 new Word(Opcode.DATA, -1, -1, -1),
	 new Word(Opcode.DATA, -1, -1, -1),
	 new Word(Opcode.DATA, -1, -1, -1)};

 public Word[] malloc = new Word[]{
	 new Word(Opcode.LDI, 8, 1, 3),
	 new Word(Opcode.LDI, 0, -1, 26),		
	 new Word(Opcode.TRAP, -1, -1, -1),
	 new Word(Opcode.STD, 0, -1, 24),
	 new Word(Opcode.STOP, -1, -1, -1)};

 public Word[] inteStop = new Word[] {
	 new Word(Opcode.STOP, -1, -1, -1) }; //testa final de programa
	 
 public Word[] inteChamSistInvalida = new Word[] {
	 new Word(Opcode.LDI, 8, -1, 4),		
	 new Word(Opcode.LDI, 9, -1, 25),	
	 new Word(Opcode.TRAP, -1, -1, -1) }; //testa Chamada de Sistema Invalida
 };
}