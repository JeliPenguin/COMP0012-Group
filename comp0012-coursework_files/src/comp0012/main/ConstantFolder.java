package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.*;
import org.apache.bcel.util.InstructionFinder;









public class ConstantFolder
{
	public static class VariableTable{
		private HashMap<Integer, Variable> variableMap;
		private ArrayList<Integer> iteratorPos;
		public VariableTable(){
			variableMap = new HashMap<>();
			iteratorPos = new ArrayList<>();
		}

		public boolean hasPos(int storePos){
			return variableMap.containsKey(storePos);
		}

		public void setVar(int line,int storePos, Object value){
			//variableMap.put(storePos,value);
			if (variableMap.containsKey(storePos)){
				//Already has an entry of Variable
				variableMap.get(storePos).addLifeTime(line,value);
			}else {
				Variable var = new Variable(line,value);
				variableMap.put(storePos, var);
			}
		}

		public Object getValue(int storePos,int line){
			if (variableMap.containsKey(storePos)) {
				return variableMap.get(storePos).getConstantValue(line);
			}
			return null;
		}

		public void printVal(){
			for (int k : variableMap.keySet()){
				System.out.println("StorePos: " + k);
				variableMap.get(k).printVariable();
			}
		}

		public void removeEntry(int line, int storePos){
			if (variableMap.containsKey(storePos)) {
				//variableMap.get(storePos).removeLine(line);
				variableMap.remove(storePos);
			}
		}

		public void addIterator(int storePos){
			iteratorPos.add(storePos);
		}

		public boolean isIterator(int storePos){
			return iteratorPos.contains(storePos);
		}

	}

	public static class Variable{
		private HashMap<Integer, Object> variableMap;
		public Variable(int line, Object value){
			//Map line to value
			variableMap = new HashMap<>();
			variableMap.put(line,value);
		}

		public void addLifeTime(int line, Object value){
			variableMap.put(line,value);
		}

		public Object getConstantValue(int line){
			int retLine = -1;
			for (int key : variableMap.keySet()){
				if (key < line && key > retLine){
					retLine = key;
				}
			}
			return variableMap.get(retLine);
		}

		public void removeLine(int line){
			variableMap.remove(line);
		}

		public void printVariable(){
			for (int k : variableMap.keySet()){
				System.out.println("	Line: " + k + "  Value: " + variableMap.get(k));
			}
		}
	}


	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	//String debuggingClass = "comp0012.target.BooleanOperators";
	//String debuggingClass = "comp0012.target.ConstantVariableFolding";
	String debuggingClass = "comp0012.target.myTest";
	String currentClass = "";

	boolean display = true;

	public ConstantFolder(String classFilePath)
	{
		try{
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	public void printInstructions(ClassGen classGen,ConstantPoolGen constPoolGen){
		Method[] methods = classGen.getMethods();
		for (Method method : methods) {
			MethodGen methodGen = new MethodGen(method, classGen.getClassName(), constPoolGen);
			System.out.println(classGen.getClassName() + " > " + method.getName());
			System.out.println(methodGen.getInstructionList());
		}
	}

	public void getNumberConstant(ConstantPoolGen cpgen){
		ConstantPool cp = cpgen.getConstantPool();
		// get the constants in the pool
		Constant[] constants = cp.getConstantPool();
		for (int i = 0; i < constants.length; i++)
		{
			if (constants[i] instanceof ConstantInteger || constants[i] instanceof ConstantDouble || constants[i] instanceof ConstantLong || constants[i] instanceof ConstantFloat)
			{
				System.out.printf("%d) ",i);
				System.out.println(constants[i]);
			}
		}
		System.out.println();
	}

	public void displayPool(ClassGen cgen, ConstantPoolGen cpgen){
		if (currentClass.equals(debuggingClass) && display) {
			//System.out.println(cpgen);
			getNumberConstant(cpgen);
			//printInstructions(cgen,cpgen);
		}
	}

	public void displayInfo(String Content,int indent){
		if (currentClass.equals(debuggingClass) && display) {
			System.out.println((new String(new char[indent]).replace("\0", " ")) + Content);
		}
	}

	public Number getPushedValue(InstructionHandle instructionHandler,ConstantPoolGen cpgen, VariableTable variableTable){
		//Gets the value that is being pushed from constant pool
		Object value = null;
		Instruction instruction = instructionHandler.getInstruction();
		if (instruction instanceof LDC){
			LDC loadInstruction = (LDC)instruction;
			value = loadInstruction.getValue(cpgen);
		}else if (instruction instanceof LDC2_W){
			LDC2_W loadInstruction = (LDC2_W)instruction;
			value = loadInstruction.getValue(cpgen);
		}else if (instruction instanceof LoadInstruction){
			LoadInstruction loadInstruction = (LoadInstruction) instruction;
			//Get value of local variable stored at the index referenced by Loadinstruction
			value = variableTable.getValue(loadInstruction.getIndex(),instructionHandler.getPosition());
		}else if (instruction instanceof ConstantPushInstruction){
			ConstantPushInstruction loadInstruction = (ConstantPushInstruction) instruction;
			value = loadInstruction.getValue();
		}
		return (Number)value;
	}

	public boolean isIterator(InstructionHandle instructionHandler,VariableTable variableTable){
		Instruction instruction = instructionHandler.getInstruction();
		if (instruction instanceof LoadInstruction){
			LoadInstruction loadInstruction = (LoadInstruction) instruction;
			//Get value of local variable stored at the index referenced by Loadinstruction
			if (variableTable.isIterator(loadInstruction.getIndex())){
				return true;
			}
		}
		return false;
	}

	enum binOps{
		//Enum for binary operations, used to achieve switch case
		IADD,FADD,DADD,LADD,ISUB,FSUB,DSUB,LSUB,IDIV,FDIV,DDIV,LDIV,IMUL,FMUL,DMUL,LMUL,IXOR,LXOR,IAND,LAND,LOR,IOR;
	}

	enum compareOps{
		LCMP,DCMPG,DCMPL,FCMPG,FCMPL;
	}

	enum unaryOps{
		INEG,LNEG,DNEG,FNEG;
	}

	public boolean comparisonFold(InstructionList il, ConstantPoolGen cpgen,VariableTable variableTable){
		boolean changed = false;
		InstructionFinder itf = new InstructionFinder(il);
		Iterator iter = itf.search("PushInstruction PushInstruction (LCMP | DCMPL | DCMPG | FCMPL | FCMPG) InstructionTargeter PushInstruction UnconditionalBranch PushInstruction");

		if (iter.hasNext()){
			//Iterator return InstructionHandle
			InstructionHandle[] instructions = (InstructionHandle[])iter.next();
			displayInfo("Old instruction segment:",2);
			for (InstructionHandle a : instructions){
				displayInfo(a.getInstruction().toString(),4);
			}
			Number[] operands = new Number[2];
			while (isIterator(instructions[0],variableTable) || isIterator(instructions[1],variableTable)){
				if (iter.hasNext()){
					instructions = (InstructionHandle[])iter.next();
					displayInfo("Ignored instruction segment:",2);
					for (InstructionHandle a : instructions){
						displayInfo(a.getInstruction().toString(),4);
					}
				}
				else{
					return false;
				}
			}
			operands[0] = getPushedValue(instructions[0],cpgen,variableTable);
			operands[1] = getPushedValue(instructions[1],cpgen,variableTable);
			if (!(operands[0] == null || operands[1] == null)){
				Instruction opcode = instructions[2].getInstruction();
				compareOps opClass = compareOps.valueOf(opcode.getClass().getSimpleName());
				Instruction newInstruction = null;
				switch (opClass){

					case LCMP:
						if(operands[0].longValue() > operands[1].longValue()){
							newInstruction = new ICONST(1);
						}
						else if (operands[0].longValue() == operands[1].longValue()){
							newInstruction = new ICONST(0);
						}
						else{
							newInstruction = new ICONST(-1);
						}
						break;

					case DCMPG:
						if(operands[0].doubleValue() > operands[1].doubleValue()){
							newInstruction = new ICONST(1);
						}
						else{
							newInstruction = new ICONST(0);
						}
						break;

					case DCMPL:
						if(operands[0].doubleValue() < operands[1].doubleValue()){
							newInstruction = new ICONST(1);
						}
						else{
							newInstruction = new ICONST(0);
						}
						break;

					case FCMPG:
						if(operands[0].floatValue() > operands[1].floatValue()){
							newInstruction = new ICONST(1);
						}
						else{
							newInstruction = new ICONST(0);
						}
						break;

					case FCMPL:
						if(operands[0].floatValue() < operands[1].floatValue()){
							newInstruction = new ICONST(1);
						}
						else{
							newInstruction = new ICONST(0);
						}
						break;
					

				}

				if(newInstruction != null){
					changed = true;
					displayInfo("New instruction segment:",2);
					displayInfo(newInstruction.toString(),4);
					instructions[0].setInstruction(newInstruction);
					try {
						il.delete(instructions[1]);
						il.delete(instructions[2]);
						il.delete(instructions[3]);
						il.delete(instructions[4]);
						il.delete(instructions[5]);
						il.delete(instructions[6]);

					}catch (TargetLostException | ArrayIndexOutOfBoundsException e){
					}
				}else{
					displayInfo("Null newInstruction",2);
				}
			}

		}
		displayInfo("\n",0);
		return changed;
	}

	public boolean unaryOpFold(InstructionList il, ConstantPoolGen cpgen,VariableTable variableTable){
		boolean changed = false;

		InstructionFinder itf = new InstructionFinder(il);
		//Search through InstructionList for pattern: load load followed by an arithmetic instruction
		//Iterator iter = itf.search("PushInstruction PushInstruction ArithmeticInstruction");
		Iterator iter = itf.search("PushInstruction (LNEG | FNEG | INEG | DNEG )");
		if (iter.hasNext()){
			//Iterator return InstructionHandle
			InstructionHandle[] instructions = (InstructionHandle[])iter.next();
			displayInfo("Old instruction segment:",2);
			for (InstructionHandle a : instructions){
				displayInfo(a.getInstruction().toString(),4);
			}
			Number[] operands = new Number[1];
			
			if (isIterator(instructions[0],variableTable) || isIterator(instructions[1],variableTable)){
				
			}
			operands[0] = getPushedValue(instructions[0],cpgen,variableTable);
			while (isIterator(instructions[0],variableTable)){
				if (iter.hasNext()){
					instructions = (InstructionHandle[])iter.next();
					displayInfo("Ignored instruction segment:",2);
					for (InstructionHandle a : instructions){
						displayInfo(a.getInstruction().toString(),4);
					}
				}
				else{
					return false;
				}
			}
			if (!(operands[0] == null)){
				Instruction opcode = instructions[1].getInstruction();
				
				unaryOps opClass = unaryOps.valueOf(opcode.getClass().getSimpleName());
				Instruction newInstruction = null;
				switch (opClass){
					case INEG:
						newInstruction = new LDC(cpgen.addInteger(operands[0].intValue() * -1));
						
						break;
					case LNEG:
						newInstruction = new LDC2_W(-cpgen.addLong(operands[0].longValue() * -1));
						break;
					case DNEG:
						newInstruction = new LDC2_W( cpgen.addDouble(operands[0].doubleValue() * -1.0));
						break;
					case FNEG:
						newInstruction = new LDC(-cpgen.addFloat(operands[0].floatValue() * -1));
						break;
					
				}
				if (newInstruction != null){
					
					changed = true;
					displayInfo("New instruction segment:",2);
					displayInfo(newInstruction.toString(),4);
					instructions[0].setInstruction(newInstruction);
					try {
						il.delete(instructions[1]);
						
					}catch (TargetLostException e){
						
					}
					
				}else{
					displayInfo("Null newInstruction",2);
				}
			}
			
		}
		
		displayInfo("\n",0);
		return changed;
	}

	public boolean binaryOpFold(InstructionList il, ConstantPoolGen cpgen,VariableTable variableTable){
		boolean changed = false;

		InstructionFinder itf = new InstructionFinder(il);
		//Search through InstructionList for pattern: load load followed by an arithmetic instruction
		//Iterator iter = itf.search("PushInstruction PushInstruction ArithmeticInstruction");
		Iterator iter = itf.search("PushInstruction PushInstruction ArithmeticInstruction");
		if (iter.hasNext()){
			//Iterator return InstructionHandle
			InstructionHandle[] instructions = (InstructionHandle[])iter.next();
			displayInfo("Old instruction segment:",2);
			for (InstructionHandle a : instructions){
				displayInfo(a.getInstruction().toString(),4);
			}
			Number[] operands = new Number[2];
			
			while (isIterator(instructions[0],variableTable) || isIterator(instructions[1],variableTable)){
				if (iter.hasNext()){
					instructions = (InstructionHandle[])iter.next();
					displayInfo("Ignored instruction segment:",2);
					for (InstructionHandle a : instructions){
						displayInfo(a.getInstruction().toString(),4);
					}
				}
				else{
					return false;
				}
			}

			operands[0] = getPushedValue(instructions[0],cpgen,variableTable);
			operands[1] = getPushedValue(instructions[1],cpgen,variableTable);
			if (!(operands[0] == null || operands[1] == null)){
				Instruction opcode = instructions[2].getInstruction();
				binOps opClass = binOps.valueOf(opcode.getClass().getSimpleName());
				Instruction newInstruction = null;
				switch (opClass){
					case IADD:
						newInstruction = new LDC(cpgen.addInteger(operands[0].intValue() + operands[1].intValue()));
						/*
						System.out.print(operands[0].intValue());
						System.out.print(" + ");
						System.out.print(operands[1].intValue());
						System.out.print(" = ");
						System.out.println(operands[0].intValue() + operands[1].intValue());*/
						break;
					case FADD:
						newInstruction = new LDC(cpgen.addFloat(operands[0].floatValue() + operands[1].floatValue()));
						break;
					case DADD:
						newInstruction = new LDC2_W(cpgen.addDouble(operands[0].doubleValue() + operands[1].doubleValue()));
						break;
					case LADD:
						newInstruction = new LDC2_W(cpgen.addLong(operands[0].longValue() + operands[1].longValue()));
						break;
					case ISUB:
						newInstruction = new LDC(cpgen.addInteger(operands[0].intValue() - operands[1].intValue()));
						break;
					case FSUB:
						newInstruction = new LDC(cpgen.addFloat(operands[0].floatValue() - operands[1].floatValue()));
						break;
					case DSUB:
						newInstruction = new LDC2_W(cpgen.addDouble(operands[0].doubleValue() - operands[1].doubleValue()));
						break;
					case LSUB:
						newInstruction = new LDC2_W(cpgen.addLong(operands[0].longValue() - operands[1].longValue()));
						break;
					case IDIV:
						newInstruction = new LDC(cpgen.addInteger(operands[0].intValue() / operands[1].intValue()));
						break;
					case FDIV:
						newInstruction = new LDC(cpgen.addFloat(operands[0].floatValue() / operands[1].floatValue()));
						break;
					case DDIV:
						newInstruction = new LDC2_W(cpgen.addDouble(operands[0].doubleValue() / operands[1].doubleValue()));
						break;
					case LDIV:
						newInstruction = new LDC2_W(cpgen.addLong(operands[0].longValue() / operands[1].longValue()));
						break;
					case IMUL:
						newInstruction = new LDC(cpgen.addInteger(operands[0].intValue() * operands[1].intValue()));
						break;
					case FMUL:
						newInstruction = new LDC(cpgen.addFloat(operands[0].floatValue() * operands[1].floatValue()));
						break;
					case DMUL:
						newInstruction = new LDC2_W(cpgen.addDouble(operands[0].doubleValue() * operands[1].doubleValue()));
						break;
					case LMUL:
						newInstruction = new LDC2_W(cpgen.addLong(operands[0].longValue() * operands[1].longValue()));
						break;
				}
				if (newInstruction != null){
					changed = true;
					displayInfo("New instruction segment:",2);
					displayInfo(newInstruction.toString(),4);
					instructions[0].setInstruction(newInstruction);
					try {
						il.delete(instructions[1]);
						il.delete(instructions[2]);
					}catch (TargetLostException e){
					}
				}else{
					displayInfo("Null newInstruction",2);
				}
			}
			
		}
		displayInfo("\n",0);
		return changed;
	}

	public ArrayList<Integer> localVarIndices(InstructionList il){
		InstructionFinder itf = new InstructionFinder(il);
		Iterator iter = itf.search("StoreInstruction");
		ArrayList<Integer> indices = new ArrayList<>();
		while (iter.hasNext()){
			InstructionHandle[] instructions = (InstructionHandle[])iter.next();
			StoreInstruction i = (StoreInstruction)instructions[0].getInstruction();
			indices.add(i.getIndex());
		}
		return indices;
	}

	public void initVariableTable(InstructionList il,ConstantPoolGen cpgen,VariableTable variableTable){
		ArrayList<Integer> indices = localVarIndices(il);
		InstructionFinder itf = new InstructionFinder(il);
		Iterator iter = itf.search("PushInstruction StoreInstruction");
		while (iter.hasNext()) {
			InstructionHandle[] instructionHandler = (InstructionHandle[])iter.next();
			Object content = getPushedValue(instructionHandler[0],cpgen,variableTable);
			if (content == null){
				continue;
			}
			StoreInstruction store = (StoreInstruction) instructionHandler[1].getInstruction();
			int storePos = store.getIndex();
			variableTable.setVar(instructionHandler[1].getPosition(),storePos,content);
		}
		removeIncrementer(il,variableTable);
		if (currentClass.equals(debuggingClass) && display) {
			System.out.println("Local Variables: ");
			variableTable.printVal();
			System.out.println();
		}
	}

	public void removeIncrementer(InstructionList il,VariableTable variableTable){
		InstructionFinder itf = new InstructionFinder(il);
		Iterator iter = itf.search("IINC");
		while (iter.hasNext()){
			InstructionHandle[] instructionHandler = (InstructionHandle[])iter.next();
			IndexedInstruction inc = (IndexedInstruction) instructionHandler[0].getInstruction();
			/*if (currentClass.equals(debuggingClass) && display){
				System.out.print("Incrementer StorePos: ");
				System.out.print(inc.getIndex());
				System.out.print("	Line: ");
				System.out.println(instructionHandler[0].getPosition());
			}*/
			variableTable.removeEntry(instructionHandler[0].getPosition(),inc.getIndex());
			variableTable.addIterator(inc.getIndex());
		}
	}

	public Method optimizeMethod(ClassGen cgen, Method me){
		ConstantPoolGen cpgen = cgen.getConstantPool();
		MethodGen mg = new MethodGen(me, cgen.getClassName(), cpgen);
		displayInfo("\nDebugging method " + mg.getName(),0);
		mg.removeNOPs();
		InstructionList il = mg.getInstructionList();
		displayInfo("Instructions before: ",0);
		displayInfo(il.toString(),0);
		boolean changed = true;
		while (changed){
			//Fixed point
			VariableTable variableTable = new VariableTable();
			initVariableTable(il,cpgen,variableTable);
			changed = false;
			displayInfo("Folding binary operation:",0);
			if (binaryOpFold(il,cpgen,variableTable)){
				changed = true;
			}
			displayInfo("Folding comparison operation:",0);
		    if (comparisonFold(il,cpgen,variableTable)){
				changed = true;
			}
			displayInfo("Folding unary operation:",0);
			if (unaryOpFold(il,cpgen,variableTable)){
				changed = true;
			}
		}

		displayInfo("\nInstructions after",0);
		displayInfo(il.toString(),0);
		return mg.getMethod();
	}

	public void optimization(ClassGen cgen){
		Method[] methods = cgen.getMethods();
		Method[] optimised = new Method[methods.length];
		for (int i = 0; i< methods.length; i++){
			optimised[i] = optimizeMethod(cgen,methods[i]);
		}
		gen.setMethods(optimised);
	}

	public void optimize()
	{
		ClassGen cgen = new ClassGen(original);
		currentClass = cgen.getClassName();
		if (currentClass.equals(debuggingClass)) {
			System.out.printf("*******%s*********\n", currentClass);
		}
		ConstantPoolGen cpgen = cgen.getConstantPool();
		displayInfo("Number Constant Pool before:",0);
		displayPool(cgen,cpgen);
		optimization(cgen);
		displayInfo("Number Constant Pool after:",0);
		displayPool(cgen,cpgen);
		gen.setConstantPool(cpgen);
		gen.setMajor(50);
		gen.setMinor(0);
		this.optimized = gen.getJavaClass();
	}

	
	public void write(String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}
}