/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/
package org.ballerinalang.nativeimpl.utils;

import static org.objectweb.asm.Opcodes.*;

import org.ballerinalang.BLangProgramLoader;
import org.ballerinalang.model.types.BType;
import org.ballerinalang.model.types.BTypes;
import org.ballerinalang.model.types.TypeTags;
import org.ballerinalang.model.values.BFloat;
import org.ballerinalang.model.values.BInteger;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.util.codegen.FunctionInfo;
import org.ballerinalang.util.codegen.Instruction;
import org.ballerinalang.util.codegen.InstructionCodes;
import org.ballerinalang.util.codegen.PackageInfo;
import org.ballerinalang.util.codegen.ProgramFile;
import org.ballerinalang.util.codegen.cpentries.ConstantPoolEntry;
import org.ballerinalang.util.codegen.cpentries.FloatCPEntry;
import org.ballerinalang.util.codegen.cpentries.FunctionCallCPEntry;
import org.ballerinalang.util.codegen.cpentries.FunctionRefCPEntry;
import org.ballerinalang.util.codegen.cpentries.IntegerCPEntry;
import org.ballerinalang.util.program.BLangFunctions;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ballerina bytecode -> JVM bytecode generator/executor.
 */
public class BJVMEnv {
    
    private static final String CALL_RESULT_OBJ_NAME = "CALL_RESULT";

    private static final String FUNC_RESULT_OBJECT_NAME = "FUNC_RESULT";

    private static final String DEFAULT_CLASS_NAME = "DEFAULT_CLASS";
    
    private static Map<String, byte[]> classData = new HashMap<>();
    
    private static Map<String, MethodInfo> methodInfoMap = new HashMap<>();
    
    public static void registerProgramFile(ProgramFile programFile) throws Exception {
        byte[] classBytes;
        String jvmClassName;
        for (PackageInfo packageInfo : programFile.getPackageInfoCollection()) {
            if (packageInfo.getPkgPath().startsWith("ballerina.lang")) {
                continue;
            }
            jvmClassName = ballerinaPackageToJVMClass(packageInfo.getPkgPath());
            classBytes = processPackage(packageInfo, jvmClassName);
            BJVMEnv.classData.put(jvmClassName, classBytes);
            Files.write(Paths.get("/home/laf/Desktop/DEFAULT_CLASS.class") , classBytes);
        }
    }
    
    private static byte[] processPackage(PackageInfo packageInfo, String jvmClassName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_6, ACC_PUBLIC, jvmClassName, null, Type.getInternalName(Object.class), null);
        generateDefaultConstructor(cw);
        for (FunctionInfo funcInfo : packageInfo.getFunctionInfoCollection()) {
            processFunction(cw, packageInfo, funcInfo);
        }
        return cw.toByteArray();        
    }
    
    private static void processFunction(ClassWriter cw, PackageInfo packageInfo, FunctionInfo functionInfo) {
        if (functionInfo.isNative() || functionInfo.getName().contains("<init>")) {
            return;
        }
        MethodInfo methodInfo = new MethodInfo(functionInfo);
        BJVMEnv.methodInfoMap.put(generateKeyForFuncID(packageInfo.getPkgPath(), functionInfo.getName()), methodInfo);
        String methodDesc = generateJVMMethodDesc(methodInfo);
        String methodName = methodInfo.getName();

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, methodName, methodDesc, null, null);
        mv.visitMaxs(100, 400);
        JVMMethodContext ctx = new JVMMethodContext(mv, methodInfo);
        createFunctionResultObject(ctx, methodInfo.getRetCount());
        
        processInstructions(mv, methodInfo.getInstrs(), methodInfo.getStartIP(), ctx);        
    }
    
    private static String generateKeyForFuncID(String pkgPath, String funcName) {
        return pkgPath + "#" + funcName;
    }
        
    private static void processInstructions(MethodVisitor mv, Instruction[] instrs, int startIP, 
            JVMMethodContext ctx) {
        int codeLength = ctx.getMethodInfo().getCodeLength();
        ConstantPoolEntry[] consts = ctx.getMethodInfo().getConsts();
        Map<Integer, Label> labelMap = allocateLabels(startIP, codeLength);
        for (int i = 0; i < codeLength; i++) {
            Instruction instr = instrs[startIP + i];
            mv.visitLabel(labelMap.get(startIP + i));
            int[] oprs = instr.getOperands();
            switch (instr.getOpcode()) {
            case InstructionCodes.BCONST_0:
                storeSmallIntegerConstantToBalRegister(ctx, oprs[0], 0);
                break;
            case InstructionCodes.BCONST_1:
                storeSmallIntegerConstantToBalRegister(ctx, oprs[0], 1);
                break;
            case InstructionCodes.ICONST:
                long ivalue = ((IntegerCPEntry) consts[oprs[0]]).getValue();
                storeIntegerConstantToBalRegister(ctx, oprs[1], ivalue);
                break;
            case InstructionCodes.FCONST:
                double fvalue = ((FloatCPEntry) consts[oprs[0]]).getValue();
                storeFloatConstantToBalRegister(ctx, oprs[1], fvalue);
                break;
            case InstructionCodes.ICONST_1:
                storeIntegerConstantToBalRegister(ctx, oprs[0], 1);
                break;
            case InstructionCodes.ICONST_2:
                storeIntegerConstantToBalRegister(ctx, oprs[0], 2);
                break;
            case InstructionCodes.ICONST_3:
                storeIntegerConstantToBalRegister(ctx, oprs[0], 3);
                break;
            case InstructionCodes.ICONST_4:
                storeIntegerConstantToBalRegister(ctx, oprs[0], 4);
                break;
            case InstructionCodes.ICONST_5:
                storeIntegerConstantToBalRegister(ctx, oprs[0], 5);
                break;
            case InstructionCodes.ICONST_0:
                storeIntegerConstantToBalRegister(ctx, oprs[0], 0);
                break;
            case InstructionCodes.FCONST_1:
                storeFloatConstantToBalRegister(ctx, oprs[0], 1);
                break;
            case InstructionCodes.FCONST_2:
                storeFloatConstantToBalRegister(ctx, oprs[0], 2);
                break;
            case InstructionCodes.FCONST_3:
                storeFloatConstantToBalRegister(ctx, oprs[0], 3);
                break;
            case InstructionCodes.FCONST_4:
                storeFloatConstantToBalRegister(ctx, oprs[0], 4);
                break;
            case InstructionCodes.FCONST_5:
                storeFloatConstantToBalRegister(ctx, oprs[0], 5);
                break;
            case InstructionCodes.FCONST_0:
                storeFloatConstantToBalRegister(ctx, oprs[0], 0);
                break;
            case InstructionCodes.ISTORE:
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[0]);
                storeJVMStackIntegerValueToBalLocalVariable(ctx, oprs[1]);
                break;
            case InstructionCodes.FSTORE:
                loadBalFloatRegisterVariableToJVMStack(ctx, oprs[0]);
                storeJVMStackFloatValueToBalLocalVariable(ctx, oprs[1]);
                break;
            case InstructionCodes.ILOAD:
                loadBalIntegerLocalVariableToJVMStack(ctx, oprs[0]);
                storeJVMStackIntegerValueToBalRegisterVariable(ctx, oprs[1]);
                break;
            case InstructionCodes.FLOAD:
                loadBalFloatLocalVariableToJVMStack(ctx, oprs[0]);
                storeJVMStackFloatValueToBalRegisterVariable(ctx, oprs[1]);
                break;
            case InstructionCodes.IADD:
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[0]);
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[1]);
                mv.visitInsn(LADD);
                storeJVMStackIntegerValueToBalRegisterVariable(ctx, oprs[2]);
                break;
            case InstructionCodes.ISUB:
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[0]);
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[1]);
                mv.visitInsn(LSUB);
                storeJVMStackIntegerValueToBalRegisterVariable(ctx, oprs[2]);
                break;
            case InstructionCodes.IMUL:
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[0]);
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[1]);
                mv.visitInsn(LMUL);
                storeJVMStackIntegerValueToBalRegisterVariable(ctx, oprs[2]);
                break;
            case InstructionCodes.IDIV:
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[0]);
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[1]);
                mv.visitInsn(LDIV);
                storeJVMStackIntegerValueToBalRegisterVariable(ctx, oprs[2]);
                break;
            case InstructionCodes.FADD:
                loadBalFloatRegisterVariableToJVMStack(ctx, oprs[0]);
                loadBalFloatRegisterVariableToJVMStack(ctx, oprs[1]);
                mv.visitInsn(DADD);
                storeJVMStackFloatValueToBalRegisterVariable(ctx, oprs[2]);
                break;
            case InstructionCodes.FSUB:
                loadBalFloatRegisterVariableToJVMStack(ctx, oprs[0]);
                loadBalFloatRegisterVariableToJVMStack(ctx, oprs[1]);
                mv.visitInsn(DSUB);
                storeJVMStackFloatValueToBalRegisterVariable(ctx, oprs[2]);
                break;
            case InstructionCodes.FMUL:
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[0]);
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[1]);
                mv.visitInsn(DMUL);
                storeJVMStackIntegerValueToBalRegisterVariable(ctx, oprs[2]);
                break;
            case InstructionCodes.FDIV:
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[0]);
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[1]);
                mv.visitInsn(DDIV);
                storeJVMStackIntegerValueToBalRegisterVariable(ctx, oprs[2]);
                break;
            case InstructionCodes.IRET:
                setObjectArrayIntFieldValue(ctx, FUNC_RESULT_OBJECT_NAME, oprs[0], oprs[1]);
                break;
            case InstructionCodes.FRET:
                setObjectArrayFloatFieldValue(ctx, FUNC_RESULT_OBJECT_NAME, oprs[0], oprs[1]);
                break;
            case InstructionCodes.RET:
                int index = ctx.lookupJVMObjectLocalVariableIndex(FUNC_RESULT_OBJECT_NAME);
                mv.visitVarInsn(ALOAD, index);
                mv.visitInsn(ARETURN);
                break;
            case InstructionCodes.CALL:
                processCallInstr(mv, ctx, consts, oprs);
                break;
            case InstructionCodes.IEQ:
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[1]);
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[0]);
                mv.visitInsn(LCMP);
                Label l1 = new Label();
                Label l2 = new Label();
                mv.visitJumpInsn(IFNE, l1);
                mv.visitLdcInsn(1);
                mv.visitJumpInsn(GOTO, l2);
                mv.visitLabel(l1);
                mv.visitLdcInsn(0);
                mv.visitLabel(l2);
                storeJVMStackSmallIntegerValueToBalRegisterVariable(ctx, oprs[2]);
                break;
            case InstructionCodes.ILT:
                /* if less than, the result should be 1, so swap the params */
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[1]);
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[0]);
                mv.visitInsn(LCMP);
                storeJVMStackSmallIntegerValueToBalRegisterVariable(ctx, oprs[2]);
                break;
            case InstructionCodes.FLT:
                loadBalFloatRegisterVariableToJVMStack(ctx, oprs[1]);
                loadBalFloatRegisterVariableToJVMStack(ctx, oprs[0]);
                mv.visitInsn(DCMPG);
                storeJVMStackSmallIntegerValueToBalRegisterVariable(ctx, oprs[2]);
                break;
            case InstructionCodes.IGT:
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[0]);
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[1]);
                mv.visitInsn(LCMP);
                storeJVMStackSmallIntegerValueToBalRegisterVariable(ctx, oprs[2]);
                break;
            case InstructionCodes.FGT:
                loadBalFloatRegisterVariableToJVMStack(ctx, oprs[0]);
                loadBalFloatRegisterVariableToJVMStack(ctx, oprs[1]);
                mv.visitInsn(DCMPG);
                storeJVMStackSmallIntegerValueToBalRegisterVariable(ctx, oprs[2]);
                break;
            case InstructionCodes.BR_FALSE:
                loadBalSmallIntegerRegisterVariableToJVMStack(ctx, oprs[0]);
                /* 1 is true, 0 or -1 is false here */
                mv.visitJumpInsn(IFLE, labelMap.get(oprs[1]));
                break;
            case InstructionCodes.BR_TRUE:
                loadBalSmallIntegerRegisterVariableToJVMStack(ctx, oprs[0]);
                mv.visitJumpInsn(IFGT, labelMap.get(oprs[1]));
                break;
            case InstructionCodes.GOTO:
                mv.visitJumpInsn(GOTO, labelMap.get(oprs[0]));
                break;
            case InstructionCodes.I2F:
                loadBalIntegerRegisterVariableToJVMStack(ctx, oprs[0]);
                mv.visitInsn(L2D);
                storeJVMStackFloatValueToBalRegisterVariable(ctx, oprs[1]);
                break;
            default:
                System.out.println("*** ERROR UNKNOWN_OP: " + instr.getOpcode() + " -> execution may fail!");
            }
        }        
        mv.visitEnd();
    }
    
    private static void processCallInstr(MethodVisitor mv, JVMMethodContext ctx, 
            ConstantPoolEntry[] consts, int[] oprs) {
        FunctionInfo funcInfo = ((FunctionRefCPEntry) consts[oprs[0]]).getFunctionInfo();
        String pkgPath = funcInfo.getPackageInfo().getPkgPath();
        if (pkgPath.startsWith("ballerina.lang")) {
            return;
        }
        FunctionCallCPEntry funcCallCPE = (FunctionCallCPEntry) consts[oprs[1]];
        int[] argRegs = funcCallCPE.getArgRegs();
        BType[] paramTypes = funcInfo.getParamTypes();
        List<Integer> intRegIndices = new ArrayList<>();
        List<Integer> floatRegIndices = new ArrayList<>();
        for (int i = 0; i < argRegs.length; i++) {
            if (paramTypes[i].getTag() == TypeTags.INT_TAG) {
                intRegIndices.add(argRegs[i]);
            } else if (paramTypes[i].getTag() == TypeTags.FLOAT_TAG) {
                floatRegIndices.add(argRegs[i]);
            }
        }
        //TODO support all types
        for (int regIndex : intRegIndices) {
            loadBalIntegerRegisterVariableToJVMStack(ctx, regIndex);
        }
        for (int regIndex : floatRegIndices) {
            loadBalFloatRegisterVariableToJVMStack(ctx, regIndex);
        }
        String jvmClass = ballerinaPackageToJVMClass(pkgPath);
        String jvmClassDesc = jvmClass.replace('.', '/');
        String methodName = funcInfo.getName();
        String methodDesc = generateJVMMethodDesc(new MethodInfo(funcInfo));
        mv.visitMethodInsn(INVOKESTATIC, jvmClassDesc, methodName, methodDesc, false);
        //TODO implement with only stack operations, i.e. use DUP
        int index = ctx.lookupJVMObjectLocalVariableIndex(CALL_RESULT_OBJ_NAME);
        mv.visitVarInsn(ASTORE, index);
        
        int[] retRegs = funcCallCPE.getRetRegs();
        BType[] retTypes = funcInfo.getRetParamTypes();
        int regIndex;
        for (int i = 0; i < retRegs.length; i++) {
            regIndex = retRegs[i];
            //TODO support all types
            if (retTypes[i].getTag() == TypeTags.INT_TAG) {
                storeObjectArrayIntFieldValueToRegister(ctx, CALL_RESULT_OBJ_NAME, i, regIndex);
            } else if (retTypes[i].getTag() == TypeTags.FLOAT_TAG) {
                storeObjectArrayFloatFieldValueToRegister(ctx, CALL_RESULT_OBJ_NAME, i, regIndex);
            }
        }
    }
    
    private static void generateDefaultConstructor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitMaxs(1, 1);
        mv.visitVarInsn(ALOAD, 0); 
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitEnd();
    }
    
    private static String ballerinaPackageToJVMClass(String pkgPath) {
        if (".".equals(pkgPath)) {
            return DEFAULT_CLASS_NAME;
        } else {
            return pkgPath.replace('/', '.');
        }
    }
    
    public static BJVMPackage lookupPackage(String pkgPath) throws Exception {
        String className = ballerinaPackageToJVMClass(pkgPath);
        Class<?> clazz = new BJVMClassLoader().loadClass(className);
        return new BJVMPackage(pkgPath, clazz);
    }
    
    public static MethodInfo lookupMethodInfo(String pkgPath, String functionName) {
        return BJVMEnv.methodInfoMap.get(generateKeyForFuncID(pkgPath, functionName));
    }
    
    /**
     * This class represents a Ballerina JVM bytecode generated function.
     */
    public static class BJVMFunction {
        
        private Method method;
        
        private MethodInfo methodInfo;
        
        public BJVMFunction(Method method, MethodInfo methodInfo) {
            this.method = method;
            this.methodInfo = methodInfo;
        }
        
        public BValue[] invoke(BValue... params) throws Exception {
            /* in the critical path, so the translation logic should be 
             * optimized as much as possible */
            Object[] jvmParams = this.translateParams(params);
            Object[] result = (Object[]) method.invoke(null, jvmParams);
            return translateResult(result);
        }
        
        private Object[] translateParams(BValue[] params) {
            Object[] result = new Object[this.methodInfo.getParamCount()];
            int i = 0;
            for (int j : this.methodInfo.getIntParamLocations()) {
                result[i] = ((BInteger) params[j]).intValue();
                i++;
            }
            for (int j : this.methodInfo.getFloatParamLocations()) {
                result[i] = ((BFloat) params[j]).floatValue();
                i++;
            }
            //TODO support all types
            return result;
        }
        
        private BValue[] translateResult(Object[] funcResult) {
            BValue[] result = new BValue[this.methodInfo.getRetCount()];
            int[] intRetLocations = this.methodInfo.getIntRetLocations();
            int[] floatRetLocations = this.methodInfo.getFloatRetLocations();
            for (int i = 0; i < intRetLocations.length; i++) {
                result[intRetLocations[i]] = new BInteger((Long) funcResult[intRetLocations[i]]);
            }
            for (int i = 0; i < floatRetLocations.length; i++) {
                result[floatRetLocations[i]] = new BFloat((Double) funcResult[floatRetLocations[i]]);
            }
            //TODO support all types
            return result;
        }
        
    }
    
    /**
     * This class represents a Ballerina JVM bytecode generated package.
     */
    public static class BJVMPackage {
        
        private String pkgPath;
        
        private Class<?> clazz;
        
        public BJVMPackage(String pkgPath, Class<?> clazz) {
            this.pkgPath = pkgPath;
            this.clazz = clazz;
        }
        
        public BJVMFunction getFunction(String name) throws Exception {
            MethodInfo mi = lookupMethodInfo(this.pkgPath, name);
            if (mi == null) {
                throw new RuntimeException("The function '" + name + 
                        "' does not exist at package path '" + this.pkgPath + "'");
            }
            return new BJVMFunction(this.clazz.getMethod(name, this.generateJVMParams(mi)), mi);
        }
        
        private Class<?>[] generateJVMParams(MethodInfo mi) {
            List<Class<?>> params = new ArrayList<>();
            for (int i = 0; i < mi.getIntParamCount(); i++) {
                params.add(long.class);
            }
            for (int i = 0; i < mi.getFloatParamCount(); i++) {
                params.add(double.class);
            }
            //TODO support other types
            return params.toArray(new Class<?>[0]);
        }
        
    }
    
    private static Map<Integer, Label> allocateLabels(int startIP, int codeLength) {
        Map<Integer, Label> labelMap = new HashMap<>();
        for (int i = 0; i < codeLength; i++) {
            labelMap.put(i + startIP, new Label());
        }
        return labelMap;
    }
    
    private static void createFunctionResultObject(JVMMethodContext ctx, int retCount) {
        int index = ctx.lookupJVMObjectLocalVariableIndex(FUNC_RESULT_OBJECT_NAME);
        ctx.mv().visitLdcInsn(retCount);
        ctx.mv().visitTypeInsn(ANEWARRAY, Type.getInternalName(Object.class));
        ctx.mv().visitVarInsn(ASTORE, index);
    }
    
    private static void setObjectArrayIntFieldValue(JVMMethodContext ctx, String objectName, 
            int arrayIndex, int regValueIndex) {
        int varIndex = ctx.lookupJVMObjectLocalVariableIndex(objectName);
        ctx.mv().visitVarInsn(ALOAD, varIndex);
        ctx.mv().visitLdcInsn(arrayIndex);
        int valueIndex = ctx.lookupJVMLocalVariableIndex(BMemoryType.REGISTER, BValueType.INTEGER, regValueIndex);
        ctx.mv().visitVarInsn(LLOAD, valueIndex);
        ctx.mv().visitMethodInsn(INVOKESTATIC, Type.getInternalName(Long.class), "valueOf", 
                "(J)Ljava/lang/Long;", false);
        ctx.mv().visitInsn(AASTORE);
    }
    
    private static void setObjectArrayFloatFieldValue(JVMMethodContext ctx, String objectName, 
            int arrayIndex, int regValueIndex) {
        int varIndex = ctx.lookupJVMObjectLocalVariableIndex(objectName);
        ctx.mv().visitVarInsn(ALOAD, varIndex);
        ctx.mv().visitLdcInsn(arrayIndex);
        int valueIndex = ctx.lookupJVMLocalVariableIndex(BMemoryType.REGISTER, BValueType.FLOAT, regValueIndex);
        ctx.mv().visitVarInsn(DLOAD, valueIndex);
        ctx.mv().visitMethodInsn(INVOKESTATIC, Type.getInternalName(Double.class), "valueOf", 
                "(D)Ljava/lang/Double;", false);
        ctx.mv().visitInsn(AASTORE);
    }
    
    private static void storeObjectArrayIntFieldValueToRegister(JVMMethodContext ctx, String objectName, 
            int arrayIndex, int regIndex) {
        int varIndex = ctx.lookupJVMObjectLocalVariableIndex(objectName);
        ctx.mv().visitVarInsn(ALOAD, varIndex);
        ctx.mv().visitLdcInsn(arrayIndex);
        ctx.mv().visitInsn(AALOAD);
        ctx.mv().visitTypeInsn(CHECKCAST, Type.getInternalName(Long.class));
        ctx.mv().visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(Long.class), "longValue", "()J", false);
        storeJVMStackIntegerValueToBalRegisterVariable(ctx, regIndex);
    }
    
    private static void storeObjectArrayFloatFieldValueToRegister(JVMMethodContext ctx, String objectName, 
            int arrayIndex, int regIndex) {
        int varIndex = ctx.lookupJVMObjectLocalVariableIndex(objectName);
        ctx.mv().visitVarInsn(ALOAD, varIndex);
        ctx.mv().visitLdcInsn(arrayIndex);
        ctx.mv().visitInsn(AALOAD);
        ctx.mv().visitTypeInsn(CHECKCAST, Type.getInternalName(Double.class));
        ctx.mv().visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(Double.class), "doubleValue", "()D", false);
        storeJVMStackFloatValueToBalRegisterVariable(ctx, regIndex);
    }
    
    private static void loadBalIntegerLocalVariableToJVMStack(JVMMethodContext ctx, int localIndex) {
        int index = ctx.lookupJVMLocalVariableIndex(BMemoryType.LOCAL, BValueType.INTEGER, localIndex);
        loadJVMIntegerLocalVariableToStack(ctx, index);
    }
    
    private static void loadBalFloatLocalVariableToJVMStack(JVMMethodContext ctx, int localIndex) {
        int index = ctx.lookupJVMLocalVariableIndex(BMemoryType.LOCAL, BValueType.FLOAT, localIndex);
        loadJVMFloatLocalVariableToStack(ctx, index);
    }
    
    private static void loadBalIntegerRegisterVariableToJVMStack(JVMMethodContext ctx, int regIndex) {
        int index = ctx.lookupJVMLocalVariableIndex(BMemoryType.REGISTER, BValueType.INTEGER, regIndex);
        loadJVMIntegerLocalVariableToStack(ctx, index);
    }
    
    private static void loadBalFloatRegisterVariableToJVMStack(JVMMethodContext ctx, int regIndex) {
        int index = ctx.lookupJVMLocalVariableIndex(BMemoryType.REGISTER, BValueType.FLOAT, regIndex);
        loadJVMFloatLocalVariableToStack(ctx, index);
    }
    
    private static void storeJVMStackIntegerValueToBalLocalVariable(JVMMethodContext ctx, int localIndex) {
        int index = ctx.lookupJVMLocalVariableIndex(BMemoryType.LOCAL, BValueType.INTEGER, localIndex);
        storeJVMIntegerStackValueToLocalVariable(ctx, index);
    }
    
    private static void storeJVMStackFloatValueToBalLocalVariable(JVMMethodContext ctx, int localIndex) {
        int index = ctx.lookupJVMLocalVariableIndex(BMemoryType.LOCAL, BValueType.FLOAT, localIndex);
        storeJVMFloatStackValueToLocalVariable(ctx, index);
    }
    
    private static void storeJVMStackIntegerValueToBalRegisterVariable(JVMMethodContext ctx, int regIndex) {
        int index = ctx.lookupJVMLocalVariableIndex(BMemoryType.REGISTER, BValueType.INTEGER, regIndex);
        storeJVMIntegerStackValueToLocalVariable(ctx, index);
    }
    
    private static void storeJVMStackFloatValueToBalRegisterVariable(JVMMethodContext ctx, int regIndex) {
        int index = ctx.lookupJVMLocalVariableIndex(BMemoryType.REGISTER, BValueType.FLOAT, regIndex);
        storeJVMFloatStackValueToLocalVariable(ctx, index);
    }
    
    private static void storeJVMStackSmallIntegerValueToBalRegisterVariable(JVMMethodContext ctx, int regIndex) {
        int index = ctx.lookupJVMLocalVariableIndex(BMemoryType.REGISTER, BValueType.SMALL_INTEGER, regIndex);
        storeJVMSmallIntegerStackValueToLocalVariable(ctx, index);
    }
    
    private static void loadBalSmallIntegerRegisterVariableToJVMStack(JVMMethodContext ctx, int regIndex) {
        int index = ctx.lookupJVMLocalVariableIndex(BMemoryType.REGISTER, BValueType.SMALL_INTEGER, regIndex);
        loadJVMSmallIntegerLocalVariableToStack(ctx, index);
    }
    
    private static void storeIntegerConstantToBalRegister(JVMMethodContext ctx, int regIndex, long value) {
        int index = ctx.lookupJVMLocalVariableIndex(BMemoryType.REGISTER, BValueType.INTEGER, regIndex);
        storeIntegerConstantToJVMLocalVariable(ctx, index, value);
    }
    
    private static void storeSmallIntegerConstantToBalRegister(JVMMethodContext ctx, int regIndex, int value) {
        int index = ctx.lookupJVMLocalVariableIndex(BMemoryType.REGISTER, BValueType.SMALL_INTEGER, regIndex);
        storeSmallIntegerConstantToJVMLocalVariable(ctx, index, value);
    }
    
    private static void storeFloatConstantToBalRegister(JVMMethodContext ctx, int regIndex, double value) {
        int index = ctx.lookupJVMLocalVariableIndex(BMemoryType.REGISTER, BValueType.FLOAT, regIndex);
        storeFloatConstantToJVMLocalVariable(ctx, index, value);
    }
    
    private static void loadIntegerConstantToJVMStack(JVMMethodContext ctx, long value) {
        ctx.mv().visitLdcInsn(value);
    }
    
    private static void loadSmallIntegerConstantToJVMStack(JVMMethodContext ctx, int value) {
        ctx.mv().visitLdcInsn(value);
    }
    
    private static void loadFloatConstantToJVMStack(JVMMethodContext ctx, double value) {
        ctx.mv().visitLdcInsn(value);
    }
    
    private static void loadJVMIntegerLocalVariableToStack(JVMMethodContext ctx, int index) {
        ctx.mv().visitVarInsn(LLOAD, index);
    }
    
    private static void loadJVMFloatLocalVariableToStack(JVMMethodContext ctx, int index) {
        ctx.mv().visitVarInsn(DLOAD, index);
    }
    
    private static void loadJVMSmallIntegerLocalVariableToStack(JVMMethodContext ctx, int index) {
        ctx.mv().visitVarInsn(ILOAD, index);
    }
    
    private static void storeJVMIntegerStackValueToLocalVariable(JVMMethodContext ctx, int index) {
        ctx.mv().visitVarInsn(LSTORE, index);
    }
    
    private static void storeJVMFloatStackValueToLocalVariable(JVMMethodContext ctx, int index) {
        ctx.mv().visitVarInsn(DSTORE, index);
    }
    
    private static void storeJVMSmallIntegerStackValueToLocalVariable(JVMMethodContext ctx, int index) {
        ctx.mv().visitVarInsn(ISTORE, index);
    }
    
    private static void storeIntegerConstantToJVMLocalVariable(JVMMethodContext ctx, int index, long value) {
        loadIntegerConstantToJVMStack(ctx, value);
        ctx.mv().visitVarInsn(LSTORE, index);
    }
    
    private static void storeSmallIntegerConstantToJVMLocalVariable(JVMMethodContext ctx, int index, int value) {
        loadSmallIntegerConstantToJVMStack(ctx, value);
        ctx.mv().visitVarInsn(ISTORE, index);
    }
    
    private static void storeFloatConstantToJVMLocalVariable(JVMMethodContext ctx, int index, double value) {
        loadFloatConstantToJVMStack(ctx, value);
        ctx.mv().visitVarInsn(DSTORE, index);
    }
    
    private static enum BMemoryType {
        LOCAL, REGISTER
    }
    
    private static enum BValueType {
        INTEGER, FLOAT, SMALL_INTEGER
    }
    
    /**
     * This class represents a JVM method created from a Ballerina function.
     */
    public static class MethodInfo {
        
        private int[] intParamLocations;
        
        private int[] floatParamLocations;
        
        private int[] intRetLocations;
        
        private int[] floatRetLocations;
                        
        private int paramLength;
        
        private int[] callRetValueTypeValueLengths;
        
        private int callRetValueLength;
        
        private FunctionInfo functionInfo;
                        
        private int codeLength;
                
        public MethodInfo(FunctionInfo functionInfo) {
            this.functionInfo = functionInfo;
            this.processParams();
            this.processReturnValues();
            this.processFunctionInstructionLength();
            this.processCallRetValueMemoryAllocation();
        }

        private void processParams() {
            //TODO support all types
            List<Integer> intParamLocationList = new ArrayList<>();
            List<Integer> floatParamLocationList = new ArrayList<>();
            BType[] types = this.functionInfo.getParamTypes();
            for (int i = 0; i < types.length; i++) {
                BType type = types[i];
                this.paramLength++;
                if (type.getTag() == TypeTags.INT_TAG) {
                    this.paramLength++;
                    intParamLocationList.add(i);
                } else if (type.getTag() == TypeTags.FLOAT_TAG) {
                    this.paramLength++;
                    floatParamLocationList.add(i);
                }
            }
            this.intParamLocations = new int[intParamLocationList.size()];
            this.floatParamLocations = new int[floatParamLocationList.size()];
            for (int i = 0; i < intParamLocations.length; i++) {
                this.intParamLocations[i] = intParamLocationList.get(i);
            }
            for (int i = 0; i < floatParamLocations.length; i++) {
                this.floatParamLocations[i] = floatParamLocationList.get(i);
            }
        }
        
        private void processReturnValues() {
            List<Integer> intRetLocationList = new ArrayList<>();
            List<Integer> floatRetLocationList = new ArrayList<>();
            BType[] types = this.functionInfo.getRetParamTypes();
            for (int i = 0; i < types.length; i++) {
                BType type = types[i];
                if (type.getTag() == TypeTags.INT_TAG) {
                    intRetLocationList.add(i);
                } else if (type.getTag() == TypeTags.FLOAT_TAG) {
                    floatRetLocationList.add(i);
                }
            }
            this.intRetLocations = new int[intRetLocationList.size()];
            this.floatRetLocations = new int[floatRetLocationList.size()];
            for (int i = 0; i < intRetLocations.length; i++) {
                this.intRetLocations[i] = intRetLocationList.get(i);
            }
            for (int i = 0; i < floatRetLocations.length; i++) {
                this.floatRetLocations[i] = floatRetLocationList.get(i);
            }
        }
        
        private void processFunctionInstructionLength() {
            //TODO need a correct a way to calculate the code length of the method
            this.codeLength = this.getInstrs().length - this.getStartIP();
        }
        
        private void processCallRetValueMemoryAllocation() {
            //TODO support all types
            /* [0] int, [1] float */
            this.callRetValueTypeValueLengths = new int[2];
            int[] tmpVals;
            Instruction instr;
            Instruction[] instrs = this.getInstrs();
            for (int i = 0; i < this.codeLength; i++) {
                instr = instrs[i + this.getStartIP()];
                if (instr.getOpcode() == InstructionCodes.CALL) {
                    FunctionInfo callFuncInfo = ((FunctionRefCPEntry) this.getConsts()[instr.getOperands()[0]]).
                            getFunctionInfo();
                    if (callFuncInfo.getName().startsWith("ballerina.lang")) {
                        continue;
                    }
                    tmpVals = this.calculateRetValueTypeLengthsForCall(callFuncInfo);
                    for (int j = 0; j < this.callRetValueTypeValueLengths.length; j++) {
                        if (tmpVals[j] > this.callRetValueTypeValueLengths[j]) {
                            this.callRetValueTypeValueLengths[j] = tmpVals[j];
                        }
                    }
                }
            }
            this.callRetValueLength = this.callRetValueTypeValueLengths[0] * 
                    2 + this.callRetValueTypeValueLengths[1] * 2;
        }
        
        private int[] calculateRetValueTypeLengthsForCall(FunctionInfo callFuncInfo) {
            //TODO support all types
            int[] result = new int[2];
            BType[] types = callFuncInfo.getRetParamTypes();
            for (BType type : types) {
                if (type.getTag() == TypeTags.INT_TAG) {
                    result[0]++;
                } else if (type.getTag() == TypeTags.FLOAT_TAG) {
                    result[1]++;
                }
            }
            return result;
        }
        
        public int[] getCallRetValueTypeValueLengths() {
            return callRetValueTypeValueLengths;
        }
        
        public ConstantPoolEntry[] getConsts() {
            return this.functionInfo.getPackageInfo().getConstPool();
        }
        
        public Instruction[] getInstrs() {
            return this.functionInfo.getPackageInfo().getInstructions();
        }
        
        public int getCallRetValueLength() {
            return callRetValueLength;
        }
        
        public String getName() {
            return this.functionInfo.getName();
        }
        
        public int getCodeLength() {
            return codeLength;
        }
        
        public int getStartIP() {
            return this.functionInfo.getDefaultWorkerInfo().getCodeAttributeInfo().getCodeAddrs();
        }
        
        public int getParamCount() {
            return this.functionInfo.getParamTypes().length;
        }
        
        public int getParamLength() {
            return paramLength;
        }
        
        public int getRetCount() {
            return this.functionInfo.getRetParamTypes().length;
        }
        
        public int[] getIntParamLocations() {
            return intParamLocations;
        }
        
        public int[] getFloatParamLocations() {
            return floatParamLocations;
        }
        
        public int getIntParamCount() {
            return this.intParamLocations.length;
        }
        
        public int getFloatParamCount() {
            return this.floatParamLocations.length;
        }
        
        public int getIntRetCount() {
            return intRetLocations.length;
        }
        
        public int getFloatRetCount() {
            return floatRetLocations.length;
        }
        
        public int[] getIntRetLocations() {
            return intRetLocations;
        }
        
        public int[] getFloatRetLocations() {
            return floatRetLocations;
        }
        
    }
    
    /**
     * This represents the JVM method context in code generation.
     */
    public static class JVMMethodContext {
        
        private Map<String, Integer> localVariableLocationMap = new HashMap<>();
        
        private MethodVisitor mv;
        
        private int currentLocalMemoryLocation = 0;
        
        private MethodInfo methodInfo;
                                        
        public JVMMethodContext(MethodVisitor mv, MethodInfo methodInfo) {
            this.mv = mv;
            this.methodInfo = methodInfo;
            this.currentLocalMemoryLocation += this.methodInfo.getParamLength() + 
                    this.methodInfo.getCallRetValueLength();
        }
        
        public MethodVisitor mv() {
            return mv;
        }
        
        private int lookupReturnValueLocation(BMemoryType memoryType, BValueType valueType, int index) {
            if (BMemoryType.REGISTER.equals(memoryType)) {
                switch (valueType) {
                case INTEGER:
                    if (this.methodInfo.getCallRetValueTypeValueLengths()[0] > index) {
                        /* a Java long (Ballerina integer) takes two slots + 
                         * offset the space taken by method params */
                        return index * 2 + this.methodInfo.getParamLength();
                    }
                    break;
                case FLOAT:
                    if (this.methodInfo.getCallRetValueTypeValueLengths()[1] > index) {
                        return (this.methodInfo.getCallRetValueTypeValueLengths()[0] + index) * 2 + 
                                this.methodInfo.getParamLength();
                    }
                    break;
                case SMALL_INTEGER:
                    /* not used as return values */
                    break;
                 //TODO add other types
                }
            }
            return -1;
        }
        
        private int lookupMethodParamLocation(BMemoryType memoryType, BValueType valueType, int index) {
            if (BMemoryType.LOCAL.equals(memoryType)) {
                switch (valueType) {
                case INTEGER:
                    if (this.methodInfo.getIntParamCount() > index) {
                        /* a Java long (Ballerina integer) takes two slots */
                        return index * 2;
                    }
                    break;
                case FLOAT:
                    if (this.methodInfo.getFloatParamCount() > index) {
                        /* float params comes after all the int params */
                        return (this.methodInfo.getIntParamCount() + index) * 2;
                    }
                    break;
                case SMALL_INTEGER:
                    /* not used as method params */
                    break;
                 //TODO add other types
                }
            }
            return -1;
        }
        
        public int lookupJVMObjectLocalVariableIndex(String name) {
            String key = this.generateLocalVariableLocationKey(name);
            Integer location = this.localVariableLocationMap.get(key);
            if (location == null) {
                location = this.currentLocalMemoryLocation;
                this.currentLocalMemoryLocation++;
                this.localVariableLocationMap.put(key, location);
            }
            return location;
        }
        
        public int lookupJVMLocalVariableIndex(BMemoryType memoryType, BValueType valueType, int index) {
            String key = this.generateLocalVariableLocationKey(memoryType, valueType, index);
            Integer location = this.localVariableLocationMap.get(key);
            if (location == null) {
                location = this.lookupMethodParamLocation(memoryType, valueType, index);
                if (location < 0) {
                    location = this.lookupReturnValueLocation(memoryType, valueType, index);
                    if (location < 0) {
                        /* if not part of the method parameters nor return values of calls */
                        location = this.currentLocalMemoryLocation;
                        this.currentLocalMemoryLocation++;
                        if (this.isWideMemoryType(valueType)) {
                            this.currentLocalMemoryLocation++;
                        }
                    }
                }
                this.localVariableLocationMap.put(key, location);
            }
            return location;
        }
        
        private String generateLocalVariableLocationKey(BMemoryType memoryType, BValueType valueType, int index) {
            return memoryType.name() + "_" + valueType.name() + "_" + index;
        }
        
        private String generateLocalVariableLocationKey(String name) {
            return "OBJECT_" + name;
        }
        
        private boolean isWideMemoryType(BValueType type) {
            if (type.equals(BValueType.INTEGER) || type.equals(BValueType.FLOAT)) {
                return true;
            }
            //TODO support all types
            return false;
        }
        
        public MethodInfo getMethodInfo() {
            return methodInfo;
        }
        
    }
    
    private static String generateJVMMethodDesc(MethodInfo methodInfo) {
        StringBuffer result = new StringBuffer("(");
        for (int i = 0; i < methodInfo.getIntParamCount(); i++) {
            result.append("J");
        }
        for (int i = 0; i < methodInfo.getFloatParamCount(); i++) {
            result.append("D");
        }
        result.append(")[Ljava/lang/Object;");
        return result.toString();
    }
        
    private static void printBVals(BValue[] vals) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vals.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            if (vals[i].getType() == BTypes.typeInt) {
                builder.append(((BInteger) vals[i]).intValue());
            } else if (vals[i].getType() == BTypes.typeFloat) {
                builder.append(((BFloat) vals[i]).floatValue());
            }
            //TODO support all types
        }
        builder.append("]");
        System.out.println(builder.toString());
    }
    
    /**
     * Custom JVM class loader for Ballerina bytecode. 
     */
    private static class BJVMClassLoader extends ClassLoader {
        
        public Class<?> findClass(String name) {
            byte[] b = this.loadClassData(name);
            return defineClass(name, b, 0, b.length);
        }
        
        public byte[] loadClassData(String name) {
            byte[] data = BJVMEnv.classData.get(name);
            if (data == null) {
                throw new RuntimeException("The class '" + name + "' is not registered in the environment");
            }
            return data;
        }
    }
    
    public static void main(String[] args) throws Exception {
        Path progDir = Paths.get("/home/laf/Desktop/bvm");
        Path sourceFile = Paths.get("sample.bal");

        BLangProgramLoader loader = new BLangProgramLoader();
        ProgramFile programFile = loader.loadProgramFile(progDir, sourceFile);
        
        BJVMEnv.registerProgramFile(programFile);
        BJVMPackage pkg = BJVMEnv.lookupPackage(".");
        
        int count1 = 1000000, count2 = 200000;
        int fibn = 33;
        int x = 7, b = 100, c = 30;
        
        fibBalJVM(pkg, fibn);
        fibBalVM(programFile, fibn);
        loopTestBalJVM(pkg, count1, x);
        loopTestBalVM(programFile, count1, x);
        smallOpMultipleCallsBalJVM(pkg, count2, b, c);
        smallOpMultipleCallsBalVM(programFile, count2, b, c);
    }
    
    public static void fibBalVM(ProgramFile programFile, long n) {
        System.out.println("#Fibonnacci BAL VM");
        long start = System.currentTimeMillis();
        BValue[] vals = BLangFunctions.invokeNew(programFile, ".", "fib", new BValue[] { new BInteger(n) });
        long end = System.currentTimeMillis();
        printBVals(vals);
        System.out.println("Time: " + (end - start) + " ms.");
    }
    
    public static void loopTestBalVM(ProgramFile programFile, long count, long x) {
        System.out.println("#LoopTest BAL VM");
        long start = System.currentTimeMillis();
        BValue[] vals = BLangFunctions.invokeNew(programFile, ".", "loopTest", 
                new BValue[] { new BInteger(count), new BInteger(x) });
        long end = System.currentTimeMillis();
        printBVals(vals);
        System.out.println("Time: " + (end - start) + " ms.");
    }
    
    public static void smallOpMultipleCallsBalVM(ProgramFile programFile, long count, long b, long c) {
        System.out.println("#Small Op Multiple Calls BAL VM");
        long start = System.currentTimeMillis();
        BValue[] vals = null;
        for (long i = 0; i < count; i++) {
            vals = BLangFunctions.invokeNew(programFile, ".", "smallOp", 
                    new BValue[] { new BInteger(i), new BInteger(b), new BInteger(c) });
        }
        long end = System.currentTimeMillis();
        printBVals(vals);
        System.out.println("Time: " + (end - start) + " ms.");
    }
    
    public static void fibBalJVM(BJVMPackage pkg, long n)throws Exception {
        System.out.println("#Fibonnacci BAL JVM");
        BJVMFunction func = pkg.getFunction("fib");
        long start = System.currentTimeMillis();
        BValue[] vals = func.invoke(new BInteger(n));
        long end = System.currentTimeMillis();
        printBVals(vals);
        System.out.println("Time: " + (end - start) + " ms.");
    }
    
    public static void loopTestBalJVM(BJVMPackage pkg, int count, int x)throws Exception {
        System.out.println("#LoopTest BAL JVM");
        BJVMFunction func = pkg.getFunction("loopTest");
        long start = System.currentTimeMillis();
        BValue[] vals = func.invoke(new BInteger(count), new BInteger(x));
        long end = System.currentTimeMillis();
        printBVals(vals);
        System.out.println("Time: " + (end - start) + " ms.");
    }
    
    public static void smallOpMultipleCallsBalJVM(BJVMPackage pkg, long count, long b, long c)throws Exception {
        System.out.println("#Small Op Multiple Calls BAL JVM");
        BJVMFunction func = pkg.getFunction("smallOp");
        long start = System.currentTimeMillis();
        BValue[] vals = null;
        for (long i = 0; i < count; i++) {
            vals = func.invoke(new BInteger(i), new BInteger(b), new BInteger(c));
        }
        long end = System.currentTimeMillis();
        printBVals(vals);
        System.out.println("Time: " + (end - start) + " ms.");
    }
    
}
