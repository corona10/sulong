/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.base;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public final class LLVMFrameNullerUtil {
    private LLVMFrameNullerUtil() {
    }

    public static void nullFrameSlot(VirtualFrame frame, FrameSlot frameSlot) {
        FrameSlotKind kind = frameSlot.getKind();
        CompilerAsserts.partialEvaluationConstant(kind);
        if (kind == FrameSlotKind.Object) {
            // object frame slots always need to be nulled (otherwise we would impact GC)
            nullObject(frame, frameSlot);
        } else if (CompilerDirectives.inCompiledCode()) {
            // Nulling primitive frame slots is only necessary in compiled code (otherwise, we would
            // compute values that are only used in framestates). Tthis code must NOT be moved to a
            // separate method as it would cause endless deopts (code method might be unresolved
            // because it was never executed). For the same reason, we also must NOT use a switch
            // statement.
            if (kind == FrameSlotKind.Boolean) {
                frame.setBoolean(frameSlot, false);
            } else if (kind == FrameSlotKind.Byte) {
                frame.setByte(frameSlot, (byte) 0);
            } else if (kind == FrameSlotKind.Int) {
                frame.setInt(frameSlot, 0);
            } else if (kind == FrameSlotKind.Long) {
                frame.setLong(frameSlot, 0L);
            } else if (kind == FrameSlotKind.Float) {
                frame.setFloat(frameSlot, 0f);
            } else if (kind == FrameSlotKind.Double) {
                frame.setDouble(frameSlot, 0d);
            } else {
                throw new UnsupportedOperationException("unexpected frameslot kind");
            }
        }
    }

    private static void nullAddress(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setObject(frameSlot, LLVMNativePointer.createNull());
    }

    private static void nullIVarBit(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setObject(frameSlot, LLVMIVarBit.createNull());
    }

    private static void null80BitFloat(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setObject(frameSlot, new LLVM80BitFloat(false, 0, 0));
    }

    private static void nullFunction(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setObject(frameSlot, LLVMNativePointer.createNull());
    }

    private static void nullObject(VirtualFrame frame, FrameSlot frameSlot) {
        CompilerAsserts.partialEvaluationConstant(frameSlot.getInfo());
        CompilerAsserts.partialEvaluationConstant(frameSlot.getInfo() == null);
        if (frameSlot.getInfo() != null) {
            Type type = (Type) frameSlot.getInfo();
            CompilerAsserts.partialEvaluationConstant(Type.isFunctionOrFunctionPointer(type));
            CompilerAsserts.partialEvaluationConstant(type instanceof VectorType);
            CompilerAsserts.partialEvaluationConstant(type instanceof VariableBitWidthType);
            CompilerAsserts.partialEvaluationConstant(type instanceof PrimitiveType && ((PrimitiveType) type).getPrimitiveKind() == PrimitiveKind.X86_FP80);
            if (Type.isFunctionOrFunctionPointer(type)) {
                nullFunction(frame, frameSlot);
                return;
            } else if (type instanceof VectorType && ((VectorType) type).getElementType() instanceof PrimitiveType) {
                nullVector(frame, frameSlot, ((PrimitiveType) ((VectorType) type).getElementType()).getPrimitiveKind());
                return;
            } else if (type instanceof VectorType && ((VectorType) type).getElementType() instanceof PointerType) {
                frame.setObject(frameSlot, LLVMPointerVector.createNullVector());
                return;
            } else if (type instanceof VariableBitWidthType) {
                nullIVarBit(frame, frameSlot);
                return;
            } else if (type instanceof PrimitiveType && ((PrimitiveType) type).getPrimitiveKind() == PrimitiveKind.X86_FP80) {
                null80BitFloat(frame, frameSlot);
                return;
            }
        }

        // This is a best effort approach. It could still be that LLVMAddress clashes with some
        // other class.
        nullAddress(frame, frameSlot);
    }

    private static void nullVector(VirtualFrame frame, FrameSlot frameSlot, PrimitiveKind elementType) {
        CompilerAsserts.partialEvaluationConstant(elementType);
        switch (elementType) {
            case DOUBLE:
                frame.setObject(frameSlot, LLVMDoubleVector.create(null));
                break;
            case FLOAT:
                frame.setObject(frameSlot, LLVMFloatVector.create(null));
                break;
            case I1:
                frame.setObject(frameSlot, LLVMI1Vector.create(null));
                break;
            case I16:
                frame.setObject(frameSlot, LLVMI16Vector.create(null));
                break;
            case I32:
                frame.setObject(frameSlot, LLVMI32Vector.create(null));
                break;
            case I64:
                frame.setObject(frameSlot, LLVMI64Vector.create(null));
                break;
            case I8:
                frame.setObject(frameSlot, LLVMI8Vector.create(null));
                break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError();

        }
    }
}
