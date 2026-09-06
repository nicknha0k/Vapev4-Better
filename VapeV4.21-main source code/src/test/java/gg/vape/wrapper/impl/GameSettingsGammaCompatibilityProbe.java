package gg.vape.wrapper.impl;

import java.io.IOException;
import java.io.InputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class GameSettingsGammaCompatibilityProbe {
    private GameSettingsGammaCompatibilityProbe() {
    }

    public static void main(String[] arguments) throws IOException {
        BoxingCalls boxingCalls = new BoxingCalls();
        try (InputStream classBytes = GameSettingsGammaCompatibilityProbe.class
                .getResourceAsStream("/gg/vape/wrapper/impl/GameSettings.class")) {
            if (classBytes == null) {
                throw new AssertionError("GameSettings.class was not found");
            }
            new ClassReader(classBytes).accept(boxingCalls, ClassReader.SKIP_DEBUG);
        }

        require(boxingCalls.setterFound, "gamma setter y(float) was not found");
        require(boxingCalls.doubleValueOfCalls == 1,
                "modern gamma setter must box exactly one Double");
        require(boxingCalls.floatValueOfCalls == 0,
                "modern gamma setter must not store a Float");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class BoxingCalls extends ClassVisitor {
        private boolean setterFound;
        private int doubleValueOfCalls;
        private int floatValueOfCalls;

        private BoxingCalls() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                String signature, String[] exceptions) {
            if (!"y".equals(name) || !"(F)V".equals(descriptor)) {
                return null;
            }
            setterFound = true;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String name,
                        String descriptor, boolean isInterface) {
                    if (opcode == Opcodes.INVOKESTATIC && "valueOf".equals(name)) {
                        if ("java/lang/Double".equals(owner)
                                && "(D)Ljava/lang/Double;".equals(descriptor)) {
                            doubleValueOfCalls++;
                        }
                        if ("java/lang/Float".equals(owner)
                                && "(F)Ljava/lang/Float;".equals(descriptor)) {
                            floatValueOfCalls++;
                        }
                    }
                }
            };
        }
    }
}
