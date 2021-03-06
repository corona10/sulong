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
package com.oracle.truffle.llvm.parser;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.ValueInstructionVisitor;
import com.oracle.truffle.llvm.runtime.LLVMException;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class StackManager {

    private StackManager() {
    }

    public static FrameDescriptor createRootFrame() {
        final FrameDescriptor rootFrame = new FrameDescriptor();
        rootFrame.addFrameSlot(LLVMStack.FRAME_ID, PointerType.VOID, FrameSlotKind.Object);
        return rootFrame;
    }

    public static FrameDescriptor createFrame(FunctionDefinition function) {
        final FrameDescriptor frame = new FrameDescriptor();

        frame.addFrameSlot(LLVMException.FRAME_SLOT_ID, null, FrameSlotKind.Object);
        frame.addFrameSlot(LLVMStack.FRAME_ID, PointerType.VOID, FrameSlotKind.Object);

        for (FunctionParameter parameter : function.getParameters()) {
            Type type = parameter.getType();
            if (parameter.isSourceVariable()) {
                type = type.shallowCopy();
            }
            frame.addFrameSlot(parameter.getName(), type, Type.getFrameSlotKind(type));
        }

        final StackAllocationFunctionVisitor functionVisitor = new StackAllocationFunctionVisitor(frame);
        function.accept((FunctionVisitor) functionVisitor);

        return frame;
    }

    private static final class StackAllocationFunctionVisitor extends ValueInstructionVisitor implements FunctionVisitor {

        private final FrameDescriptor frame;

        private StackAllocationFunctionVisitor(FrameDescriptor frame) {
            this.frame = frame;
        }

        @Override
        public void visitValueInstruction(ValueInstruction valueInstruction) {
            final String slotName = valueInstruction.getName();

            Type type = valueInstruction.getType();
            final FrameSlotKind slotKind = Type.getFrameSlotKind(type);

            if (valueInstruction.isSourceVariable()) {
                // when we set the sourcetype at runtime this type needs to be distinct
                type = type.shallowCopy();
            }

            frame.addFrameSlot(slotName, type, slotKind);
        }

        @Override
        public void visit(InstructionBlock block) {
            block.accept(this);
        }
    }
}
