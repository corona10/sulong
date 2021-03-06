/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.op;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.ForeignToComparableValueNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.LLVMAddressEQNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.LLVMAddressEqualsNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.LLVMAddressNEQNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.LLVMForeignEqualsNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.LLVMManagedEqualsNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.LLVMNativeEqualsNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.ManagedToComparableValueNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.NativeToComparableValueNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.ToComparableValueNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalReadNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObject;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMPointerCompareNode extends LLVMExpressionNode {

    public enum Kind {
        ULT,
        UGT,
        UGE,
        ULE,
        SLE,
        SGT,
        SGE,
        SLT,
        EQ,
        NEQ,
    }

    public static LLVMExpressionNode create(Kind kind, LLVMExpressionNode l, LLVMExpressionNode r) {
        switch (kind) {
            case SLT:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long val1, long val2) {
                        return val1 < val2;
                    }
                }, l, r);

            case SGE:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long val1, long val2) {
                        return val1 >= val2;
                    }
                }, l, r);
            case SGT:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long val1, long val2) {
                        return val1 > val2;
                    }
                }, l, r);
            case SLE:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long val1, long val2) {
                        return val1 <= val2;
                    }
                }, l, r);
            case UGE:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long val1, long val2) {
                        return Long.compareUnsigned(val1, val2) >= 0;
                    }
                }, l, r);
            case UGT:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long val1, long val2) {
                        return Long.compareUnsigned(val1, val2) > 0;
                    }
                }, l, r);
            case ULE:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long val1, long val2) {
                        return Long.compareUnsigned(val1, val2) <= 0;
                    }
                }, l, r);
            case ULT:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long val1, long val2) {
                        return Long.compareUnsigned(val1, val2) < 0;
                    }
                }, l, r);

            case EQ:
                return LLVMAddressEQNodeGen.create(l, r);
            case NEQ:
                return LLVMAddressNEQNodeGen.create(l, r);
            default:
                throw new AssertionError();

        }
    }

    protected abstract static class NativePointerCompare {

        abstract boolean compare(long val1, long val2);

    }

    private final NativePointerCompare op;

    public LLVMPointerCompareNode(NativePointerCompare op) {
        this.op = op;
    }

    protected abstract static class ForeignToComparableValue extends LLVMNode {

        abstract long execute(TruffleObject obj);

        public static ForeignToComparableValue create() {
            return ForeignToComparableValueNodeGen.create();
        }

        @Specialization
        protected long doForeign(LLVMTypedForeignObject obj) {
            return getHashCode(obj.getForeign());
        }

        @Fallback
        protected long doOther(TruffleObject obj) {
            return getHashCode(obj);
        }
    }

    @ImportStatic(ForeignToLLVMType.class)
    protected abstract static class ManagedToComparableValue extends LLVMNode {

        abstract long execute(Object obj);

        @Specialization
        protected long doAddress(long address) {
            return address;
        }

        @Specialization
        protected long doManagedMalloc(LLVMVirtualAllocationAddress address) {
            if (address.isNull()) {
                return address.getOffset();
            } else {
                return getHashCode(address.getObject()) + address.getOffset();
            }
        }

        @Specialization
        protected long doManaged(LLVMManagedPointer address,
                        @Cached("create()") ForeignToComparableValue toComparable) {
            return toComparable.execute(address.getObject()) + address.getOffset();
        }

        @Specialization
        protected long doLLVMBoxedPrimitive(LLVMBoxedPrimitive address,
                        @Cached("create(I64)") ForeignToLLVM toLLVM) {
            return (long) toLLVM.executeWithTarget(address.getValue());
        }

        public static ManagedToComparableValue create() {
            return ManagedToComparableValueNodeGen.create();
        }
    }

    protected abstract static class NativeToComparableValue extends LLVMNode {

        protected abstract long execute(Object obj, LLVMObjectNativeLibrary lib);

        @Specialization(guards = "lib.isPointer(obj)")
        protected long doPointer(Object obj, LLVMObjectNativeLibrary lib) {
            try {
                return lib.asPointer(obj);
            } catch (InteropException ex) {
                throw ex.raise();
            }
        }

        @Specialization(guards = "!lib.isPointer(obj)")
        @SuppressWarnings("unused")
        protected long doManaged(Object obj, LLVMObjectNativeLibrary lib,
                        @Cached("create()") ManagedToComparableValue toComparable) {
            return toComparable.execute(obj);
        }
    }

    protected abstract static class ToComparableValue extends LLVMNode {

        protected abstract long execute(Object obj);

        @Specialization(guards = "lib.guard(obj)")
        protected long doNativeCached(Object obj,
                        @Cached("createCached(obj)") LLVMObjectNativeLibrary lib,
                        @Cached("createToComparable()") NativeToComparableValue toComparable) {
            return doNative(obj, lib, toComparable);
        }

        @Specialization(replaces = "doNativeCached", guards = "lib.guard(obj)")
        protected long doNative(Object obj,
                        @Cached("createGeneric()") LLVMObjectNativeLibrary lib,
                        @Cached("createToComparable()") NativeToComparableValue toComparable) {
            return toComparable.execute(obj, lib);
        }

        static NativeToComparableValue createToComparable() {
            return NativeToComparableValueNodeGen.create();
        }
    }

    @TruffleBoundary
    private static int getHashCode(Object address) {
        return address.hashCode();
    }

    @Child private ToComparableValue convertVal1 = ToComparableValueNodeGen.create();
    @Child private ToComparableValue convertVal2 = ToComparableValueNodeGen.create();

    @Specialization
    protected boolean doGenericCompare(Object val1, Object val2) {
        return op.compare(convertVal1.execute(val1), convertVal2.execute(val2));
    }

    abstract static class LLVMForeignEqualsNode extends LLVMNode {

        abstract boolean execute(Env env, TruffleObject obj1, TruffleObject obj2);

        boolean isHostObject(Env env, TruffleObject obj) {
            return env.isHostObject(obj);
        }

        @Specialization(guards = {"isHostObject(env, obj1)", "isHostObject(env, obj2)"})
        protected boolean doJava(Env env, TruffleObject obj1, TruffleObject obj2) {
            return env.asHostObject(obj1) == env.asHostObject(obj2);
        }

        @Specialization
        protected boolean doForeign(Env env, LLVMTypedForeignObject obj1, LLVMTypedForeignObject obj2,
                        @Cached("create()") LLVMForeignEqualsNode equals) {
            return equals.execute(env, obj1.getForeign(), obj2.getForeign());
        }

        @Fallback
        protected boolean doOther(@SuppressWarnings("unused") Env env, TruffleObject obj1, TruffleObject obj2) {
            return obj1 == obj2;
        }

        public static LLVMForeignEqualsNode create() {
            return LLVMForeignEqualsNodeGen.create();
        }
    }

    abstract static class LLVMManagedEqualsNode extends LLVMNode {

        abstract boolean execute(Object val1, Object val2);

        @Specialization
        protected boolean doForeign(LLVMManagedPointer obj1, LLVMManagedPointer obj2,
                        @Cached("create()") LLVMForeignEqualsNode equals,
                        @Cached("getContextReference()") ContextReference<LLVMContext> ctxRef) {
            return equals.execute(ctxRef.get().getEnv(), obj1.getObject(), obj2.getObject()) && obj1.getOffset() == obj2.getOffset();
        }

        @Specialization
        protected boolean doGlobal(LLVMGlobal g1, LLVMGlobal g2) {
            return g1 == g2;
        }

        @Specialization
        protected boolean doVirtual(LLVMVirtualAllocationAddress v1, LLVMVirtualAllocationAddress v2) {
            return v1.getObject() == v2.getObject() && v1.getOffset() == v2.getOffset();
        }

        @Specialization
        protected boolean doFunctionDescriptor(LLVMFunctionDescriptor f1, LLVMFunctionDescriptor f2) {
            return f1 == f2;
        }

        @Specialization
        protected boolean doForeign(LLVMGlobal g1, LLVMManagedPointer obj2,
                        @Cached("create()") LLVMManagedEqualsNode recursive,
                        @Cached("create()") LLVMGlobalReadNode.ReadObjectNode readGlobalNode) {
            Object value = readGlobalNode.execute(g1);
            return recursive.execute(value, obj2);
        }

        @Specialization
        protected boolean doForeign(LLVMManagedPointer obj1, LLVMGlobal g2,
                        @Cached("create()") LLVMManagedEqualsNode recursive,
                        @Cached("create()") LLVMGlobalReadNode.ReadObjectNode readGlobalNode) {
            Object value = readGlobalNode.execute(g2);
            return recursive.execute(obj1, value);
        }

        protected boolean isNative(LLVMPointer p) {
            return LLVMNativePointer.isInstance(p);
        }

        @Specialization(guards = "isNative(p1) || isNative(p2)")
        protected boolean doManagedNative(LLVMPointer p1, LLVMPointer p2) {
            // the case where both pointers are native is handled earlier, so one has to be managed
            assert LLVMManagedPointer.isInstance(p1) || LLVMManagedPointer.isInstance(p2);
            // one of the pointers is native, the other not, so they can't be equal
            return false;
        }

        @Specialization(guards = "val1.getClass() != val2.getClass()")
        @SuppressWarnings("unused")
        protected boolean doDifferentType(Object val1, Object val2) {
            // different type, and at least one of them is managed, and not a pointer
            // these objects can not have the same address
            return false;
        }

        public static LLVMManagedEqualsNode create() {
            return LLVMManagedEqualsNodeGen.create();
        }
    }

    abstract static class LLVMNativeEqualsNode extends LLVMNode {

        abstract boolean execute(Object val1, LLVMObjectNativeLibrary lib1,
                        Object val2, LLVMObjectNativeLibrary lib2);

        @Specialization(guards = {"lib1.isPointer(val1)", "lib2.isPointer(val2)"})
        protected boolean doPointerPointer(Object val1, LLVMObjectNativeLibrary lib1,
                        Object val2, LLVMObjectNativeLibrary lib2) {
            try {
                return lib1.asPointer(val1) == lib2.asPointer(val2);
            } catch (InteropException ex) {
                throw ex.raise();
            }
        }

        @Specialization(guards = "!lib1.isPointer(val1) || !lib2.isPointer(val2)")
        protected boolean doOther(Object val1, @SuppressWarnings("unused") LLVMObjectNativeLibrary lib1,
                        Object val2, @SuppressWarnings("unused") LLVMObjectNativeLibrary lib2,
                        @Cached("create()") LLVMManagedEqualsNode managedEquals) {
            return managedEquals.execute(val1, val2);
        }
    }

    abstract static class LLVMAddressEqualsNode extends LLVMNode {

        abstract boolean execute(Object val1, Object val2);

        @Specialization(guards = {"lib1.guard(val1)", "lib2.guard(val2)"})
        protected boolean doCached(Object val1, Object val2,
                        @Cached("createCached(val1)") LLVMObjectNativeLibrary lib1,
                        @Cached("createCached(val2)") LLVMObjectNativeLibrary lib2,
                        @Cached("createEquals()") LLVMNativeEqualsNode equals) {
            return equals.execute(val1, lib1, val2, lib2);
        }

        @Specialization(replaces = "doCached", guards = {"lib.guard(val1)", "lib.guard(val2)"})
        protected boolean doGeneric(Object val1, Object val2,
                        @Cached("createGeneric()") LLVMObjectNativeLibrary lib,
                        @Cached("createEquals()") LLVMNativeEqualsNode equals) {
            return equals.execute(val1, lib, val2, lib);
        }

        static LLVMNativeEqualsNode createEquals() {
            return LLVMNativeEqualsNodeGen.create();
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    abstract static class LLVMAddressEQNode extends LLVMExpressionNode {

        @Child private LLVMAddressEqualsNode equals = LLVMAddressEqualsNodeGen.create();

        @Specialization
        protected boolean doCompare(Object val1, Object val2) {
            return equals.execute(val1, val2);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    abstract static class LLVMAddressNEQNode extends LLVMExpressionNode {

        @Child private LLVMAddressEqualsNode equals = LLVMAddressEqualsNodeGen.create();

        @Specialization
        protected boolean doCompare(Object val1, Object val2) {
            return !equals.execute(val1, val2);
        }
    }
}
