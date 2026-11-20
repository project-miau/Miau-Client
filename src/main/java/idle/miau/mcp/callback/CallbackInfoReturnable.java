package idle.miau.mcp.callback;

public class CallbackInfoReturnable<R> extends CallbackInfo {
    private R returnValue;

    public CallbackInfoReturnable(String name, boolean cancellable) {
        super(name, cancellable);
    }

    public CallbackInfoReturnable(String name, boolean cancellable, R returnValue) {
        super(name, cancellable);
        this.returnValue = returnValue;
    }

    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(String name, boolean cancellable, byte returnValue) {
        this(name, cancellable, (R) Byte.valueOf(returnValue));
    }

    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(String name, boolean cancellable, char returnValue) {
        this(name, cancellable, (R) Character.valueOf(returnValue));
    }

    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(String name, boolean cancellable, double returnValue) {
        this(name, cancellable, (R) Double.valueOf(returnValue));
    }

    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(String name, boolean cancellable, float returnValue) {
        this(name, cancellable, (R) Float.valueOf(returnValue));
    }

    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(String name, boolean cancellable, int returnValue) {
        this(name, cancellable, (R) Integer.valueOf(returnValue));
    }

    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(String name, boolean cancellable, long returnValue) {
        this(name, cancellable, (R) Long.valueOf(returnValue));
    }

    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(String name, boolean cancellable, short returnValue) {
        this(name, cancellable, (R) Short.valueOf(returnValue));
    }

    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(String name, boolean cancellable, boolean returnValue) {
        this(name, cancellable, (R) Boolean.valueOf(returnValue));
    }

    public void setReturnValue(R returnValue) throws CancellationException {
        cancel();
        this.returnValue = returnValue;
    }

    public R getReturnValue() {
        return returnValue;
    }

    public byte getReturnValueB() {
        return returnValue == null ? 0 : ((Byte) returnValue).byteValue();
    }

    public char getReturnValueC() {
        return returnValue == null ? 0 : ((Character) returnValue).charValue();
    }

    public double getReturnValueD() {
        return returnValue == null ? 0.0D : ((Double) returnValue).doubleValue();
    }

    public float getReturnValueF() {
        return returnValue == null ? 0.0F : ((Float) returnValue).floatValue();
    }

    public int getReturnValueI() {
        return returnValue == null ? 0 : ((Integer) returnValue).intValue();
    }

    public long getReturnValueJ() {
        return returnValue == null ? 0L : ((Long) returnValue).longValue();
    }

    public short getReturnValueS() {
        return returnValue == null ? 0 : ((Short) returnValue).shortValue();
    }

    public boolean getReturnValueZ() {
        return returnValue != null && ((Boolean) returnValue).booleanValue();
    }
}
