package gg.vape.asm.matcher;


public abstract class InstructionPattern {
    public static final int PUTFIELD;
    private static String opaqueMarker;
    private String descriptor;
    private int opcode;
    private String owner;
    private String name;
    public static final int GETSTATIC;
    public static final int PUTSTATIC;
    public static final int INVOKESTATIC;
    public static final int INVOKEVIRTUAL;
    public static final int INVOKESPECIAL;
    public static final int GETFIELD;
    public static final int INVOKEINTERFACE;

    public static String getOpaqueMarker() {
        return opaqueMarker;
    }

    public InstructionPattern(int opcode, String owner, String name, String descriptor) {
        owner = owner.replace(".", "/");
        descriptor = descriptor.replace(".", "/");
        this.opcode = opcode;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
    }

    public String getName() {
        return this.name;
    }


    public String getDescriptor() {
        return this.descriptor;
    }

    public static void setOpaqueMarker(String marker) {
        opaqueMarker = marker;
    }

    public int getOpcode() {
        return this.opcode;
    }

    public boolean matches(InstructionPattern instructionPattern) {
        return !(this.opcode != instructionPattern.opcode
                || !this.owner.equals(instructionPattern.owner) && !this.owner.equals("*")
                || !this.name.equals(instructionPattern.name) && !this.name.equals("*")
                || !this.descriptor.equals(instructionPattern.descriptor) && !this.descriptor.equals("*"));
    }

    public String getOwner() {
        return this.owner;
    }

    static {
        if (InstructionPattern.getOpaqueMarker() != null) {
            InstructionPattern.setOpaqueMarker("jHaNE");
        }
        long[] lArray = new long[]{-2327468895398526791L, -1358600573514940234L, -8503526305340325708L, -3693968536541069129L, 7643330736333783224L, -1797710973226712907L, 6908187125879931058L, -9093027909794266957L};
        GETSTATIC = (int)lArray[6];
        PUTFIELD = (int)lArray[5];
        INVOKEINTERFACE = (int)lArray[0];
        INVOKEVIRTUAL = (int)lArray[1];
        INVOKESTATIC = (int)lArray[4];
        INVOKESPECIAL = (int)lArray[3];
        PUTSTATIC = (int)lArray[7];
        GETFIELD = (int)lArray[2];
    }
}

